package com.docst.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * GitHub Webhook 컨트롤러.
 * GitHub에서 전송하는 webhook 이벤트를 수신하고 처리한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
public class GitHubWebhookController {

    private final WebhookService webhookService;

    /**
     * GitHub webhook 이벤트를 수신한다.
     *
     * @param signature GitHub이 전송한 서명 (X-Hub-Signature-256 헤더)
     * @param event     이벤트 타입 (X-GitHub-Event 헤더)
     * @param delivery  Delivery ID (X-GitHub-Delivery 헤더)
     * @param payload   이벤트 페이로드 (JSON)
     * @return 200 OK
     */
    @PostMapping("/github")
    public ResponseEntity<Map<String, String>> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
            @RequestBody String payload) {

        log.info("Received GitHub webhook: event={}, delivery={}", event, delivery);

        // 서명 검증
        if (!webhookService.verifySignature(payload, signature)) {
            log.warn("Invalid webhook signature: delivery={}", delivery);
            return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
        }

        // 이벤트 처리
        try {
            webhookService.processWebhookEvent(event, payload);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Webhook processed"));
        } catch (Exception e) {
            log.error("Failed to process webhook: event={}, delivery={}", event, delivery, e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to process webhook"));
        }
    }

    /**
     * Webhook 연결 테스트용 ping 엔드포인트.
     *
     * @return pong
     */
    @GetMapping("/github/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "pong"));
    }
}
