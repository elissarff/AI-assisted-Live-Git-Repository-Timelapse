package com.timelapse.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.timelapse.backend.entity.RefactoringRepositorySettingsEntity;

public interface RefactoringRepositorySettingsJpaRepository extends JpaRepository<RefactoringRepositorySettingsEntity, Long> {
}
