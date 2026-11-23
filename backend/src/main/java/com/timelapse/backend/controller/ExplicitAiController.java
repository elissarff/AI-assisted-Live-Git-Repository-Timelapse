package com.timelapse.backend.controller;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.service.ai.analysis.ExplicitAiService;
@RestController
@RequestMapping("/api/repositories/{repoKey}/ai")
public class ExplicitAiController {
    private final ExplicitAiService service;
    public ExplicitAiController(ExplicitAiService service) { 
        this.service=service; 
    }

    @GetMapping("/categories") public Map<String,Object> categories(@PathVariable UUID repoKey,@RequestParam(required=false) String branch) throws Exception { 
        return service.classifications(repoKey,branch); 
    }
    
    @PostMapping("/categories") public Map<String,Object> classify(@PathVariable UUID repoKey,@RequestParam(required=false) String branch) { 
        return service.classify(repoKey,branch); 
    }

    @PostMapping("/explain") public Map<String,Object> explain(@PathVariable UUID repoKey,
        @RequestParam String type,
        @RequestParam String sha,
        @RequestParam(required=false) String path,
        @RequestParam(required=false) String from,
        @RequestParam(required=false) String to,
        @RequestParam(required=false) String fromSha,
        @RequestParam(required=false) String toSha) {
        return service.explain(repoKey,type,sha,path,from,to,fromSha,toSha);
    }
}
