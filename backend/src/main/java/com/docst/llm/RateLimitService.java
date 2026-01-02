package com.docst.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting 서비스.
 *
 * Sliding Window 방식으로 LLM API 호출 횟수를 제한.
 * IP 또는 사용자 ID 기반으로 제한을 적용할 수 있음.
 */
@Service
@Slf4j
public class RateLimitService {

    // Key: identifier (IP or UserID), Value: RateLimitBucket
    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Rate Limit 설정
     */
    private static final int MAX_REQUESTS_PER_WINDOW = 20; // 윈도우당 최대 요청 수
    private static final long WINDOW_SIZE_SECONDS = 60;     // 윈도우 크기 (60초)

    /**
     * Rate Limit 체크.
     *
     * @param identifier 사용자 식별자 (IP 주소 또는 User ID)
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String identifier) {
        RateLimitBucket bucket = buckets.computeIfAbsent(identifier, k -> new RateLimitBucket());

        synchronized (bucket) {
            long now = Instant.now().getEpochSecond();

            // 오래된 요청 기록 제거 (sliding window)
            bucket.requests.removeIf(timestamp -> now - timestamp > WINDOW_SIZE_SECONDS);

            // Rate limit 체크
            if (bucket.requests.size() >= MAX_REQUESTS_PER_WINDOW) {
                log.warn("Rate limit exceeded for identifier: {}", identifier);
                return false;
            }

            // 새 요청 기록 추가
            bucket.requests.add(now);
            return true;
        }
    }

    /**
     * Rate Limit 정보 조회.
     *
     * @param identifier 사용자 식별자
     * @return RateLimitInfo
     */
    public RateLimitInfo getRateLimitInfo(String identifier) {
        RateLimitBucket bucket = buckets.get(identifier);
        if (bucket == null) {
            return new RateLimitInfo(MAX_REQUESTS_PER_WINDOW, MAX_REQUESTS_PER_WINDOW, 0);
        }

        synchronized (bucket) {
            long now = Instant.now().getEpochSecond();
            bucket.requests.removeIf(timestamp -> now - timestamp > WINDOW_SIZE_SECONDS);

            int used = bucket.requests.size();
            int remaining = Math.max(0, MAX_REQUESTS_PER_WINDOW - used);
            long resetAt = bucket.requests.isEmpty() ? now : bucket.requests.get(0) + WINDOW_SIZE_SECONDS;

            return new RateLimitInfo(MAX_REQUESTS_PER_WINDOW, remaining, resetAt);
        }
    }

    /**
     * Rate Limit Bucket (사용자별 요청 기록)
     */
    private static class RateLimitBucket {
        // 요청 타임스탬프 목록 (Unix Epoch Seconds)
        final java.util.List<Long> requests = new java.util.ArrayList<>();
    }

    /**
     * Rate Limit 정보 DTO
     *
     * @param limit 최대 요청 수
     * @param remaining 남은 요청 수
     * @param resetAt 리셋 시각 (Unix Epoch Seconds)
     */
    public record RateLimitInfo(
        int limit,
        int remaining,
        long resetAt
    ) {}

    /**
     * 주기적인 Bucket 정리.
     *
     * 메모리 누수 방지를 위해 오래된 Bucket 제거.
     * (실제 프로덕션에서는 Scheduled Task로 실행)
     */
    public void cleanupOldBuckets() {
        long now = Instant.now().getEpochSecond();
        buckets.entrySet().removeIf(entry -> {
            RateLimitBucket bucket = entry.getValue();
            synchronized (bucket) {
                bucket.requests.removeIf(timestamp -> now - timestamp > WINDOW_SIZE_SECONDS);
                return bucket.requests.isEmpty();
            }
        });
        log.debug("Cleaned up old rate limit buckets. Remaining: {}", buckets.size());
    }
}
