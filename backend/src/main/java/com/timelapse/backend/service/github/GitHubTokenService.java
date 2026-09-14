package com.timelapse.backend.service.github;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.timelapse.backend.config.GitProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubTokenService {
    private final GitProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public GitHubTokenService(GitProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newHttpClient());
    }

    GitHubTokenService(GitProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public String getInstallationToken(Long installationId) throws Exception {
        ensureConfigured();
        String jwt = createAppJwt();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/app/installations/" + installationId + "/access_tokens"))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + jwt)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub installation token request failed with HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        String token = root.path("token").asText();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GitHub installation token response did not contain a token");
        }
        return token;
    }

    String createAppJwt() throws Exception {
        Instant now = Instant.now();
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iat\":" + now.minusSeconds(30).getEpochSecond()
                + ",\"exp\":" + now.plusSeconds(540).getEpochSecond()
                + ",\"iss\":\"" + escapeJson(properties.getAppId()) + "\"}");
        String unsigned = header + "." + payload;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(readPrivateKey(properties.getPrivateKey()));
        signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private void ensureConfigured() {
        if (properties.getAppId() == null || properties.getAppId().isBlank()) {
            throw new IllegalStateException("github.app-id is required for GitHub App repositories");
        }
        if (properties.getPrivateKey() == null || properties.getPrivateKey().isBlank()) {
            throw new IllegalStateException("github.private-key is required for GitHub App repositories");
        }
    }

    private PrivateKey readPrivateKey(String pem) throws Exception {
        String normalized = pem.replace("\\n", "\n").trim();
        boolean pkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
        normalized = normalized
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        if (pkcs1) {
            keyBytes = wrapPkcs1AsPkcs8(keyBytes);
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private byte[] wrapPkcs1AsPkcs8(byte[] pkcs1) {
        // PKCS#8 PrivateKeyInfo = SEQUENCE(version, rsaEncryption AlgorithmIdentifier, OCTET STRING(PKCS#1))
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] algorithm = new byte[] {
                0x30, 0x0d,
                0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] octet = derWrap((byte) 0x04, pkcs1);
        byte[] body = new byte[version.length + algorithm.length + octet.length];
        System.arraycopy(version, 0, body, 0, version.length);
        System.arraycopy(algorithm, 0, body, version.length, algorithm.length);
        System.arraycopy(octet, 0, body, version.length + algorithm.length, octet.length);
        return derWrap((byte) 0x30, body);
    }

    private byte[] derWrap(byte tag, byte[] content) {
        byte[] length = derLength(content.length);
        byte[] result = new byte[1 + length.length + content.length];
        result[0] = tag;
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(content, 0, result, 1 + length.length, content.length);
        return result;
    }

    private byte[] derLength(int length) {
        if (length < 128) return new byte[] {(byte) length};
        int temp = length;
        int count = 0;
        while (temp > 0) { count++; temp >>>= 8; }
        byte[] result = new byte[count + 1];
        result[0] = (byte) (0x80 | count);
        for (int i = count; i > 0; i--) {
            result[i] = (byte) (length & 0xff);
            length >>>= 8;
        }
        return result;
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
