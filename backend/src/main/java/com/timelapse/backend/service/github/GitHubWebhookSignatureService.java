package com.timelapse.backend.service.github;

import com.timelapse.backend.config.GitProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class GitHubWebhookSignatureService {
    private final GitProperties properties;

    public GitHubWebhookSignatureService(GitProperties properties) {
        this.properties = properties;
    }

    public boolean isValid(byte[] payload, String signatureHeader) throws Exception {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false;
        String secret = properties.getGitHubWebhookSecret();
        if (secret == null || secret.isBlank()) return false;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }
}
