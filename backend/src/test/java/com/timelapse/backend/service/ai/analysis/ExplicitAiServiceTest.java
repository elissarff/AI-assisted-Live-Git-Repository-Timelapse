package com.timelapse.backend.service.ai.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelapse.backend.dto.CommitDetailDto;
import com.timelapse.backend.dto.CommitDto;
import com.timelapse.backend.dto.FileContentDto;
import com.timelapse.backend.dto.RepositoryTreeDto;
import com.timelapse.backend.dto.RepositoryTreeNodeDto;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.types.RepositoryVisibility;

class ExplicitAiServiceTest {

    static final String SHA = "a".repeat(40);
    static final String SHA2 = "b".repeat(40);
    static final String VALID = """
        {"summary":"Adds validation","purpose":"Reject invalid input","significance":"Minor","category":"bugfix","tags":["validation"],"affectedComponents":["API"]}
        """;

    @Test
    void explanationIsCachedAndSerializedAsPlainData() throws Exception {
        var f = new Fixture(20);
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        var first = f.service.explain(f.key, "COMMIT", SHA, null);
        var second = f.service.explain(f.key, "COMMIT", SHA, null);
        assertEquals("COMPLETED", first.get("status"));
        assertEquals(first, second);
        verify(f.provider, times(1)).analyze(anyString(), anyString());
        String json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(first);
        assertEquals("Adds validation", f.mapper.readTree(json).path("analysis").path("summary").asText());
        assertEquals(1, f.usage.get());
    }

