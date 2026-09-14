package com.timelapse.backend.service;

import com.timelapse.backend.dto.CommitDto;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class GitService {

    public boolean repositoryExists(Path gitDirectory) {
        return Files.isDirectory(gitDirectory) && Files.exists(gitDirectory.resolve("HEAD"));
    }

    public void cloneBare(String remoteUrl, Path destination) throws Exception {
        cloneBare(remoteUrl, destination, null);
    }

    public void cloneBare(String remoteUrl, Path destination, CredentialsProvider credentials) throws Exception {
        var command = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destination.toFile())
                .setBare(true);

        if (credentials != null) {
            command.setCredentialsProvider(credentials);
        }

        try (Git ignored = command.call()) {
            // no secrets are logged here
        }
    }

    public Repository openRepository(Path gitDirectory) throws Exception {
        return new FileRepositoryBuilder()
                .setGitDir(gitDirectory.toFile())
                .build();
    }

    public void fetch(Path gitDirectory) throws Exception {
        fetch(gitDirectory, null);
    }

    public void fetch(Path gitDirectory, CredentialsProvider credentials) throws Exception {
        try (Repository repository = openRepository(gitDirectory);
             Git git = new Git(repository)) {
            var command = git.fetch().setRemote("origin");
            if (credentials != null) {
                command.setCredentialsProvider(credentials);
            }
            command.call();
        }
    }

    public String getRemoteBranchHead(Path gitDirectory, String branch) throws Exception {
        try (Repository repository = openRepository(gitDirectory)) {
            ObjectId objectId = resolveBranch(repository, branch);
            return objectId != null ? objectId.getName() : null;
        }
    }

    public String detectDefaultBranch(Path gitDirectory) throws Exception {
        try (Repository repository = openRepository(gitDirectory)) {
            String branch = repository.getBranch();
            if (branch != null && !branch.isBlank() && !"HEAD".equals(branch)) {
                return branch;
            }
            var target = repository.exactRef("HEAD");
            if (target != null && target.isSymbolic() && target.getTarget() != null) {
                String name = target.getTarget().getName();
                if (name.startsWith("refs/heads/")) {
                    return name.substring("refs/heads/".length());
                }
            }
            return "main";
        }
    }

    public List<CommitDto> getRecentCommits(Path gitDirectory, String branch, int limit) throws Exception {
        try (Repository repository = openRepository(gitDirectory);
             Git git = new Git(repository)) {
            ObjectId branchHead = resolveBranch(repository, branch);
            if (branchHead == null) return List.of();

            List<CommitDto> result = new ArrayList<>();
            for (RevCommit commit : git.log().add(branchHead).setMaxCount(limit).call()) {
                result.add(toDto(commit));
            }
            return result;
        }
    }

    public int countCommits(Path gitDirectory, String branch) throws Exception {
        try (Repository repository = openRepository(gitDirectory);
             Git git = new Git(repository)) {
            ObjectId branchHead = resolveBranch(repository, branch);
            if (branchHead == null) return 0;
            int count = 0;
            for (RevCommit ignored : git.log().add(branchHead).call()) count++;
            return count;
        }
    }

    public boolean commitExists(Path gitDirectory, String sha) throws Exception {
        if (sha == null || sha.isBlank()) return false;
        try (Repository repository = openRepository(gitDirectory)) {
            return repository.resolve(sha) != null;
        }
    }

    public boolean isAncestor(Path gitDirectory, String ancestorSha, String descendantSha) throws Exception {
        if (ancestorSha == null || descendantSha == null) return false;
        try (Repository repository = openRepository(gitDirectory);
             RevWalk walk = new RevWalk(repository)) {
            ObjectId ancestor = repository.resolve(ancestorSha);
            ObjectId descendant = repository.resolve(descendantSha);
            if (ancestor == null || descendant == null) return false;
            RevCommit a = walk.parseCommit(ancestor);
            RevCommit d = walk.parseCommit(descendant);
            return walk.isMergedInto(a, d);
        }
    }

    public List<CommitDto> getAllCommits(Path gitDirectory, String newSha) throws Exception {
        if (newSha == null) return List.of();
        try (Repository repository = openRepository(gitDirectory);
             Git git = new Git(repository)) {
            ObjectId newCommit = repository.resolve(newSha);
            if (newCommit == null) return List.of();
            List<CommitDto> result = new ArrayList<>();
            for (RevCommit commit : git.log().add(newCommit).call()) {
                result.add(toDto(commit));
            }
            Collections.reverse(result);
            return result;
        }
    }

    public List<CommitDto> getCommitsBetween(Path gitDirectory, String oldSha, String newSha) throws Exception {
        if (newSha == null || (oldSha != null && oldSha.equals(newSha))) return List.of();
        if (oldSha == null) return getAllCommits(gitDirectory, newSha);

        try (Repository repository = openRepository(gitDirectory);
             Git git = new Git(repository)) {
            ObjectId oldCommit = repository.resolve(oldSha);
            ObjectId newCommit = repository.resolve(newSha);
            if (newCommit == null) return List.of();
            if (oldCommit == null) return getAllCommits(gitDirectory, newSha);

            List<CommitDto> result = new ArrayList<>();
            for (RevCommit commit : git.log().addRange(oldCommit, newCommit).call()) {
                result.add(toDto(commit));
            }
            Collections.reverse(result);
            return result;
        }
    }

    private ObjectId resolveBranch(Repository repository, String branch) throws Exception {
        if (branch == null || branch.isBlank()) return null;
        ObjectId objectId = repository.resolve("refs/remotes/origin/" + branch);
        if (objectId == null) objectId = repository.resolve("refs/heads/" + branch);
        return objectId;
    }

    private CommitDto toDto(RevCommit commit) {
        return new CommitDto(
                commit.getName(),
                commit.getShortMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getWhenAsInstant()
        );
    }
}
