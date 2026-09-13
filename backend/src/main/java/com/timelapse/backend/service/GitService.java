package com.timelapse.backend.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;
import com.timelapse.backend.dto.CommitDto;

@Service
public class GitService {

    private final GitProperties properties;

    public GitService(GitProperties properties) {
        this.properties = properties;
    }

    /*
     * Clone remote repository as a bare Git repository.
     */
    public void cloneBare(
            String remoteUrl,
            Path destination
    ) throws Exception {

        var cloneCommand = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destination.toFile())
                .setBare(true);

        try (Git ignored = cloneCommand.call()) {
            System.out.println(
                    "Bare repository cloned to: "
                            + destination.toAbsolutePath()
            );
        }
    }

    /*
     * Open a bare repository.
     */
    public Repository openRepository(
            Path gitDirectory
    ) throws Exception {

        return new FileRepositoryBuilder()
                .setGitDir(gitDirectory.toFile())
                .build();
    }

    /*
     * Fetch changes from origin.
     */
    public void fetch(Path gitDirectory) throws Exception {

        try (Repository repository =
                     openRepository(gitDirectory);
             Git git = new Git(repository)) {

            var fetchCommand = git.fetch()
                    .setRemote("origin");

            fetchCommand.call();
        }
    }

    /*
     * Get the SHA pointed to by the remote branch.
     */
    public String getRemoteBranchHead(
            Path gitDirectory,
            String branch
    ) throws Exception {

        try (Repository repository =
                     openRepository(gitDirectory)) {

            ObjectId objectId =
                    repository.resolve(
                            "refs/remotes/origin/" + branch
                    );

            /*
             * Bare clone refs can sometimes be available directly
             * as heads instead.
             */
            if (objectId == null) {
                objectId = repository.resolve(
                        "refs/heads/" + branch
                );
            }

            return objectId != null
                    ? objectId.getName()
                    : null;
        }
    }

    public List<CommitDto> getRecentCommits(
            Path gitDirectory,
            String branch,
            int limit
    ) throws Exception {

        try (Repository repository =
                     openRepository(gitDirectory);
             Git git = new Git(repository)) {

            ObjectId branchHead = resolveBranch(
                    repository,
                    branch
            );

            if (branchHead == null) {
                return List.of();
            }

            Iterable<RevCommit> commits =
                    git.log()
                            .add(branchHead)
                            .setMaxCount(limit)
                            .call();

            List<CommitDto> result =
                    new ArrayList<>();

            for (RevCommit commit : commits) {
                result.add(toDto(commit));
            }

            return result;
        }
    }

    public int countCommits(
            Path gitDirectory,
            String branch
    ) throws Exception {

        try (Repository repository =
                     openRepository(gitDirectory);
             Git git = new Git(repository)) {

            ObjectId branchHead =
                    resolveBranch(repository, branch);

            if (branchHead == null) {
                return 0;
            }

            int count = 0;

            Iterable<RevCommit> commits =
                    git.log()
                            .add(branchHead)
                            .call();

            for (RevCommit ignored : commits) {
                count++;
            }

            return count;
        }
    }

    /*
     * Find commits added between two SHAs.
     */
    public List<CommitDto> getCommitsBetween(
            Path gitDirectory,
            String oldSha,
            String newSha
    ) throws Exception {

        if (oldSha == null ||
            newSha == null ||
            oldSha.equals(newSha)) {

            return List.of();
        }

        try (Repository repository =
                     openRepository(gitDirectory);
             Git git = new Git(repository)) {

            ObjectId oldCommit =
                    repository.resolve(oldSha);

            ObjectId newCommit =
                    repository.resolve(newSha);

            if (newCommit == null) {
                return List.of();
            }

            List<CommitDto> result =
                    new ArrayList<>();

            Iterable<RevCommit> commits;

            if (oldCommit == null) {

                commits = git.log()
                        .add(newCommit)
                        .call();

            } else {

                commits = git.log()
                        .addRange(oldCommit, newCommit)
                        .call();
            }

            for (RevCommit commit : commits) {
                result.add(toDto(commit));
            }

            /*
             * JGit returns newest first.
             *
             * For your timelapse it is usually more convenient
             * to process oldest -> newest.
             */
            Collections.reverse(result);

            return result;
        }
    }

    private ObjectId resolveBranch(
            Repository repository,
            String branch
    ) throws Exception {

        ObjectId objectId =
                repository.resolve(
                        "refs/remotes/origin/" + branch
                );

        if (objectId == null) {
            objectId =
                    repository.resolve(
                            "refs/heads/" + branch
                    );
        }

        return objectId;
    }

    private CommitDto toDto(
            RevCommit commit
    ) {

        return new CommitDto(
                commit.getName(),
                commit.getShortMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent()
                        .getWhenAsInstant()
        );
    }
}