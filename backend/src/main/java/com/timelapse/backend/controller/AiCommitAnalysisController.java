package com.timelapse.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.service.ai.analysis.AiCommitAnalysisService;
import com.timelapse.backend.service.ai.analysis.ExplicitAiService;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/ai/commits")
public class AiCommitAnalysisController {
    private final AiCommitAnalysisService service;
    private final ExplicitAiService explicit;

    public AiCommitAnalysisController(AiCommitAnalysisService service, ExplicitAiService explicit) {
        this.service=service; this.explicit=explicit; 
    }

    @GetMapping("/{sha}")
    public Map<String,Object> get(@PathVariable String repositoryId,@PathVariable String sha) {
        return service.status(repositoryId,sha);
    }
    @PostMapping("/{sha}/analyze")
    public Map<String,Object> analyze(@PathVariable String repositoryId,@PathVariable String sha,
        @RequestParam(defaultValue="false") boolean retry) {
        return explicit.explain(java.util.UUID.fromString(repositoryId),"COMMIT",sha,null);
    }
}
