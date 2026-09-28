package com.timelapse.backend.service.git;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

@Service
public class GitRepositoryService {

    public boolean repositoryExists(Path gitDirectory) {
        return Files.isDirectory(gitDirectory) && Files.exists(gitDirectory.resolve("HEAD"));
    }

    public void cloneBare(String remoteUrl, Path destination) throws Exception {
        cloneBare(remoteUrl, destination, (CredentialsProvider) null);
    }

    public void cloneBare(String remoteUrl, Path destination, String username, String password) throws Exception {
        cloneBare(remoteUrl, destination, credentials(username, password));
    }

    public void cloneBare(String remoteUrl, Path destination, CredentialsProvider credentials) throws Exception {
        var command = Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(destination.toFile())
                .setBare(true);
        if (credentials != null) command.setCredentialsProvider(credentials);
        try (Git ignored = command.call()) {
            // Repository is fully cloned before the Git handle is closed.
        }
    }

    public Repository openRepository(Path gitDirectory) throws Exception {
        return new FileRepositoryBuilder().setGitDir(gitDirectory.toFile()).build();
    }

    public void fetch(Path gitDirectory) throws Exception {
        fetch(gitDirectory, (CredentialsProvider) null);
    }

    public void fetch(Path gitDirectory, String username, String password) throws Exception {
        fetch(gitDirectory, credentials(username, password));
    }

    public void fetch(Path gitDirectory, CredentialsProvider credentials) throws Exception {
        try (Repository repository = openRepository(gitDirectory); Git git = new Git(repository)) {
            var command = git.fetch().setRemote("origin");
            if (credentials != null) command.setCredentialsProvider(credentials);
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
            if (branch != null && !branch.isBlank() && !"HEAD".equals(branch)) return branch;
            var target = repository.exactRef("HEAD");
            if (target != null && target.isSymbolic() && target.getTarget() != null) {
                String name = target.getTarget().getName();
                if (name.startsWith("refs/heads/")) return name.substring("refs/heads/".length());
            }
            return "main";
        }
    }

    ObjectId resolveBranch(Repository repository, String branch) throws Exception {
        if (branch == null || branch.isBlank()) return null;
        ObjectId objectId = repository.resolve("refs/remotes/origin/" + branch);
        if (objectId == null) objectId = repository.resolve("refs/heads/" + branch);
        return objectId;
    }

    private CredentialsProvider credentials(String username, String password) {
        if (username == null || password == null) return null;
        return new UsernamePasswordCredentialsProvider(username, password);
    }
}
