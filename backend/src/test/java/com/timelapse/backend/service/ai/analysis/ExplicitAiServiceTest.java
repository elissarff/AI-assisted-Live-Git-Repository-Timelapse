package com.timelapse.backend.service.ai.analysis;

import java.util.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.*;
import com.timelapse.backend.dto.*;
import com.timelapse.backend.entity.RepositoryEntity;
import com.timelapse.backend.repository.RepositoryJpaRepository;
import com.timelapse.backend.service.git.GitMiningService;
import com.timelapse.backend.types.RepositoryVisibility;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExplicitAiServiceTest {
    static final String SHA="a".repeat(40);
    static final String SHA2="b".repeat(40);
    static final String VALID="""
        {"summary":"Adds validation","purpose":"Reject invalid input","significance":"Minor","category":"bugfix","tags":["validation"],"affectedComponents":["API"]}
        """;
    @Test void explanationIsCachedAndSerializedAsPlainData() throws Exception {
        var f=new Fixture(20);
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree(VALID));
        var first=f.service.explain(f.key,"COMMIT",SHA,null);
        var second=f.service.explain(f.key,"COMMIT",SHA,null);
        assertEquals("COMPLETED",first.get("status"));assertEquals(first,second);
        verify(f.provider,times(1)).analyze(anyString(),anyString());
        String json=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(first);
        assertEquals("Adds validation",f.mapper.readTree(json).path("analysis").path("summary").asText());
        assertEquals(1,f.usage.get());
    }
    @Test void classificationBatchesAreCachedAndNeedNoFurtherCalls() throws Exception {
        var f=new Fixture(20);
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree("{\"commits\":["+entry(SHA)+","+entry(SHA2)+"]}"));
        assertEquals(2,f.service.classify(f.key,"main").get("classified"));
        assertEquals(2,f.service.classify(f.key,"main").get("classified"));
        verify(f.provider,times(1)).analyze(anyString(),anyString());
    }
    @Test void classificationUsesBoundedBatchesAndReadOnlyListingDoesNotGenerate() throws Exception {
        var f=new Fixture(20);
        var commits=new ArrayList<CommitDto>();
        var entries=new ArrayList<String>();
        for(int i=1;i<=11;i++) {
            String sha=String.format("%040x",i);
            commits.add(new CommitDto(sha,"Change","Author",Instant.EPOCH));
            if(i<=8) entries.add(entry(sha));
        }
        when(f.git.getAllCommits(any(),anyString())).thenReturn(commits);
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree("{\"commits\":["+String.join(",",entries)+"]}"));
        var result=f.service.classify(f.key,"main");
        assertEquals(8,result.get("classified")); assertEquals(11,result.get("total"));
        assertEquals(8,f.service.classifications(f.key,"main").get("classified"));
        verify(f.provider,times(1)).analyze(anyString(),anyString());
    }
    @Test void invalidBatchSavesNoPartialResultsButCountsAttempt() throws Exception {
        var f=new Fixture(20);
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree("{\"commits\":["+entry(SHA)+","+entry(SHA)+"]}"));
        assertEquals("FAILED",f.service.classify(f.key,"main").get("status"));
        assertEquals(0,f.saved.size());assertEquals(1,f.usage.get());
    }
    @Test void classificationReusesExistingExplanations() throws Exception {
        var f=new Fixture(20);
        f.saved.put("COMMIT:"+SHA,VALID);f.saved.put("COMMIT:"+SHA2,VALID);
        assertEquals(2,f.service.classify(f.key,"main").get("classified"));
        verify(f.provider,never()).analyze(anyString(),anyString());
    }
    @Test void quotaBlocksGenerationAndProviderFailureCounts() throws Exception {
        var f=new Fixture(1);
        when(f.provider.analyze(anyString(),anyString())).thenThrow(new IllegalStateException("Busy"));
        assertEquals("FAILED",f.service.explain(f.key,"COMMIT",SHA,null).get("status"));
        var blocked=f.service.explain(f.key,"COMMIT",SHA,null);
        assertEquals("Daily AI limit reached",blocked.get("error"));
        verify(f.provider,times(1)).analyze(anyString(),anyString());
    }
    @Test void fileCacheUsesRevisionAndPathAndRejectsBinary() throws Exception {
        var f=new Fixture(20);
        when(f.git.getFileContent(any(),eq(SHA),eq("App.java"))).thenReturn(new FileContentDto("App.java",SHA,20,false,"class App {}"));
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED",f.service.explain(f.key,"FILE_SNAPSHOT",SHA,"App.java").get("status"));
        assertEquals("COMPLETED",f.service.explain(f.key,"FILE_SNAPSHOT",SHA,"App.java").get("status"));
        when(f.git.getFileContent(any(),eq(SHA),eq("image.png"))).thenReturn(new FileContentDto("image.png",SHA,20,true,null));
        assertEquals("FAILED",f.service.explain(f.key,"FILE_SNAPSHOT",SHA,"image.png").get("status"));
        verify(f.provider,times(1)).analyze(anyString(),anyString());
    }
    @Test void repositoryUsesSnapshotAndCachesResult() throws Exception {
        var f=new Fixture(20);
        when(f.git.getRepositoryTree(any(),eq(SHA))).thenReturn(new RepositoryTreeDto(SHA,new RepositoryTreeNodeDto("root","","DIRECTORY",List.of(new RepositoryTreeNodeDto("App.java","App.java","FILE",List.of())))));
        when(f.git.getFileContent(any(),eq(SHA),anyString())).thenReturn(new FileContentDto("README.md",SHA,20,false,"Example application"));
        when(f.provider.analyze(anyString(),anyString())).thenReturn(f.mapper.readTree(VALID));
        assertEquals("COMPLETED",f.service.explain(f.key,"REPOSITORY",SHA,null).get("status"));
        assertEquals("COMPLETED",f.service.explain(f.key,"REPOSITORY",SHA,null).get("status"));
        verify(f.provider).analyze(contains("REPOSITORY"),contains("App.java"));
    }
    @Test void privateRepositoriesAndUnsafePathsDoNotReachProvider() throws Exception {
        var f=new Fixture(20);
        assertEquals("FAILED",f.service.explain(f.key,"FILE_SNAPSHOT",SHA,"../secret").get("status"));
        when(f.repo.getVisibility()).thenReturn(RepositoryVisibility.PRIVATE);
        assertThrows(ResponseStatusException.class,()->f.service.explain(f.key,"COMMIT",SHA,null));
        verify(f.provider,never()).analyze(anyString(),anyString());
    }
    @Test void evidenceIsBounded() { assertTrue(ExplicitAiService.bounded("x".repeat(20000),12000).length()<12100); }
    static String entry(String sha) { return "{\"sha\":\""+sha+"\",\"summary\":\"Change\",\"category\":\"maintenance\",\"tags\":[\"cleanup\"]}"; }
    static class Fixture {
        UUID key=UUID.randomUUID(); ObjectMapper mapper=new ObjectMapper();
        LlmProvider provider=mock(LlmProvider.class);GitMiningService git=mock(GitMiningService.class);
        RepositoryEntity repo=mock(RepositoryEntity.class);Map<String,String> saved=new LinkedHashMap<>();AtomicInteger usage=new AtomicInteger();ExplicitAiService service;
        Fixture(int budget) throws Exception {
            when(repo.getId()).thenReturn(1L);when(repo.getName()).thenReturn("Example");when(repo.getDefaultBranch()).thenReturn("main");
            when(repo.getVisibility()).thenReturn(RepositoryVisibility.PUBLIC);when(repo.getLocalGitDirectory()).thenReturn("/tmp/ai.git");
            var repositories=mock(RepositoryJpaRepository.class);when(repositories.findByRepoKey(key)).thenReturn(Optional.of(repo));
            when(provider.provider()).thenReturn("gemini");when(provider.model()).thenReturn("test");
            when(git.commitExists(any(),anyString())).thenReturn(true);when(git.getRemoteBranchHead(any(),anyString())).thenReturn(SHA2);
            when(git.getAllCommits(any(),anyString())).thenReturn(List.of(new CommitDto(SHA,"First","Author",Instant.EPOCH),new CommitDto(SHA2,"Second","Author",Instant.EPOCH)));
            when(git.getCommitDetail(any(),anyString())).thenAnswer(i->new CommitDetailDto(i.getArgument(1),"aaaaaaa","Test",null,null,List.of(),0,0,0,List.of()));
            var jdbc=mock(JdbcTemplate.class,i->{
                if(i.getMethod().getName().equals("queryForObject")) return usage.get();
                if(i.getMethod().getName().equals("queryForList")) {
                    String sql=i.getArgument(0);
                    if(sql.contains("DISTINCT ON")) {
                        List<Map<String,Object>> rows=new ArrayList<>();
                        saved.forEach((k,v)->{ if(k.startsWith("CLASSIFY:")) rows.add(Map.of("target_key",k.substring(9),"result",v)); });return rows;
                    }
                    if(sql.contains("ai_context_results")) {
                        String value=saved.get(i.getArgument(2)+":"+i.getArgument(3));
                        return value==null?List.of():List.of(Map.of("result",value));
                    }
                    return List.of();
                }
                if(i.getMethod().getName().equals("update")) {
                    String sql=i.getArgument(0);
                    if(sql.contains("INSERT INTO ai_usage")) usage.incrementAndGet();
                    if(sql.contains("INSERT INTO ai_context_results")) saved.put(i.getArgument(2)+":"+i.getArgument(3),i.getArgument(6));
                    return 1;
                }
                return RETURNS_DEFAULTS.answer(i);
            });
            var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            service=new ExplicitAiService(jdbc,repositories,git,provider,mapper,manager,budget);
        }
    }
}
