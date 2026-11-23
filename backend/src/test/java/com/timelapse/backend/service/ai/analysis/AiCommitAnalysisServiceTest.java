package com.timelapse.backend.service.ai.analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.ai.AiSettingsService;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.types.RepositoryVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiCommitAnalysisServiceTest {
    private static final String SHA = "a".repeat(40);
    private static final String RESULT = """
        {"summary":"Adds validation","purpose":"Reject invalid input","category":"bugfix",
         "significance":"Improves reliability","affectedComponents":["API"],"tags":["validation"]}
        """;

    @Test
    void manualRequestReturnsCompletedResultWithoutCallingProviderForCachedCommit() throws Exception {
        var f = new Fixture("COMPLETED", 0, false);
        var result = f.service.request(f.key.toString(), SHA, false);
        assertEquals("COMPLETED", result.get("status"));
        assertNotNull(result.get("analysis"));
        // Spring Boot 4 serializes responses with Jackson 3, while the provider uses Jackson 2.
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result);
        var response = f.mapper.readTree(json);
        assertEquals("Adds validation", response.path("analysis").path("summary").asText());
        assertEquals("bugfix", response.path("analysis").path("category").asText());
        assertEquals("validation", response.path("analysis").path("tags").get(0).asText());
        verify(f.provider, never()).explain(anyString());
    }

    @Test
    void manualRequestProcessesCommitImmediatelyWithAutomaticAiOff() throws Exception {
        var f = new Fixture("PENDING", 0, false);
        when(f.provider.explain(anyString())).thenReturn(f.mapper.readTree(RESULT));
        var result = f.service.request(f.key.toString(), SHA, false);
        assertEquals("COMPLETED", result.get("status"));
        verify(f.provider).explain(contains("Message: Test commit"));
        verifyNoInteractions(f.settings);
    }

    @Test
    void providerFailureReturnsFailedInsteadOfLeavingManualRequestPending() throws Exception {
        var f = new Fixture("PENDING", 0, false);
        when(f.provider.explain(anyString())).thenThrow(new IllegalStateException("Gemini HTTP 503"));
        var result = f.service.request(f.key.toString(), SHA, false);
        assertEquals("FAILED", result.get("status"));
        assertEquals("Gemini HTTP 503", result.get("error"));
    }

    @Test
    void exhaustedQuotaReturnsFailureWithoutProviderCall() throws Exception {
        var f = new Fixture("PENDING", 20, false);
        var result = f.service.request(f.key.toString(), SHA, false);
        assertEquals("FAILED", result.get("status"));
        assertTrue(result.get("error").toString().contains("limit"));
        verify(f.provider, never()).explain(anyString());
    }

    @Test
    void invalidProviderOutputReturnsFailureInsteadOfAnExplanation() throws Exception {
        var f = new Fixture("PENDING", 0, false);
        when(f.provider.explain(anyString())).thenReturn(f.mapper.readTree("{\"summary\":\"Incomplete\"}"));
        var result = f.service.request(f.key.toString(), SHA, false);
        assertEquals("FAILED", result.get("status"));
        assertFalse(result.containsKey("analysis"));
        assertTrue(result.get("error").toString().contains("purpose"));
    }

    @Test
    void runningJobDoesNotStartDuplicateProviderCall() throws Exception {
        var f = new Fixture("RUNNING", 0, true);
        var error = assertThrows(ResponseStatusException.class,
            () -> f.service.request(f.key.toString(), SHA, false));
        assertEquals(409, error.getStatusCode().value());
        verify(f.provider, never()).explain(anyString());
    }

    private static class Fixture {
        final UUID key = UUID.randomUUID();
        final ObjectMapper mapper = new ObjectMapper();
        final LlmProvider provider = mock(LlmProvider.class);
        final AiSettingsService settings = mock(AiSettingsService.class);
        final AiCommitAnalysisService service;
        String status;
        String error = "";

        Fixture(String initialStatus, int usage, boolean alreadyRunning) throws Exception {
            status = initialStatus;
            var repo = mock(RepositoryEntity.class);
            when(repo.getId()).thenReturn(1L);
            when(repo.getRepoKey()).thenReturn(key);
            when(repo.getVisibility()).thenReturn(RepositoryVisibility.PUBLIC);
            when(repo.getLocalGitDirectory()).thenReturn("/tmp/test-ai-repository");
            var repositories = mock(RepositoryJpaRepository.class);
            when(repositories.findByRepoKey(key)).thenReturn(Optional.of(repo));
            when(repositories.findById(1L)).thenReturn(Optional.of(repo));
            var git = mock(GitMiningService.class);
            when(git.getCommitDetail(any(Path.class), eq(SHA))).thenReturn(
                new CommitDetailDto(SHA, "aaaaaaa", "Test commit", null, null, List.of(), 0, 0, 0, List.of()));
            when(provider.provider()).thenReturn("gemini");
            when(provider.model()).thenReturn("test-model");
            var jdbc = mock(JdbcTemplate.class, invocation -> {
                String sql = invocation.getArgument(0);
                switch (invocation.getMethod().getName()) {
                    case "queryForObject":
                        if (sql.contains("ai_analysis_profiles")) return 2L;
                        if (sql.contains("SELECT priority")) return 100;
                        if (sql.contains("count(*)")) return usage;
                        if (sql.contains("SELECT id FROM ai_analyses")) return 3L;
                        break;
                    case "queryForMap":
                        return Map.of("repository_id",1L,"target_key",SHA,"profile_id",2L);
                    case "queryForList":
                        if (sql.contains("SELECT j.status")) return List.of("COMPLETED".equals(status)
                            ? Map.of("status",status,"result",RESULT)
                            : Map.of("status",status,"error",error));
                        if (sql.startsWith("UPDATE ai_analysis_jobs")) return alreadyRunning ? List.of() : List.of(4L);
                        return List.of();
                    case "update":
                        if (sql.contains("SET status='COMPLETED'")) status="COMPLETED";
                        if (sql.contains("SET status=CASE")) {
                            assertEquals(true, invocation.getArgument(1));
                            status="FAILED";
                            error=invocation.getArgument(2);
                        }
                        return 1;
                }
                return RETURNS_DEFAULTS.answer(invocation);
            });
            service = new AiCommitAnalysisService(jdbc,repositories,git,settings,provider,mapper,20);
        }
    }
}
