package com.docst.chunking;

import com.docst.document.DocChunk;
import com.docst.document.repository.DocChunkRepository;
import com.docst.document.DocumentVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 청킹 서비스.
 * 문서 버전을 청크로 분할하고 저장/관리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkingService {

    private final MarkdownChunker markdownChunker;
    private final DocChunkRepository docChunkRepository;

    /**
     * 문서 버전을 청킹하여 저장한다.
     * 기존 청크가 있으면 삭제하고 새로 생성한다.
     *
     * @param documentVersion 문서 버전
     * @return 생성된 청크 목록
     */
    @Transactional
    public List<DocChunk> chunkAndSave(DocumentVersion documentVersion) {
        if (documentVersion == null) {
            throw new IllegalArgumentException("DocumentVersion cannot be null");
        }

        String content = documentVersion.getContent();
        if (content == null || content.isEmpty()) {
            log.debug("DocumentVersion {} has no content, skipping chunking", documentVersion.getId());
            return List.of();
        }

        // 기존 청크 삭제
        deleteChunksByDocumentVersion(documentVersion.getId());

        // Markdown 청킹 수행
        List<ChunkResult> chunkResults = markdownChunker.chunk(content);
        log.debug("Created {} chunks for DocumentVersion {}", chunkResults.size(), documentVersion.getId());

        // DocChunk 엔티티 생성 및 저장
        List<DocChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkResults.size(); i++) {
            ChunkResult result = chunkResults.get(i);

            DocChunk chunk = new DocChunk(
                documentVersion,
                i,
                result.content(),
                result.tokenCount()
            ).withHeadingPath(result.headingPath());

            chunks.add(chunk);
        }

        List<DocChunk> savedChunks = docChunkRepository.saveAll(chunks);
        log.info("Saved {} chunks for DocumentVersion {}", savedChunks.size(), documentVersion.getId());

        return savedChunks;
    }

    /**
     * 문서 버전의 청크 목록을 조회한다.
     *
     * @param documentVersionId 문서 버전 ID
     * @return 청크 목록 (청크 인덱스 순)
     */
    @Transactional(readOnly = true)
    public List<DocChunk> getChunks(UUID documentVersionId) {
        return docChunkRepository.findByDocumentVersionIdOrderByChunkIndex(documentVersionId);
    }

    /**
     * 문서 버전의 청크를 모두 삭제한다.
     *
     * @param documentVersionId 문서 버전 ID
     */
    @Transactional
    public void deleteChunksByDocumentVersion(UUID documentVersionId) {
        long count = docChunkRepository.countByDocumentVersionId(documentVersionId);
        if (count > 0) {
            docChunkRepository.deleteByDocumentVersionId(documentVersionId);
            log.debug("Deleted {} chunks for DocumentVersion {}", count, documentVersionId);
        }
    }

    /**
     * 문서 버전의 청크 개수를 조회한다.
     *
     * @param documentVersionId 문서 버전 ID
     * @return 청크 개수
     */
    @Transactional(readOnly = true)
    public long countChunks(UUID documentVersionId) {
        return docChunkRepository.countByDocumentVersionId(documentVersionId);
    }

    /**
     * 여러 문서 버전을 일괄 청킹한다.
     * 각 문서 버전마다 개별 트랜잭션으로 처리하여 일부 실패 시에도 나머지는 계속 진행한다.
     *
     * @param documentVersions 문서 버전 목록
     * @return 성공적으로 청킹된 문서 버전 ID 목록
     */
    public List<UUID> batchChunk(List<DocumentVersion> documentVersions) {
        List<UUID> successIds = new ArrayList<>();

        for (DocumentVersion version : documentVersions) {
            try {
                chunkAndSave(version);
                successIds.add(version.getId());
            } catch (Exception e) {
                log.error("Failed to chunk DocumentVersion {}: {}", version.getId(), e.getMessage(), e);
                // 개별 실패는 로그만 남기고 계속 진행
            }
        }

        log.info("Batch chunking completed: {}/{} successful",
            successIds.size(), documentVersions.size());

        return successIds;
    }
}
