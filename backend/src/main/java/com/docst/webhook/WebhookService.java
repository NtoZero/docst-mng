package com.docst.webhook;

import com.docst.gitrepo.Repository;
import com.docst.gitrepo.repository.RepositoryRepository;
import com.docst.sync.service.SyncService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Webhook 처리 서비스.
 * GitHub webhook 이벤트의 서명 검증 및 이벤트 처리를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RepositoryRepository repositoryRepository;
    private final SyncService syncService;
    private final ObjectMapper objectMapper;

    @Value("${docst.webhook.github.secret:}")
    private String webhookSecret;

    /**
     * GitHub webhook 서명을 검증한다.
     *
     * @param payload   요청 본문 (raw JSON string)
     * @param signature X-Hub-Signature-256 헤더 값 (예: "sha256=...")
     * @return 서명이 유효하면 true
     */
    public boolean verifySignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Webhook secret is not configured. Signature verification disabled.");
            return true; // 개발 환경에서는 검증 생략 가능
        }

        if (signature == null || !signature.startsWith("sha256=")) {
            log.warn("Invalid signature format: {}", signature);
            return false;
        }

        try {
            // HMAC-SHA256으로 서명 계산
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);

            // 타이밍 공격 방지를 위한 일정 시간 비교
            return java.security.MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    /**
     * Webhook 이벤트를 처리한다.
     *
     * @param event   이벤트 타입 (push, ping, etc.)
     * @param payload 이벤트 페이로드 (JSON string)
     */
    public void processWebhookEvent(String event, String payload) {
        if (event == null) {
            log.warn("Received webhook without event type");
            return;
        }

        switch (event) {
            case "ping" -> handlePingEvent(payload);
            case "push" -> handlePushEvent(payload);
            default -> log.info("Ignoring unsupported webhook event: {}", event);
        }
    }

    /**
     * Ping 이벤트를 처리한다.
     * GitHub에서 webhook 설정 시 전송하는 테스트 이벤트.
     */
    private void handlePingEvent(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            String zen = json.path("zen").asText();
            log.info("Received GitHub ping: {}", zen);
        } catch (Exception e) {
            log.error("Failed to parse ping event", e);
        }
    }

    /**
     * Push 이벤트를 처리한다.
     * 레포지토리에 새로운 커밋이 푸시되면 자동으로 동기화를 실행한다.
     *
     * @param payload Push 이벤트 페이로드
     */
    private void handlePushEvent(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            // 레포지토리 정보 추출
            JsonNode repoNode = json.path("repository");
            String fullName = repoNode.path("full_name").asText(); // "owner/repo"
            String[] parts = fullName.split("/");
            if (parts.length != 2) {
                log.warn("Invalid repository full_name: {}", fullName);
                return;
            }
            String owner = parts[0];
            String repoName = parts[1];

            // 브랜치 정보 추출
            String ref = json.path("ref").asText(); // "refs/heads/main"
            String branch = ref.replace("refs/heads/", "");

            // 커밋 정보 추출
            JsonNode headCommitNode = json.path("head_commit");
            String commitSha = headCommitNode.path("id").asText();
            String commitMessage = headCommitNode.path("message").asText();

            log.info("Received push event: repository={}, branch={}, commit={}, message={}",
                    fullName, branch, commitSha.substring(0, 7), commitMessage);

            // 해당 레포지토리 찾기
            Optional<Repository> repoOpt = repositoryRepository.findByOwnerAndName(owner, repoName);
            if (repoOpt.isEmpty()) {
                log.warn("Repository not found in database: {}", fullName);
                return;
            }

            Repository repository = repoOpt.get();

            // 기본 브랜치가 아니면 무시
            if (!branch.equals(repository.getDefaultBranch())) {
                log.info("Ignoring push to non-default branch: {} (default: {})",
                        branch, repository.getDefaultBranch());
                return;
            }

            // 비동기 동기화 실행
            log.info("Triggering incremental sync for repository: {}", fullName);
            syncService.startSync(repository.getId(), branch);

        } catch (Exception e) {
            log.error("Failed to handle push event", e);
            throw new RuntimeException("Failed to process push event", e);
        }
    }
}
