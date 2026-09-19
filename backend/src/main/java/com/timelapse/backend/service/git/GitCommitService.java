package com.timelapse.backend.service.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.CommitDto;

@Service
public class GitCommitService {

    List<CommitDto> getRecentCommits(Repository repository, ObjectId branchHead, int limit) throws Exception {
        if (branchHead == null) return List.of();
        List<CommitDto> result = new ArrayList<>();
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().add(branchHead).setMaxCount(limit).call()) result.add(toDto(commit));
        }
        return result;
    }

    int countCommits(Repository repository, ObjectId branchHead) throws Exception {
        if (branchHead == null) return 0;
        int count = 0;
        try (Git git = new Git(repository)) {
            for (RevCommit ignored : git.log().add(branchHead).call()) count++;
        }
        return count;
    }

    boolean commitExists(Repository repository, String sha) throws Exception {
        return sha != null && !sha.isBlank() && repository.resolve(sha) != null;
    }

    boolean isAncestor(Repository repository, String ancestorSha, String descendantSha) throws Exception {
        if (ancestorSha == null || descendantSha == null) return false;
        ObjectId ancestor = repository.resolve(ancestorSha);
        ObjectId descendant = repository.resolve(descendantSha);
        if (ancestor == null || descendant == null) return false;
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.isMergedInto(walk.parseCommit(ancestor), walk.parseCommit(descendant));
        }
    }

    List<CommitDto> getAllCommits(Repository repository, String newSha) throws Exception {
        if (newSha == null) return List.of();
        ObjectId newCommit = repository.resolve(newSha);
        if (newCommit == null) return List.of();
        List<CommitDto> result = new ArrayList<>();
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().add(newCommit).call()) result.add(toDto(commit));
        }
        Collections.reverse(result);
        return result;
    }

    List<CommitDto> getCommitsBetween(Repository repository, String oldSha, String newSha) throws Exception {
        if (newSha == null || (oldSha != null && oldSha.equals(newSha))) return List.of();
        if (oldSha == null) return getAllCommits(repository, newSha);
        ObjectId oldCommit = repository.resolve(oldSha);
        ObjectId newCommit = repository.resolve(newSha);
        if (newCommit == null) return List.of();
        if (oldCommit == null) return getAllCommits(repository, newSha);
        List<CommitDto> result = new ArrayList<>();
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().addRange(oldCommit, newCommit).call()) result.add(toDto(commit));
        }
        Collections.reverse(result);
        return result;
    }

    RevCommit parseCommit(Repository repository, RevWalk walk, String sha) throws Exception {
        ObjectId objectId = repository.resolve(sha);
        if (objectId == null) throw new IllegalArgumentException("Commit not found: " + sha);
        return walk.parseCommit(objectId);
    }

    CommitDto toDto(RevCommit commit) {
        return new CommitDto(commit.getName(), commit.getShortMessage(),
                commit.getAuthorIdent().getName(), commit.getAuthorIdent().getWhenAsInstant());
    }

}
