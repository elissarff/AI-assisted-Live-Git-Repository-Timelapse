package com.timelapse.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;

@Service
public class GitHubWebhookSignatureService {

    private final GitProperties properties;

    public GitHubWebhookSignatureService(
            GitProperties properties
    ) {
        this.properties = properties;
    }

    public boolean isValid(
            byte[] payload,
            String signatureHeader
    ) throws Exception {

        if (signatureHeader == null ||
            !signatureHeader.startsWith("sha256=")) {

            return false;
        }

        String secret =
                properties.getGitHubWebhookSecret();

        if (secret == null ||
            secret.isBlank()) {

            return false;
        }

        Mac mac =
                Mac.getInstance("HmacSHA256");

        SecretKeySpec secretKey =
                new SecretKeySpec(
                        secret.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                );

        mac.init(secretKey);

        byte[] expectedHash =
                mac.doFinal(payload);

        String expectedSignature =
                "sha256="
                        + HexFormat
                            .of()
                            .formatHex(expectedHash);

        return MessageDigest.isEqual(
                expectedSignature.getBytes(
                        StandardCharsets.UTF_8
                ),
                signatureHeader.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }
}