package com.timelapse.backend.service.git;

import java.nio.charset.StandardCharsets;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import com.timelapse.backend.dto.FileContentDto;

@Service
public class GitFileService {

    FileContentDto getFileContent(Repository repository, RevCommit commit, String filePath) throws Exception {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, filePath, commit.getTree())) {
            if (treeWalk == null) {
                throw new IllegalArgumentException("File not found at commit " + commit.getName() + ": " + filePath);
            }
            ObjectId blobId = treeWalk.getObjectId(0);
            var loader = repository.open(blobId);
            byte[] bytes = loader.getBytes();
            boolean binary = isBinary(bytes);
            String content = binary ? null : new String(bytes, StandardCharsets.UTF_8);
            return new FileContentDto(filePath, commit.getName(), loader.getSize(), binary, content);
        }
    }

    boolean isBinary(byte[] data) {
        int limit = Math.min(data.length, 8000);
        for (int i = 0; i < limit; i++) if (data[i] == 0) return true;
        return false;
    }
}
