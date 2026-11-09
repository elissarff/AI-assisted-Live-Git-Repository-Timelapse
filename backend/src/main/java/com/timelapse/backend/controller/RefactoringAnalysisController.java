package com.timelapse.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.service.refactoring.RefactoringAnalysisService;

@RestController
@RequestMapping("/api/repositories/{repoKey}/refactorings")
public class RefactoringAnalysisController {

    private final RefactoringAnalysisService service;

    public RefactoringAnalysisController(RefactoringAnalysisService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> list(@PathVariable UUID repoKey,
            @RequestParam(defaultValue = "50") int limit, @RequestParam(defaultValue = "0") int offset) {
        return service.list(repoKey, limit, offset);
    }

    @GetMapping("/{sha}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID repoKey, @PathVariable String sha) {
        try {
            return ResponseEntity.ok(service.job(repoKey, sha));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 404 && "No analysis for commit".equals(ex.getReason())) {
                return ResponseEntity.noContent().build();
            }
            throw ex;
        }
    }

    @PostMapping("/{sha}")
    public Map<String, Object> analyze(@PathVariable UUID repoKey, @PathVariable String sha) throws Exception {
        return service.request(repoKey, sha, true);
    }

    @PostMapping("/{sha}/retry")
    public Map<String, Object> retry(@PathVariable UUID repoKey, @PathVariable String sha) {
        service.retry(repoKey, sha);
        return service.job(repoKey, sha);
    }
}
