package com.timelapse.backend.service.git;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.AuthorDto;
import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.FileContentDto;
import com.timelapse.backend.dto.RepositoryTreeDto;

@Service
public class GitMiningService {
    private final GitRepositoryService repositories;
    private final GitCommitService commits;
    private final GitDiffService diffs;
    private final GitFileService files;
    private final GitTreeService trees;

    public GitMiningService(GitRepositoryService repositories, GitCommitService commits,
                            GitDiffService diffs, GitFileService files, GitTreeService trees) {
        this.repositories = repositories;
        this.commits = commits;
        this.diffs = diffs;
        this.files = files;
        this.trees = trees;
    }

    public String getRemoteBranchHead(Path gitDirectory, String branch) throws Exception {
        return repositories.getRemoteBranchHead(gitDirectory, branch);
    }

    public List<CommitDto> getRecentCommits(Path gitDirectory, String branch, int limit) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            ObjectId branchHead = repositories.resolveBranch(repository, branch);
            return commits.getRecentCommits(repository, branchHead, limit);
        }
    }

    public int countCommits(Path gitDirectory, String branch) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            ObjectId branchHead = repositories.resolveBranch(repository, branch);
            return commits.countCommits(repository, branchHead);
        }
    }

    public boolean commitExists(Path gitDirectory, String sha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            return commits.commitExists(repository, sha);
        }
    }

    public boolean isAncestor(Path gitDirectory, String ancestorSha, String descendantSha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            return commits.isAncestor(repository, ancestorSha, descendantSha);
        }
    }

    public List<CommitDto> getAllCommits(Path gitDirectory, String newSha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            return commits.getAllCommits(repository, newSha);
        }
    }

    public List<CommitDto> getCommitsBetween(Path gitDirectory, String oldSha, String newSha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            return commits.getCommitsBetween(repository, oldSha, newSha);
        }
    }

    public List<CommitDto> getCommitsForSync(Path gitDirectory, String previousSha, String currentSha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory)) {
            if (previousSha == null
                    || !commits.commitExists(repository, previousSha)
                    || !commits.isAncestor(repository, previousSha, currentSha)) {
                return commits.getAllCommits(repository, currentSha);
            }
            return commits.getCommitsBetween(repository, previousSha, currentSha);
        }
    }

    public RepositoryTreeDto getRepositoryTree(Path gitDirectory, String commitSha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory);
             RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = commits.parseCommit(repository, walk, commitSha);
            return trees.getTree(repository, commit);
        }
    }

    public FileContentDto getFileContent(Path gitDirectory, String commitSha, String filePath) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory);
             RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = commits.parseCommit(repository, walk, commitSha);
            return files.getFileContent(repository, commit, filePath);
        }
    }

    public CommitDetailDto getCommitDetail(Path gitDirectory, String sha) throws Exception {
        try (Repository repository = repositories.openRepository(gitDirectory); RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = commits.parseCommit(repository, walk, sha);
            GitDiffService.DiffResult diff = diffs.getFileChanges(repository, commit);

            List<String> parents = new ArrayList<>();
            for (RevCommit parent : commit.getParents()) parents.add(parent.getName());
            AuthorDto author = new AuthorDto(commit.getAuthorIdent().getName(), commit.getAuthorIdent().getEmailAddress());

            return new CommitDetailDto(
                    commit.getName(),
                    commit.getName().substring(0, Math.min(7, commit.getName().length())),
                    commit.getFullMessage(),
                    author,
                    commit.getAuthorIdent().getWhenAsInstant(),
                    parents,
                    diff.files().size(),
                    diff.additions(),
                    diff.deletions(),
                    diff.files());
        }
    }
}
