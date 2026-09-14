package com.timelapse.backend.service.github;

import com.timelapse.backend.service.RepositoryService;
import com.timelapse.backend.service.RepositorySyncService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GitHubWebhookServiceTest {
    @Test
    void invalidWebhookSignatureIsRejectedBeforePayloadProcessing() throws Exception {
        GitHubWebhookSignatureService signatures = mock(GitHubWebhookSignatureService.class);
        when(signatures.isValid(any(byte[].class), anyString())).thenReturn(false);

        GitHubWebhookService service = new GitHubWebhookService(
                signatures,
                mock(RepositoryService.class),
                mock(RepositorySyncService.class),
                mock(GitHubInstallationService.class),
                mock(ObjectMapper.class)
        );

        assertThrows(SecurityException.class,
                () -> service.handle("push", "sha256=bad", "delivery", "{}".getBytes()));
    }
}
