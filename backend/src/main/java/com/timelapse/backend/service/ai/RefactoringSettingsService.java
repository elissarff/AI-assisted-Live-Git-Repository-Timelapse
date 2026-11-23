package com.timelapse.backend.service.ai;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.timelapse.backend.dto.RefactoringSettingsDto;
import com.timelapse.backend.dto.UpdateRefactoringSettingsRequest;
import com.timelapse.backend.entity.RefactoringRepositorySettingsEntity;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RefactoringRepositorySettingsJpaRepository;
import com.timelapse.backend.repository.RepositoryJpaRepository;

@Service
public class RefactoringSettingsService {

    private final RepositoryJpaRepository repositories;
    private final RefactoringRepositorySettingsJpaRepository settings;

    public RefactoringSettingsService(RepositoryJpaRepository repositories, RefactoringRepositorySettingsJpaRepository settings) {
        this.repositories = repositories;
        this.settings = settings;
    }

    private RepositoryEntity requireRepository(UUID key) {
        return repositories.findByRepoKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }

    @Transactional(readOnly = true)
    public RefactoringSettingsDto get(UUID key) {
        RepositoryEntity repo = requireRepository(key);
        return settings.findById(repo.getId()).map(s -> new RefactoringSettingsDto(s.isEnabled()))
                .orElse(new RefactoringSettingsDto(false));
    }

    @Transactional
    public RefactoringSettingsDto update(UUID key, UpdateRefactoringSettingsRequest request) {
        RepositoryEntity repo = requireRepository(key);
        if (request == null || request.enabled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled is required");
        }
        RefactoringRepositorySettingsEntity value = settings.findById(repo.getId()).orElseGet(() -> {
            RefactoringRepositorySettingsEntity created = new RefactoringRepositorySettingsEntity();
            created.setRepositoryId(repo.getId());
            return created;
        });
        value.setEnabled(request.enabled());
        return new RefactoringSettingsDto(settings.save(value).isEnabled());
    }
}
