package com.timelapse.backend.service.github;

import com.timelapse.backend.config.GitProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GitHubWebhookSignatureServiceTest {
    @Test
    void validatesCorrectSignatureAndRejectsInvalidSignature() throws Exception {
        GitProperties properties = mock(GitProperties.class);
        when(properties.getGitHubWebhookSecret()).thenReturn("secret");
        GitHubWebhookSignatureService service = new GitHubWebhookSignatureService(properties);
        byte[] payload = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));

        assertTrue(service.isValid(payload, signature));
        assertFalse(service.isValid(payload, "sha256=deadbeef"));
        assertFalse(service.isValid(payload, null));
    }
}
