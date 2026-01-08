package com.docst.llm.model;

/**
 * Citation record representing a document source referenced during LLM chat.
 * Used to provide source attribution for RAG (Retrieval Augmented Generation) responses.
 */
public record Citation(
    String documentId,
    String repositoryId,
    String path,
    String title,
    String headingPath,
    String chunkId,
    double score,
    String snippet
) {
    /**
     * Creates a Citation with minimal required fields.
     */
    public static Citation of(String documentId, String path, String snippet, double score) {
        return new Citation(documentId, null, path, null, null, null, score, snippet);
    }

    /**
     * Creates a Citation with heading path information.
     */
    public static Citation withHeading(
        String documentId,
        String repositoryId,
        String path,
        String headingPath,
        String chunkId,
        double score,
        String snippet
    ) {
        return new Citation(documentId, repositoryId, path, null, headingPath, chunkId, score, snippet);
    }
}
