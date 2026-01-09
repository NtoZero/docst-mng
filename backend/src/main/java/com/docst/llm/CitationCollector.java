package com.docst.llm;

import com.docst.llm.model.Citation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-based collector for citations during LLM chat sessions.
 * Collects document sources referenced by tools (e.g., searchDocuments)
 * and provides them at the end of streaming responses.
 *
 * <p>Uses ConcurrentHashMap with sessionId as key to support reactive/async
 * streaming where tool execution and response streaming run on different threads.
 *
 * <p>Usage:
 * <pre>
 * String sessionId = "unique-session-id";
 * citationCollector.clear(sessionId);  // Start of request
 * // ... tool calls add citations via add(sessionId, citation) ...
 * List&lt;Citation&gt; citations = citationCollector.getAndClear(sessionId);  // End of request
 * </pre>
 *
 * <p>Note: Must call clear(sessionId) or getAndClear(sessionId) to prevent memory leaks.
 */
@Component
@Slf4j
public class CitationCollector {

    private final Map<String, List<Citation>> sessionCitations = new ConcurrentHashMap<>();

    /**
     * Adds a citation for a specific session.
     *
     * @param sessionId the session identifier
     * @param citation the citation to add
     */
    public void add(String sessionId, Citation citation) {
        if (sessionId != null && citation != null) {
            sessionCitations.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(citation);
            log.debug("Added citation for session {}: path={}", sessionId, citation.path());
        }
    }

    /**
     * Adds multiple citations for a specific session.
     *
     * @param sessionId the session identifier
     * @param citations the citations to add
     */
    public void addAll(String sessionId, List<Citation> citations) {
        if (sessionId != null && citations != null && !citations.isEmpty()) {
            sessionCitations.computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(citations);
            log.debug("Added {} citations for session {}", citations.size(), sessionId);
        }
    }

    /**
     * Returns the citations for a session and removes them.
     * This should be called at the end of each request to prevent memory leaks.
     *
     * @param sessionId the session identifier
     * @return a new list containing all collected citations, or empty list if none
     */
    public List<Citation> getAndClear(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<Citation> citations = sessionCitations.remove(sessionId);
        if (citations == null) {
            log.debug("No citations found for session {}", sessionId);
            return Collections.emptyList();
        }
        log.debug("Retrieved and cleared {} citations for session {}", citations.size(), sessionId);
        return new ArrayList<>(citations);
    }

    /**
     * Returns the citations for a session without clearing.
     *
     * @param sessionId the session identifier
     * @return an unmodifiable view of the current citations
     */
    public List<Citation> get(String sessionId) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<Citation> citations = sessionCitations.get(sessionId);
        return citations != null ? List.copyOf(citations) : Collections.emptyList();
    }

    /**
     * Clears citations for a session without returning them.
     * Should be called at the start of each request or on error cleanup.
     *
     * @param sessionId the session identifier
     */
    public void clear(String sessionId) {
        if (sessionId != null) {
            sessionCitations.remove(sessionId);
            log.debug("Cleared citations for session {}", sessionId);
        }
    }

    /**
     * Returns the number of citations collected for a session.
     *
     * @param sessionId the session identifier
     * @return the citation count
     */
    public int size(String sessionId) {
        if (sessionId == null) {
            return 0;
        }
        List<Citation> citations = sessionCitations.get(sessionId);
        return citations != null ? citations.size() : 0;
    }

    /**
     * Checks if any citations have been collected for a session.
     *
     * @param sessionId the session identifier
     * @return true if no citations, false otherwise
     */
    public boolean isEmpty(String sessionId) {
        return size(sessionId) == 0;
    }

    // ===== Legacy ThreadLocal methods for backward compatibility =====
    // TODO: Remove after migrating all callers

    @Deprecated
    public void add(Citation citation) {
        log.warn("Using deprecated add(Citation) without sessionId - citation will be lost in async context");
    }

    @Deprecated
    public void addAll(List<Citation> citations) {
        log.warn("Using deprecated addAll(List) without sessionId - citations will be lost in async context");
    }

    @Deprecated
    public List<Citation> getAndClear() {
        log.warn("Using deprecated getAndClear() without sessionId - returning empty list");
        return Collections.emptyList();
    }

    @Deprecated
    public List<Citation> get() {
        log.warn("Using deprecated get() without sessionId - returning empty list");
        return Collections.emptyList();
    }

    @Deprecated
    public void clear() {
        log.warn("Using deprecated clear() without sessionId - no-op");
    }

    @Deprecated
    public int size() {
        log.warn("Using deprecated size() without sessionId - returning 0");
        return 0;
    }

    @Deprecated
    public boolean isEmpty() {
        log.warn("Using deprecated isEmpty() without sessionId - returning true");
        return true;
    }
}
