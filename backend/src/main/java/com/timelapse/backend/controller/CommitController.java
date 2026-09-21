package com.timelapse.backend.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.FileContentDto;
import com.timelapse.backend.service.RepositoryService;

/**
 * Read-only Git history API used by the timelapse.
 *
 * Controllers deliberately do not open JGit repositories themselves.  They
 * delegate to RepositoryService, which resolves the registered repository and
 * then delegates all Git reads to GitMiningService/GitService.  This keeps the
 * controller layer consistent with the architecture diagrams and guarantees
 * that the local bare clone remains the source used for extraction.
 */
@RestController
@RequestMapping("/api/repositories/{repositoryId}/commits")
public class CommitController {
    private final RepositoryService repositoryService;

    public CommitController(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    /**
     * Complete default-branch history in chronological order (oldest -> newest).
     * This is the primary endpoint used to construct the timelapse scrubber.
     */
    @GetMapping
    public List<CommitDto> timeline(@PathVariable String repositoryId) throws Exception {
        return repositoryService.getTimeline(repositoryId);
    }

    /**
     * Full authoritative Git facts for one commit: message, author, parents,
     * timestamp, changed files, change type and +/- line counts.
     */
    @GetMapping("/{sha}")
    public CommitDetailDto detail(
            @PathVariable String repositoryId,
            @PathVariable String sha
    ) throws Exception {
        return repositoryService.getCommitDetail(repositoryId, sha);
    }

    /**
     * File contents exactly as they existed at a selected commit.  The path is
     * a query parameter so repository paths containing slashes do not fight
     * Spring MVC path matching.
     *
     * Example:
     * GET /api/repositories/{id}/commits/{sha}/file?path=src/main/App.tsx
     */
    @GetMapping("/{sha}/file")
    public ResponseEntity<FileContentDto> fileAtCommit(
            @PathVariable String repositoryId,
            @PathVariable String sha,
            @RequestParam("path") String path
    ) throws Exception {
        FileContentDto result = repositoryService.getFileContent(repositoryId, sha, path);
        // A commit SHA identifies immutable Git data, so this response may be cached.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(result);
    }
}