    @Test
    void classificationBatchesAreCachedAndNeedNoFurtherCalls() throws Exception {
        var f = new Fixture(20);
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree("{\"commits\":[" + entry(SHA) + "," + entry(SHA2) + "]}"));
        assertEquals(2, f.service.classify(f.key, "main").get("classified"));
        assertEquals(2, f.service.classify(f.key, "main").get("classified"));
        verify(f.provider, times(1)).analyze(anyString(), anyString());
    }

    @Test
    void classificationUsesBoundedBatchesAndReadOnlyListingDoesNotGenerate() throws Exception {
        var f = new Fixture(20);
        var commits = new ArrayList<CommitDto>();
        var entries = new ArrayList<String>();
        for (int i = 1; i <= 11; i++) {
            String sha = String.format("%040x", i);
            commits.add(new CommitDto(sha, "Change", "Author", Instant.EPOCH));
            if (i <= 8) {
                entries.add(entry(sha));
            }
        }
        when(f.git.getAllCommits(any(), anyString())).thenReturn(commits);
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree("{\"commits\":[" + String.join(",", entries) + "]}"));
        var result = f.service.classify(f.key, "main");
        assertEquals(8, result.get("classified"));
        assertEquals(11, result.get("total"));
        assertEquals(8, f.service.classifications(f.key, "main").get("classified"));
        verify(f.provider, times(1)).analyze(anyString(), anyString());
    }

    @Test
    void invalidBatchSavesNoPartialResultsButCountsAttempt() throws Exception {
        var f = new Fixture(20);
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree("{\"commits\":[" + entry(SHA) + "," + entry(SHA) + "]}"));
        assertEquals("FAILED", f.service.classify(f.key, "main").get("status"));
        assertEquals(0, f.saved.size());
        assertEquals(1, f.usage.get());
    }

    @Test
    void classificationReusesExistingExplanations() throws Exception {
        var f = new Fixture(20);
        f.saved.put("COMMIT:" + SHA, VALID);
        f.saved.put("COMMIT:" + SHA2, VALID);
        assertEquals(2, f.service.classify(f.key, "main").get("classified"));
        verify(f.provider, never()).analyze(anyString(), anyString());
    }

    @Test
    void quotaBlocksGenerationAndProviderFailureCounts() throws Exception {
        var f = new Fixture(1);
        when(f.provider.analyze(anyString(), anyString())).thenThrow(new IllegalStateException("Busy"));
        assertEquals("FAILED", f.service.explain(f.key, "COMMIT", SHA, null).get("status"));
        var blocked = f.service.explain(f.key, "COMMIT", SHA, null);
        assertEquals("Daily AI limit reached", blocked.get("error"));
        verify(f.provider, times(1)).analyze(anyString(), anyString());
    }

    @Test
    void fileCacheUsesRevisionAndPathAndRejectsBinary() throws Exception {
        var f = new Fixture(20);
        when(f.git.getFileContent(any(), eq(SHA), eq("App.java"), eq(48000))).thenReturn(new FileContentDto("App.java", SHA, 20, false, "class App {}"));
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED", f.service.explain(f.key, "FILE_SNAPSHOT", SHA, "App.java").get("status"));
        assertEquals("COMPLETED", f.service.explain(f.key, "FILE_SNAPSHOT", SHA, "App.java").get("status"));
        when(f.git.getFileContent(any(), eq(SHA), eq("image.png"), eq(48000))).thenReturn(new FileContentDto("image.png", SHA, 20, true, null));
        assertEquals("FAILED", f.service.explain(f.key, "FILE_SNAPSHOT", SHA, "image.png").get("status"));
        verify(f.provider, times(1)).analyze(anyString(), anyString());
    }

    @Test
    void repositoryUsesSnapshotAndCachesResult() throws Exception {
        var f = new Fixture(20);
        when(f.git.getRepositoryTree(any(), eq(SHA))).thenReturn(new RepositoryTreeDto(SHA, new RepositoryTreeNodeDto("root", "", "DIRECTORY", List.of(new RepositoryTreeNodeDto("App.java", "App.java", "FILE", List.of())))));
        when(f.git.getFileContent(any(), eq(SHA), anyString(), eq(48000))).thenReturn(new FileContentDto("README.md", SHA, 20, false, "Example application"));
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED", f.service.explain(f.key, "REPOSITORY", SHA, null).get("status"));
        assertEquals("COMPLETED", f.service.explain(f.key, "REPOSITORY", SHA, null).get("status"));
        verify(f.provider).analyze(contains("REPOSITORY"), contains("App.java"));
    }

    @Test
    void privateRepositoriesAndUnsafePathsDoNotReachProvider() throws Exception {
        var f = new Fixture(20);
        assertEquals("FAILED", f.service.explain(f.key, "FILE_SNAPSHOT", SHA, "../secret").get("status"));
        when(f.repo.getVisibility()).thenReturn(RepositoryVisibility.PRIVATE);
        assertThrows(ResponseStatusException.class, () -> f.service.explain(f.key, "COMMIT", SHA, null));
        verify(f.provider, never()).analyze(anyString(), anyString());
    }

    @Test
    void periodUsesInclusiveUtcBoundsAndCachesByHeadAndRange() throws Exception {
        var f = new Fixture(20);
        when(f.git.getAllCommits(any(), anyString())).thenReturn(List.of(
                new CommitDto(SHA, "Before", "Author", Instant.parse("2026-10-01T23:59:59Z")),
                new CommitDto(SHA2, "Inside", "Author", Instant.parse("2026-10-02T23:59:59Z"))));
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED", f.service.explain(f.key, "PERIOD", SHA2, null, "2026-10-02", "2026-10-02").get("status"));
        assertEquals("COMPLETED", f.service.explain(f.key, "PERIOD", SHA2, null, "2026-10-02", "2026-10-02").get("status"));
        verify(f.provider, times(1)).analyze(contains("PERIOD"), contains("Matching commits: 1"));
        verify(f.git, never()).getCommitDetail(any(), eq(SHA));
        assertEquals("COMPLETED", f.service.explain(f.key, "PERIOD", SHA2, null, "2026-10-01", "2026-10-02").get("status"));
        verify(f.provider, times(2)).analyze(anyString(), anyString());
    }

    @Test
    void invalidOrEmptyPeriodsNeverGenerate() throws Exception {
        var f = new Fixture(20);
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA, null, null, null).get("status"));
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA, null, "bad", null).get("status"));
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA, null, "2026-12-01", "2026-11-01").get("status"));
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA, null, "2026-11-01", "2026-12-01").get("status"));
        verify(f.provider, never()).analyze(anyString(), anyString());
    }

    @Test
    void periodEvidenceIsBoundedAndReportsSampling() throws Exception {
        var f = new Fixture(20);
        var commits = new ArrayList<CommitDto>();
        for (int i = 0; i < 60; i++) {
            commits.add(new CommitDto(String.format("%040x", i), "Change", "Author", Instant.EPOCH));
        }
        when(f.git.getAllCommits(any(), anyString())).thenReturn(commits);
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED", f.service.explain(f.key, "PERIOD", SHA, null, "1970-01-01", null).get("status"));
        verify(f.provider).analyze(anyString(), argThat(e -> e.contains("latest 30") && e.contains("Matching commits: 60") && e.length() < 16100));
    }

    @Test
    void temporaryProviderErrorsHaveShortMessagesAndCountUsage() throws Exception {
        var f = new Fixture(20);
        when(f.provider.analyze(anyString(), anyString())).thenThrow(new IllegalStateException("Gemini HTTP 503: high demand"));
        assertEquals("AI is busy. Try again shortly.", f.service.explain(f.key, "COMMIT", SHA, null).get("error"));
        assertEquals(1, f.usage.get());
    }

    @Test
    void fileChangeAndEvolutionUseSeparateCaches() throws Exception {
        var f = new Fixture(20);
        when(f.history.evidence(any(), eq(SHA), eq("App.java"), anyBoolean())).thenReturn("File patch evidence");
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        for (String type : List.of("FILE_CHANGE", "FILE_EVOLUTION")) {
            assertEquals("COMPLETED", f.service.explain(f.key, type, SHA, "App.java").get("status"));
            assertEquals("COMPLETED", f.service.explain(f.key, type, SHA, "App.java").get("status"));
        }
        verify(f.history).evidence(any(), eq(SHA), eq("App.java"), eq(false));
        verify(f.history).evidence(any(), eq(SHA), eq("App.java"), eq(true));
        verify(f.provider, times(2)).analyze(anyString(), anyString());
    }

    @Test
    void commitRangeUsesAncestryAndExcludesStart() throws Exception {
        var f = new Fixture(20);
        when(f.git.isAncestor(any(), anyString(), anyString())).thenReturn(true);
        when(f.git.getCommitsBetween(any(), eq(SHA), eq(SHA2))).thenReturn(List.of(new CommitDto(SHA2, "Range change", "Author", Instant.EPOCH)));
        when(f.provider.analyze(anyString(), anyString())).thenReturn(f.mapper.readTree(VALID));
        for (int i = 0; i < 2; i++) {
            assertEquals("COMPLETED", f.service.explain(f.key, "PERIOD", SHA2, null, null, null, SHA, SHA2).get("status"));
        }
        verify(f.provider).analyze(anyString(), contains("start excluded, end included"));
        verify(f.git).getCommitsBetween(any(), eq(SHA), eq(SHA2));
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA2, null, "2026-01-01", null, SHA, SHA2).get("status"));
        when(f.git.isAncestor(any(), anyString(), anyString())).thenReturn(false);
        assertEquals("FAILED", f.service.explain(f.key, "PERIOD", SHA2, null, null, null, SHA2, SHA).get("status"));
        verify(f.provider, times(1)).analyze(anyString(), anyString());
    }

    @Test
    void evidenceIsBounded() {
        assertTrue(ExplicitAiService.bounded("x".repeat(20000), 12000).length() < 12100);
    }

    static String entry(String sha) {
        return "{\"sha\":\"" + sha + "\",\"summary\":\"Change\",\"category\":\"maintenance\",\"tags\":[\"cleanup\"]}";
    }

    static class Fixture {

        UUID key = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper();
        AiFileHistory history = mock(AiFileHistory.class);
        LlmProvider provider = mock(LlmProvider.class);
        GitMiningService git = mock(GitMiningService.class);
        RepositoryEntity repo = mock(RepositoryEntity.class);
        Map<String, String> saved = new LinkedHashMap<>();
        AtomicInteger usage = new AtomicInteger();
        ExplicitAiService service;

        Fixture(int budget) throws Exception {
            when(repo.getId()).thenReturn(1L);
            when(repo.getName()).thenReturn("Example");
            when(repo.getDefaultBranch()).thenReturn("main");
            when(repo.getVisibility()).thenReturn(RepositoryVisibility.PUBLIC);
            when(repo.getLocalGitDirectory()).thenReturn("/tmp/ai.git");
            var repositories = mock(RepositoryJpaRepository.class);
            when(repositories.findByRepoKey(key)).thenReturn(Optional.of(repo));
            when(provider.provider()).thenReturn("gemini");
            when(provider.model()).thenReturn("test");
            when(git.commitExists(any(), anyString())).thenReturn(true);
            when(git.getRemoteBranchHead(any(), anyString())).thenReturn(SHA2);
            when(git.getAllCommits(any(), anyString())).thenReturn(List.of(new CommitDto(SHA, "First", "Author", Instant.EPOCH), new CommitDto(SHA2, "Second", "Author", Instant.EPOCH)));
            when(git.getCommitDetail(any(), anyString())).thenAnswer(i -> new CommitDetailDto(i.getArgument(1), "aaaaaaa", "Test", null, null, List.of(), 0, 0, 0, List.of()));
            var jdbc = mock(JdbcTemplate.class, i -> {
                if (i.getMethod().getName().equals("queryForObject")) {
                    return usage.get();
                }
                if (i.getMethod().getName().equals("queryForList")) {
                    String sql = i.getArgument(0);
                    if (sql.contains("DISTINCT ON")) {
                        List<Map<String, Object>> rows = new ArrayList<>();
                        saved.forEach((k, v) -> {
                            if (k.startsWith("CLASSIFY:")) {
                                rows.add(Map.of("target_key", k.substring(9), "result", v));
                        
                            }});
                        return rows;
                    }
                    if (sql.contains("ai_context_results")) {
                        String value = saved.get(i.getArgument(2) + ":" + i.getArgument(3));
                        return value == null ? List.of() : List.of(Map.of("result", value));
                    }
                    return List.of();
                }
                if (i.getMethod().getName().equals("update")) {
                    String sql = i.getArgument(0);
                    if (sql.contains("INSERT INTO ai_usage")) {
                        usage.incrementAndGet();
                    }
                    if (sql.contains("INSERT INTO ai_context_results")) {
                        saved.put(i.getArgument(2) + ":" + i.getArgument(3), i.getArgument(6));
                    }
                    return 1;
                }
                return RETURNS_DEFAULTS.answer(i);
            });
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            service = new ExplicitAiService(jdbc, repositories, git, provider, mapper, manager, budget, history, 48000);
        }
    }
}
