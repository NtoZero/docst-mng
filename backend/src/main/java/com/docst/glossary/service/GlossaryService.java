package com.docst.glossary.service;

import com.docst.admin.service.PgVectorDataSourceManager;
import com.docst.embedding.DynamicEmbeddingClientFactory;
import com.docst.glossary.GlossaryTerm;
import com.docst.glossary.repository.GlossaryTermRepository;
import com.docst.project.Project;
import com.docst.project.repository.ProjectRepository;
import com.docst.rag.config.RagConfigService;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.user.User;
import com.docst.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 용어 사전 서비스.
 * 용어 CRUD 및 키워드/시맨틱 검색을 제공한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class GlossaryService {

    private final GlossaryTermRepository glossaryTermRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PgVectorDataSourceManager pgVectorDataSourceManager;
    private final DynamicEmbeddingClientFactory embeddingClientFactory;
    private final RagConfigService ragConfigService;

    /**
     * 프로젝트의 모든 용어를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 용어 목록
     */
    public List<GlossaryTerm> findByProjectId(UUID projectId) {
        return glossaryTermRepository.findByProjectIdOrderByName(projectId);
    }

    /**
     * 프로젝트의 특정 카테고리 용어를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param category 카테고리
     * @return 용어 목록
     */
    public List<GlossaryTerm> findByProjectIdAndCategory(UUID projectId, String category) {
        return glossaryTermRepository.findByProjectIdAndCategoryOrderByName(projectId, category);
    }

    /**
     * 용어 ID로 조회한다.
     *
     * @param id 용어 ID
     * @return 용어 (없으면 empty)
     */
    public Optional<GlossaryTerm> findById(UUID id) {
        return glossaryTermRepository.findById(id);
    }

    /**
     * 프로젝트와 용어명으로 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param name 용어명
     * @return 용어 (없으면 empty)
     */
    public Optional<GlossaryTerm> findByProjectIdAndName(UUID projectId, String name) {
        return glossaryTermRepository.findByProjectIdAndName(projectId, name);
    }

    /**
     * 프로젝트의 모든 카테고리 목록을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 카테고리 목록
     */
    public List<String> findCategories(UUID projectId) {
        return glossaryTermRepository.findDistinctCategoriesByProjectId(projectId);
    }

    /**
     * 새 용어를 생성한다.
     *
     * @param projectId 프로젝트 ID
     * @param name 용어명
     * @param definition 정의
     * @param synonyms 동의어 목록
     * @param category 카테고리
     * @param abbreviation 약어
     * @param relatedTerms 관련 용어 ID 목록
     * @param createdById 생성자 ID
     * @return 생성된 용어
     */
    @Transactional
    public GlossaryTerm create(UUID projectId, String name, String definition,
                               List<String> synonyms, String category, String abbreviation,
                               List<UUID> relatedTerms, UUID createdById) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (glossaryTermRepository.existsByProjectIdAndName(projectId, name)) {
            throw new IllegalArgumentException("Term already exists: " + name);
        }

        User createdBy = createdById != null
            ? userRepository.findById(createdById).orElse(null)
            : null;

        GlossaryTerm term = new GlossaryTerm(project, name, definition, createdBy);
        term.setSynonyms(synonyms != null ? synonyms : new ArrayList<>());
        term.setCategory(category);
        term.setAbbreviation(abbreviation);
        term.setRelatedTerms(relatedTerms != null ? relatedTerms : new ArrayList<>());

        GlossaryTerm saved = glossaryTermRepository.save(term);
        log.info("Created glossary term: {} in project {}", name, projectId);

        // 시맨틱 검색용 임베딩 생성
        embedTerm(projectId, saved);

        return saved;
    }

    /**
     * 용어를 수정한다.
     *
     * @param id 용어 ID
     * @param name 용어명
     * @param definition 정의
     * @param synonyms 동의어 목록
     * @param category 카테고리
     * @param abbreviation 약어
     * @param relatedTerms 관련 용어 ID 목록
     * @return 수정된 용어
     */
    @Transactional
    public GlossaryTerm update(UUID id, String name, String definition,
                               List<String> synonyms, String category, String abbreviation,
                               List<UUID> relatedTerms) {
        GlossaryTerm term = glossaryTermRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Term not found: " + id));

        UUID projectId = term.getProject().getId();

        // 이름 변경 시 중복 확인
        if (!term.getName().equals(name) &&
            glossaryTermRepository.existsByProjectIdAndName(projectId, name)) {
            throw new IllegalArgumentException("Term already exists: " + name);
        }

        term.setName(name);
        term.setDefinition(definition);
        term.setSynonyms(synonyms != null ? synonyms : new ArrayList<>());
        term.setCategory(category);
        term.setAbbreviation(abbreviation);
        term.setRelatedTerms(relatedTerms != null ? relatedTerms : new ArrayList<>());

        GlossaryTerm saved = glossaryTermRepository.save(term);
        log.info("Updated glossary term: {} in project {}", name, projectId);

        // 임베딩 재생성
        deleteEmbedding(id);
        embedTerm(projectId, saved);

        return saved;
    }

    /**
     * 용어를 삭제한다.
     *
     * @param id 용어 ID
     */
    @Transactional
    public void delete(UUID id) {
        GlossaryTerm term = glossaryTermRepository.findById(id).orElse(null);
        if (term != null) {
            deleteEmbedding(id);
            glossaryTermRepository.delete(term);
            log.info("Deleted glossary term: {} from project {}",
                term.getName(), term.getProject().getId());
        }
    }

    /**
     * 키워드로 용어를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 키워드
     * @return 용어 목록
     */
    public List<GlossaryTerm> searchByKeyword(UUID projectId, String query) {
        if (query == null || query.isBlank()) {
            return findByProjectId(projectId);
        }
        return glossaryTermRepository.searchByKeyword(projectId, query.trim());
    }

    /**
     * 시맨틱 검색으로 용어를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 용어 목록 (점수순)
     */
    public List<GlossaryTermWithScore> searchSemantic(UUID projectId, String query, int topK) {
        if (!pgVectorDataSourceManager.isEnabled()) {
            log.warn("PgVector is not enabled. Falling back to keyword search.");
            return searchByKeyword(projectId, query).stream()
                .map(term -> new GlossaryTermWithScore(term, 1.0))
                .toList();
        }

        PgVectorStore vectorStore = createVectorStore(projectId);
        if (vectorStore == null) {
            log.warn("Failed to create VectorStore. Falling back to keyword search.");
            return searchByKeyword(projectId, query).stream()
                .map(term -> new GlossaryTermWithScore(term, 1.0))
                .toList();
        }

        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.3)
            .build();

        List<Document> results;
        try {
            results = vectorStore.similaritySearch(request);
        } catch (Exception e) {
            log.error("Semantic search failed", e);
            return searchByKeyword(projectId, query).stream()
                .map(term -> new GlossaryTermWithScore(term, 1.0))
                .toList();
        }

        // 프로젝트 필터링 및 결과 변환
        List<GlossaryTermWithScore> termResults = new ArrayList<>();
        for (Document doc : results) {
            String contentType = (String) doc.getMetadata().get("content_type");
            if (!"glossary_term".equals(contentType)) {
                continue;
            }

            String docProjectId = (String) doc.getMetadata().get("project_id");
            if (!projectId.toString().equals(docProjectId)) {
                continue;
            }

            String termIdStr = (String) doc.getMetadata().get("glossary_term_id");
            if (termIdStr == null) {
                continue;
            }

            UUID termId = UUID.fromString(termIdStr);
            GlossaryTerm term = glossaryTermRepository.findById(termId).orElse(null);
            if (term == null) {
                continue;
            }

            Double distance = doc.getMetadata().get("distance") != null
                ? ((Number) doc.getMetadata().get("distance")).doubleValue()
                : null;
            double score = distance != null ? (1.0 - distance / 2.0) : 0.5;

            termResults.add(new GlossaryTermWithScore(term, score));
        }

        return termResults;
    }

    /**
     * 용어를 VectorStore에 임베딩한다.
     */
    private void embedTerm(UUID projectId, GlossaryTerm term) {
        if (!pgVectorDataSourceManager.isEnabled()) {
            return;
        }

        PgVectorStore vectorStore = createVectorStore(projectId);
        if (vectorStore == null) {
            return;
        }

        // 임베딩할 텍스트: 용어명 + 정의 + 동의어
        StringBuilder content = new StringBuilder();
        content.append(term.getName()).append("\n\n");
        content.append(term.getDefinition());
        if (term.getSynonyms() != null && !term.getSynonyms().isEmpty()) {
            content.append("\n\nSynonyms: ").append(String.join(", ", term.getSynonyms()));
        }
        if (term.getAbbreviation() != null) {
            content.append("\n\nAbbreviation: ").append(term.getAbbreviation());
        }

        // 메타데이터 구성
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("glossary_term_id", term.getId().toString());
        metadata.put("term_name", term.getName());
        metadata.put("project_id", projectId.toString());
        metadata.put("content_type", "glossary_term");
        if (term.getCategory() != null) {
            metadata.put("category", term.getCategory());
        }

        Document document = new Document(
            "glossary_" + term.getId().toString(),
            content.toString(),
            metadata
        );

        try {
            vectorStore.add(List.of(document));
            log.debug("Embedded glossary term: {}", term.getName());
        } catch (Exception e) {
            log.error("Failed to embed glossary term: {}", term.getName(), e);
        }
    }

    /**
     * 용어 임베딩을 VectorStore에서 삭제한다.
     */
    private void deleteEmbedding(UUID termId) {
        if (!pgVectorDataSourceManager.isEnabled()) {
            return;
        }

        PgVectorStore vectorStore = createVectorStore(null);
        if (vectorStore == null) {
            return;
        }

        try {
            vectorStore.delete(List.of("glossary_" + termId.toString()));
            log.debug("Deleted embedding for glossary term: {}", termId);
        } catch (Exception e) {
            log.error("Failed to delete embedding for glossary term: {}", termId, e);
        }
    }

    /**
     * VectorStore를 생성한다.
     */
    private PgVectorStore createVectorStore(UUID projectId) {
        JdbcTemplate jdbcTemplate = pgVectorDataSourceManager.getOrCreateJdbcTemplate();
        if (jdbcTemplate == null) {
            return null;
        }

        ResolvedRagConfig config = ragConfigService.resolve(projectId, null);
        EmbeddingModel embeddingModel = embeddingClientFactory.createEmbeddingModel(projectId, config);

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(pgVectorDataSourceManager.getDimensions())
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .removeExistingVectorStoreTable(false)
            .initializeSchema(false)
            .schemaName(pgVectorDataSourceManager.getSchemaName())
            .vectorTableName(pgVectorDataSourceManager.getTableName())
            .build();
    }

    /**
     * 용어와 점수를 포함하는 결과 레코드.
     */
    public record GlossaryTermWithScore(GlossaryTerm term, double score) {}
}
