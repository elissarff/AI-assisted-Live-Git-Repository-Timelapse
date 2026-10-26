package com.timelapse.backend.service.ai;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.timelapse.backend.dto.AiSettingsDto;
import com.timelapse.backend.dto.UpdateAiSettingsRequest;
import com.timelapse.backend.entity.AiRepositorySettingsEntity;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.AiRepositorySettingsJpaRepository;
import com.timelapse.backend.repository.RepositoryJpaRepository;

@Service
public class AiSettingsService {
    private final RepositoryJpaRepository repositories;
    private final AiRepositorySettingsJpaRepository settings;
    public AiSettingsService(RepositoryJpaRepository repositories, AiRepositorySettingsJpaRepository settings) {
        this.repositories = repositories; this.settings = settings;
    }
    private RepositoryEntity requireRepository(UUID key) {
        return repositories.findByRepoKey(key).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found"));
    }
    private AiSettingsDto dto(AiRepositorySettingsEntity value) {
        return new AiSettingsDto(value.isAutoAnalyze());
    }
    @Transactional(readOnly = true)
    public AiSettingsDto get(UUID key) {
        RepositoryEntity repo = requireRepository(key);
        return settings.findById(repo.getId()).map(this::dto).orElse(new AiSettingsDto(false));
    }
    @Transactional
    public AiSettingsDto update(UUID key, UpdateAiSettingsRequest request) {
        RepositoryEntity repo = requireRepository(key);
        if (request == null || request.autoAnalyze() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "autoAnalyze is required");
        }
        AiRepositorySettingsEntity value = settings.findById(repo.getId()).orElseGet(() -> {
            AiRepositorySettingsEntity created = new AiRepositorySettingsEntity();
            created.setRepositoryId(repo.getId());
            return created;
        });
        value.setAutoAnalyze(request.autoAnalyze());
        return dto(settings.save(value));
    }
}
