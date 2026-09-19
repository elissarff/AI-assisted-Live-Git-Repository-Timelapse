package com.timelapse.backend.service.git;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.types.FileChangeType;

@Service
public class GitDiffService {

    public DiffResult getFileChanges(Repository repository, RevCommit commit) throws Exception {
        List<DiffEntry> entries = getDiffEntries(repository, commit);
        List<FileChangeDto> files = new ArrayList<>();
        int totalAdditions = 0;
        int totalDeletions = 0;

        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            for (DiffEntry entry : entries) {
                FileHeader header = formatter.toFileHeader(entry);
                int additions = countAdditions(header);
                int deletions = countDeletions(header);
                totalAdditions += additions;
                totalDeletions += deletions;
                files.add(new FileChangeDto(entry.getOldPath(), entry.getNewPath(), mapChangeType(entry.getChangeType()),
                        additions, deletions, false, List.of()));
            }
        }
        return new DiffResult(files, totalAdditions, totalDeletions);
    }

    AbstractTreeIterator prepareTreeParser(Repository repository, RevCommit commit) throws Exception {
        try (var reader = repository.newObjectReader()) {
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(reader, commit.getTree().getId());
            return treeParser;
        }
    }

    List<DiffEntry> getDiffEntries(Repository repository, RevCommit commit) throws Exception {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            AbstractTreeIterator oldTree;
            if (commit.getParentCount() == 0) {
                oldTree = new EmptyTreeIterator();
            } else {
                try (RevWalk walk = new RevWalk(repository)) {
                    RevCommit parent = walk.parseCommit(commit.getParent(0).getId());
                    oldTree = prepareTreeParser(repository, parent);
                }
            }
            AbstractTreeIterator newTree = prepareTreeParser(repository, commit);
            return formatter.scan(oldTree, newTree);
        }
    }

    FileChangeType mapChangeType(DiffEntry.ChangeType changeType) {
        return switch (changeType) {
            case ADD -> FileChangeType.ADD;
            case MODIFY -> FileChangeType.MODIFY;
            case DELETE -> FileChangeType.DELETE;
            case RENAME -> FileChangeType.RENAME;
            case COPY -> FileChangeType.COPY;
        };
    }

    int countAdditions(FileHeader header) {
        int additions = 0;
        for (Edit edit : header.toEditList()) additions += edit.getLengthB();
        return additions;
    }

    int countDeletions(FileHeader header) {
        int deletions = 0;
        for (Edit edit : header.toEditList()) deletions += edit.getLengthA();
        return deletions;
    }

    public record DiffResult(List<FileChangeDto> files, int additions, int deletions) { }
}
