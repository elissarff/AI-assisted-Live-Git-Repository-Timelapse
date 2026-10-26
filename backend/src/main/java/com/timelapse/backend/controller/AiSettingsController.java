package com.timelapse.backend.controller;
import java.util.UUID;
import com.timelapse.backend.dto.*;
import com.timelapse.backend.service.ai.AiSettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repoKey}/ai/settings")
public class AiSettingsController {
    private final AiSettingsService service;
    public AiSettingsController(AiSettingsService service) { this.service = service; }
    @GetMapping public AiSettingsDto get(@PathVariable UUID repoKey) { return service.get(repoKey); }
    @PatchMapping public AiSettingsDto update(@PathVariable UUID repoKey, @RequestBody UpdateAiSettingsRequest request) {
        return service.update(repoKey, request);
    }
}
