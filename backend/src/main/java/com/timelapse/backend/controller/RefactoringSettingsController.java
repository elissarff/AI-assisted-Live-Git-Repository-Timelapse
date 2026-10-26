package com.timelapse.backend.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.dto.RefactoringSettingsDto;
import com.timelapse.backend.dto.UpdateRefactoringSettingsRequest;
import com.timelapse.backend.service.ai.RefactoringSettingsService;

@RestController
@RequestMapping("/api/repositories/{repoKey}/refactoring/settings")
public class RefactoringSettingsController {

    private final RefactoringSettingsService service;

    public RefactoringSettingsController(RefactoringSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public RefactoringSettingsDto get(@PathVariable UUID repoKey) {
        return service.get(repoKey);
    }

    @PatchMapping
    public RefactoringSettingsDto update(@PathVariable UUID repoKey, @RequestBody UpdateRefactoringSettingsRequest request) {
        return service.update(repoKey, request);
    }
}
