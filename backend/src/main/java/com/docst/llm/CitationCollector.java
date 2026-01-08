package com.docst.llm;

import com.docst.llm.model.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ThreadLocal-based collector for citations during LLM chat sessions.
 * Collects document sources referenced by tools (e.g., searchDocuments)
 * and provides them at the end of streaming responses.
 *
 * <p>Usage:
 * <pre>
 * citationCollector.clear();  // Start of request
 * // ... tool calls add citations ...
 * List&lt;Citation&gt; citations = citationCollector.getAndClear();  // End of request
 * </pre>
 *
 * <p>Note: Must call clear() or getAndClear() to prevent memory leaks
 * when using thread pools.
 */
@Component
public class CitationCollector {

    private static final ThreadLocal<List<Citation>> CITATIONS =
        ThreadLocal.withInitial(ArrayList::new);

    /**
     * Adds a citation to the current thread's collection.
     *
     * @param citation the citation to add
     */
    public void add(Citation citation) {
        if (citation != null) {
            CITATIONS.get().add(citation);
        }
    }

    /**
     * Adds multiple citations to the current thread's collection.
     *
     * @param citations the citations to add
     */
    public void addAll(List<Citation> citations) {
        if (citations != null) {
            CITATIONS.get().addAll(citations);
        }
    }

    /**
     * Returns the current citations and clears the ThreadLocal.
     * This should be called at the end of each request to prevent memory leaks.
     *
     * @return a new list containing all collected citations
     */
    public List<Citation> getAndClear() {
        List<Citation> result = new ArrayList<>(CITATIONS.get());
        CITATIONS.remove();
        return result;
    }

    /**
     * Returns the current citations without clearing.
     *
     * @return an unmodifiable view of the current citations
     */
    public List<Citation> get() {
        return List.copyOf(CITATIONS.get());
    }

    /**
     * Clears the ThreadLocal without returning the citations.
     * Should be called at the start of each request or on error cleanup.
     */
    public void clear() {
        CITATIONS.remove();
    }

    /**
     * Returns the number of citations collected.
     *
     * @return the citation count
     */
    public int size() {
        return CITATIONS.get().size();
    }

    /**
     * Checks if any citations have been collected.
     *
     * @return true if no citations, false otherwise
     */
    public boolean isEmpty() {
        return CITATIONS.get().isEmpty();
    }
}
