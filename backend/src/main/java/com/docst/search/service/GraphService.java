package com.docst.search.service;

import com.docst.document.Document;
import com.docst.document.DocumentLink;
import com.docst.document.repository.DocumentLinkRepository;
import com.docst.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 그래프 분석 서비스.
 * 문서 간 링크 관계를 그래프로 분석한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphService {

    private final DocumentLinkRepository documentLinkRepository;
    private final DocumentRepository documentRepository;

    /**
     * 프로젝트 전체의 문서 관계 그래프를 생성한다.
     *
     * @param projectId 프로젝트 ID
     * @return 그래프 데이터
     */
    @Transactional(readOnly = true)
    public GraphData getProjectGraph(UUID projectId) {
        // 프로젝트의 모든 내부 링크 조회
        List<DocumentLink> links = documentLinkRepository.findInternalLinksByProjectId(projectId);

        // 노드 및 엣지 생성
        return buildGraph(links);
    }

    /**
     * 레포지토리의 문서 관계 그래프를 생성한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 그래프 데이터
     */
    @Transactional(readOnly = true)
    public GraphData getRepositoryGraph(UUID repositoryId) {
        // 레포지토리의 모든 내부 링크 조회
        List<DocumentLink> links = documentLinkRepository.findInternalLinksByRepositoryId(repositoryId);

        // 노드 및 엣지 생성
        return buildGraph(links);
    }

    /**
     * 특정 문서를 중심으로 연결된 문서 그래프를 생성한다.
     *
     * @param documentId 문서 ID
     * @param depth      탐색 깊이 (0: 직접 연결, 1: 1단계 이웃, ...)
     * @return 그래프 데이터
     */
    @Transactional(readOnly = true)
    public GraphData getDocumentGraph(UUID documentId, int depth) {
        Set<UUID> visitedDocuments = new HashSet<>();
        Set<DocumentLink> collectedLinks = new HashSet<>();

        // BFS로 연결된 문서 탐색
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> depthMap = new HashMap<>();

        queue.add(documentId);
        depthMap.put(documentId, 0);
        visitedDocuments.add(documentId);

        while (!queue.isEmpty()) {
            UUID currentDocId = queue.poll();
            int currentDepth = depthMap.get(currentDocId);

            if (currentDepth >= depth) {
                continue;
            }

            // 나가는 링크
            List<DocumentLink> outgoingLinks = documentLinkRepository.findBySourceDocumentId(currentDocId);
            for (DocumentLink link : outgoingLinks) {
                if (link.getTargetDocument() != null && !link.isBroken()) {
                    collectedLinks.add(link);
                    UUID targetId = link.getTargetDocument().getId();
                    if (!visitedDocuments.contains(targetId)) {
                        visitedDocuments.add(targetId);
                        depthMap.put(targetId, currentDepth + 1);
                        queue.add(targetId);
                    }
                }
            }

            // 들어오는 링크
            List<DocumentLink> incomingLinks = documentLinkRepository.findByTargetDocumentId(currentDocId);
            for (DocumentLink link : incomingLinks) {
                collectedLinks.add(link);
                UUID sourceId = link.getSourceDocument().getId();
                if (!visitedDocuments.contains(sourceId)) {
                    visitedDocuments.add(sourceId);
                    depthMap.put(sourceId, currentDepth + 1);
                    queue.add(sourceId);
                }
            }
        }

        return buildGraph(new ArrayList<>(collectedLinks));
    }

    /**
     * 링크 목록으로부터 그래프 데이터를 생성한다.
     *
     * @param links 링크 목록
     * @return 그래프 데이터
     */
    private GraphData buildGraph(List<DocumentLink> links) {
        Map<UUID, GraphNode> nodeMap = new HashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (DocumentLink link : links) {
            Document sourceDoc = link.getSourceDocument();
            Document targetDoc = link.getTargetDocument();

            if (targetDoc == null) {
                continue; // 깨진 링크는 제외
            }

            // Source 노드 추가
            nodeMap.putIfAbsent(sourceDoc.getId(), new GraphNode(
                    sourceDoc.getId(),
                    sourceDoc.getPath(),
                    sourceDoc.getTitle(),
                    sourceDoc.getDocType().name()
            ));

            // Target 노드 추가
            nodeMap.putIfAbsent(targetDoc.getId(), new GraphNode(
                    targetDoc.getId(),
                    targetDoc.getPath(),
                    targetDoc.getTitle(),
                    targetDoc.getDocType().name()
            ));

            // Edge 추가
            edges.add(new GraphEdge(
                    link.getId(),
                    sourceDoc.getId(),
                    targetDoc.getId(),
                    link.getLinkType().name(),
                    link.getAnchorText()
            ));
        }

        // 노드별 통계 계산
        Map<UUID, Integer> outgoingCounts = new HashMap<>();
        Map<UUID, Integer> incomingCounts = new HashMap<>();

        for (GraphEdge edge : edges) {
            outgoingCounts.merge(edge.source(), 1, Integer::sum);
            incomingCounts.merge(edge.target(), 1, Integer::sum);
        }

        // 노드에 통계 설정
        for (GraphNode node : nodeMap.values()) {
            node.setOutgoingLinks(outgoingCounts.getOrDefault(node.id(), 0));
            node.setIncomingLinks(incomingCounts.getOrDefault(node.id(), 0));
        }

        return new GraphData(new ArrayList<>(nodeMap.values()), edges);
    }

    /**
     * 특정 문서의 영향 분석을 수행한다.
     * 이 문서가 변경되면 영향을 받을 수 있는 문서 목록을 반환한다.
     *
     * @param documentId 문서 ID
     * @return 영향 받는 문서 목록
     */
    @Transactional(readOnly = true)
    public ImpactAnalysis analyzeImpact(UUID documentId) {
        // 이 문서를 참조하는 문서 찾기 (들어오는 링크)
        List<DocumentLink> incomingLinks = documentLinkRepository.findByTargetDocumentId(documentId);

        List<ImpactedDocument> directImpact = incomingLinks.stream()
                .map(link -> {
                    Document doc = link.getSourceDocument();
                    return new ImpactedDocument(
                            doc.getId(),
                            doc.getPath(),
                            doc.getTitle(),
                            1, // 직접 참조 (depth 1)
                            link.getLinkType().name(),
                            link.getAnchorText()
                    );
                })
                .collect(Collectors.toList());

        // 간접 영향 분석 (depth 2): 직접 참조하는 문서를 참조하는 문서들
        Set<UUID> indirectDocIds = new HashSet<>();
        for (DocumentLink link : incomingLinks) {
            UUID sourceDocId = link.getSourceDocument().getId();
            List<DocumentLink> indirectLinks = documentLinkRepository.findByTargetDocumentId(sourceDocId);
            for (DocumentLink indirectLink : indirectLinks) {
                indirectDocIds.add(indirectLink.getSourceDocument().getId());
            }
        }

        // 직접 영향 문서는 제외
        Set<UUID> directDocIds = incomingLinks.stream()
                .map(link -> link.getSourceDocument().getId())
                .collect(Collectors.toSet());
        indirectDocIds.removeAll(directDocIds);
        indirectDocIds.remove(documentId); // 자기 자신 제외

        List<ImpactedDocument> indirectImpact = indirectDocIds.stream()
                .map(docId -> documentRepository.findById(docId).orElse(null))
                .filter(Objects::nonNull)
                .map(doc -> new ImpactedDocument(
                        doc.getId(),
                        doc.getPath(),
                        doc.getTitle(),
                        2, // 간접 참조 (depth 2)
                        null,
                        null
                ))
                .collect(Collectors.toList());

        int totalImpacted = directImpact.size() + indirectImpact.size();

        return new ImpactAnalysis(
                documentId,
                totalImpacted,
                directImpact,
                indirectImpact
        );
    }

    /**
     * 그래프 데이터.
     */
    public record GraphData(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    /**
     * 그래프 노드 (문서).
     */
    public static class GraphNode {
        private final UUID id;
        private final String path;
        private final String title;
        private final String docType;
        private int outgoingLinks;
        private int incomingLinks;

        public GraphNode(UUID id, String path, String title, String docType) {
            this.id = id;
            this.path = path;
            this.title = title;
            this.docType = docType;
        }

        public UUID id() {
            return id;
        }

        public String path() {
            return path;
        }

        public String title() {
            return title;
        }

        public String docType() {
            return docType;
        }

        public int outgoingLinks() {
            return outgoingLinks;
        }

        public int incomingLinks() {
            return incomingLinks;
        }

        public void setOutgoingLinks(int count) {
            this.outgoingLinks = count;
        }

        public void setIncomingLinks(int count) {
            this.incomingLinks = count;
        }
    }

    /**
     * 그래프 엣지 (링크).
     */
    public record GraphEdge(
            UUID id,
            UUID source,
            UUID target,
            String linkType,
            String anchorText
    ) {
    }

    /**
     * 영향 분석 결과.
     */
    public record ImpactAnalysis(
            UUID documentId,
            int totalImpacted,
            List<ImpactedDocument> directImpact,
            List<ImpactedDocument> indirectImpact
    ) {
    }

    /**
     * 영향 받는 문서.
     */
    public record ImpactedDocument(
            UUID id,
            String path,
            String title,
            int depth,
            String linkType,
            String anchorText
    ) {
    }
}
