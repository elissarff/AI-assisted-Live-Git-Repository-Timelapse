package com.timelapse.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.timelapse.backend.service.GitHubWebhookService;
import com.timelapse.backend.service.GitHubWebhookSignatureService;

@RestController
@RequestMapping("/api/webhooks/github")
public class GitHubWebhookController {

    private final GitHubWebhookService webhookService;

    private final GitHubWebhookSignatureService
            signatureService;

    public GitHubWebhookController(
            GitHubWebhookService webhookService,
            GitHubWebhookSignatureService signatureService
    ) {
        this.webhookService = webhookService;
        this.signatureService = signatureService;
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(

            @RequestHeader(
                    value = "X-GitHub-Event",
                    required = false
            )
            String event,

            @RequestHeader(
                    value = "X-Hub-Signature-256",
                    required = false
            )
            String signature,

            @RequestBody
            byte[] payload

    ) throws Exception {

        if (!signatureService.isValid(
                payload,
                signature
        )) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid webhook signature");
        }

        System.out.println(
                "GitHub event: " + event
        );

        /*
         * GitHub sends a ping event when
         * the webhook is initially created.
         */
        if ("ping".equals(event)) {

            System.out.println(
                    "GitHub webhook connected successfully!"
            );

            return ResponseEntity.ok(
                    "Webhook connected"
            );
        }

        if ("push".equals(event)) {

            webhookService.handlePush(
                    payload
            );
        }

        return ResponseEntity.ok(
                "Webhook received"
        );
    }
}