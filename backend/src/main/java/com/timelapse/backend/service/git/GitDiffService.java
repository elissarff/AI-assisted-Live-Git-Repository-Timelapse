package com.timelapse.backend.service.git;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.DiffHunkDto;
import com.timelapse.backend.dto.DiffLineDto;
import com.timelapse.backend.dto.FileChangeDto;
import com.timelapse.backend.types.DiffLineType;
import com.timelapse.backend.types.FileChangeType;

@Service
public class GitDiffService {
    private static final int CONTEXT_LINES = 3;

    public DiffResult getFileChanges(Repository repository, RevCommit commit) throws Exception {
        List<DiffEntry> entries = getDiffEntries(repository, commit);
        List<FileChangeDto> files = new ArrayList<>();
        int totalAdditions = 0;
        int totalDeletions = 0;

        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);

            RevCommit parent = firstParent(repository, commit);
            for (DiffEntry entry : entries) {
                FileHeader header = formatter.toFileHeader(entry);
                int additions = countAdditions(header);
                int deletions = countDeletions(header);
                totalAdditions += additions;
                totalDeletions += deletions;

                RawFile oldFile = readFile(repository, parent, entry.getOldPath());
                RawFile newFile = readFile(repository, commit, entry.getNewPath());
                boolean binary = oldFile.binary() || newFile.binary();
                List<DiffHunkDto> hunks = binary
                        ? List.of()
                        : buildHunks(oldFile.text(), newFile.text(), header.toEditList());

                files.add(new FileChangeDto(
                        entry.getOldPath(), entry.getNewPath(), mapChangeType(entry.getChangeType()),
                        additions, deletions, binary, hunks));
            }
        }
        return new DiffResult(files, totalAdditions, totalDeletions);
    }

    private RevCommit firstParent(Repository repository, RevCommit commit) throws IOException {
        if (commit.getParentCount() == 0) return null;
        try (RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(commit.getParent(0).getId());
        }
    }

    /** Reads the exact blob at a commit so diff DTOs contain real source lines, not just counts. */
    private RawFile readFile(Repository repository, RevCommit commit, String path) throws IOException {
        if (commit == null || path == null || DiffEntry.DEV_NULL.equals(path)) return RawFile.empty();
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, commit.getTree())) {
            if (treeWalk == null) return RawFile.empty();
            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId, Constants.OBJ_BLOB);
            byte[] bytes = loader.getBytes();
            if (RawText.isBinary(bytes)) return new RawFile(new RawText(new byte[0]), true);
            return new RawFile(new RawText(bytes), false);
        }
    }

    /**
     * Converts JGit edits into GitHub-style structured hunks. CONTEXT lines come from the
     * real blob, DELETION lines from the parent blob, and ADDITION lines from the commit blob.
     */
    private List<DiffHunkDto> buildHunks(RawText oldText, RawText newText, List<Edit> edits) {
        if (edits.isEmpty()) return List.of();
        List<DiffHunkDto> hunks = new ArrayList<>();

        int i = 0;
        while (i < edits.size()) {
            Edit first = edits.get(i);
            int oldStart0 = Math.max(0, first.getBeginA() - CONTEXT_LINES);
            int newStart0 = Math.max(0, first.getBeginB() - CONTEXT_LINES);
            int oldEnd = Math.min(oldText.size(), first.getEndA() + CONTEXT_LINES);
            int newEnd = Math.min(newText.size(), first.getEndB() + CONTEXT_LINES);
            int last = i;

            // Merge nearby edits so overlapping context is emitted only once.
            while (last + 1 < edits.size()) {
                Edit next = edits.get(last + 1);
                if (next.getBeginA() > oldEnd && next.getBeginB() > newEnd) break;
                last++;
                oldEnd = Math.min(oldText.size(), next.getEndA() + CONTEXT_LINES);
                newEnd = Math.min(newText.size(), next.getEndB() + CONTEXT_LINES);
            }

            List<DiffLineDto> lines = new ArrayList<>();
            int oldPos = oldStart0;
            int newPos = newStart0;
            for (int e = i; e <= last; e++) {
                Edit edit = edits.get(e);

                while (oldPos < edit.getBeginA() && newPos < edit.getBeginB()) {
                    lines.add(new DiffLineDto(DiffLineType.CONTEXT, oldPos + 1, newPos + 1, oldText.getString(oldPos)));
                    oldPos++;
                    newPos++;
                }
                while (oldPos < edit.getEndA()) {
                    lines.add(new DiffLineDto(DiffLineType.DELETION, oldPos + 1, null, oldText.getString(oldPos)));
                    oldPos++;
                }
                while (newPos < edit.getEndB()) {
                    lines.add(new DiffLineDto(DiffLineType.ADDITION, null, newPos + 1, newText.getString(newPos)));
                    newPos++;
                }
            }

            while (oldPos < oldEnd && newPos < newEnd) {
                lines.add(new DiffLineDto(DiffLineType.CONTEXT, oldPos + 1, newPos + 1, oldText.getString(oldPos)));
                oldPos++;
                newPos++;
            }

            hunks.add(new DiffHunkDto(
                    oldStart0 + 1, oldEnd - oldStart0,
                    newStart0 + 1, newEnd - newStart0,
                    lines));
            i = last + 1;
        }
        return hunks;
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

    private record RawFile(RawText text, boolean binary) {
        static RawFile empty() { return new RawFile(new RawText(new byte[0]), false); }
    }

    public record DiffResult(List<FileChangeDto> files, int additions, int deletions) { }
}
