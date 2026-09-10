package com.timelapse.backend.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.RepositoryInfoDto;

@Service
public class GitService {

    public RepositoryInfoDto inspectRepository(
            String repositoryPath
    ) throws Exception {

        File directory = new File(repositoryPath);

        try (Git git = Git.open(directory)) {

            String branch = git.getRepository().getBranch();

            String repositoryName = directory.getName();

            var head = git.getRepository().resolve("HEAD");

            String headSha =
                    head != null
                            ? head.getName()
                            : null;

            List<CommitDto> recentCommits =
                    new ArrayList<>();

            int commitCount = 0;

            Iterable<RevCommit> commits =
                    git.log().call();

            for (RevCommit commit : commits) {

                commitCount++;

                if (recentCommits.size() < 10) {

                    recentCommits.add(
                        new CommitDto(
                            commit.getName(),
                            commit.getShortMessage(),
                            commit.getAuthorIdent().getName(),
                            commit.getAuthorIdent()
                                  .getWhenAsInstant()
                        )
                    );
                }
            }

            return new RepositoryInfoDto(
                    repositoryName,
                    branch,
                    headSha,
                    commitCount,
                    recentCommits
            );
        }
    }
}