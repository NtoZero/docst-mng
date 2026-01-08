package com.docst.llm.model;

import java.util.List;

/**
 * Sealed interface representing SSE stream events for LLM chat responses.
 * Supports typed streaming with content chunks and citation metadata.
 */
public sealed interface StreamEvent permits StreamEvent.ContentEvent, StreamEvent.CitationsEvent {

    /**
     * Content event containing a text chunk from the LLM response.
     */
    record ContentEvent(String content) implements StreamEvent {
        public String type() {
            return "content";
        }
    }

    /**
     * Citations event containing document sources referenced during the response.
     * Sent once at the end of the stream.
     */
    record CitationsEvent(List<Citation> citations) implements StreamEvent {
        public String type() {
            return "citations";
        }
    }
}
