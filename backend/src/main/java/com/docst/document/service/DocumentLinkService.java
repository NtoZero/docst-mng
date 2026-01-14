package com.docst.document.service;

import com.docst.document.Document;
import com.docst.document.DocumentLink;
import com.docst.document.repository.DocumentLinkRepository;
import com.docst.document.repository.DocumentRepository;
import com.docst.git.LinkParser;
import com.docst.git.LinkParser.ParsedLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * 문서 링크 서비스.
 * 문서 간 링크 추출, 저장, 해결 로직을 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLinkService {

    private final DocumentLinkRepository documentLinkRepository;
    private final DocumentRepository documentRepository;
    private final LinkParser linkParser;

    /**
     * 문서에서 링크를 추출하고 저장한다.
     * 기존 링크는 삭제하고 새로 추출한 링크를 저장한다.
     *
     * @param document 문서
     * @param content  문서 내용
     */
    @Transactional
    public void extractAndSaveLinks(Document document, String content) {
        log.debug("Extracting links from document: {}", document.getPath());

        // 기존 링크 삭제
        documentLinkRepository.deleteBySourceDocumentId(document.getId());

        // 링크 추출
        List<ParsedLink> parsedLinks = linkParser.extractLinks(content);
        if (parsedLinks.isEmpty()) {
            log.debug("No links found in document: {}", document.getPath());
            return;
        }

        // 링크 저장
        for (ParsedLink parsedLink : parsedLinks) {
            DocumentLink link = new DocumentLink(
                    document,
                    parsedLink.getLinkText(),
                    parsedLink.getLinkType(),
                    parsedLink.getAnchorText(),
                    parsedLink.getLineNumber()
            );

            // 내부 링크인 경우 목적지 문서 해결 시도
            if (link.isInternal()) {
                Optional<Document> targetDoc = resolveTargetDocument(document, parsedLink.getLinkText());
                targetDoc.ifPresent(link::setTargetDocument);
            }

            documentLinkRepository.save(link);
        }

        log.info("Saved {} links for document: {}", parsedLinks.size(), document.getPath());
    }

    /**
     * 링크 텍스트를 통해 목적지 문서를 해결한다.
     *
     * @param sourceDocument 시작 문서
     * @param linkText       링크 텍스트
     * @return 목적지 문서 (존재하지 않으면 empty)
     */
    private Optional<Document> resolveTargetDocument(Document sourceDocument, String linkText) {
        // Wiki 링크: [[page]] → page.md 또는 page/index.md
        if (!linkText.contains("/") && !linkText.endsWith(".md")) {
            // 단순 페이지 이름 → 같은 디렉토리에서 찾기
            String parentPath = getParentPath(sourceDocument.getPath());
            String candidatePath1 = parentPath.isEmpty() ? linkText + ".md" : parentPath + "/" + linkText + ".md";
            String candidatePath2 = parentPath.isEmpty() ? linkText + "/index.md" : parentPath + "/" + linkText + "/index.md";

            Optional<Document> doc = documentRepository.findByRepositoryIdAndPath(
                    sourceDocument.getRepository().getId(), candidatePath1);
            if (doc.isPresent()) {
                return doc;
            }

            return documentRepository.findByRepositoryIdAndPath(
                    sourceDocument.getRepository().getId(), candidatePath2);
        }

        // 상대 경로 링크: ./docs/api.md, ../README.md
        String resolvedPath = resolveRelativePath(sourceDocument.getPath(), linkText);
        return documentRepository.findByRepositoryIdAndPath(
                sourceDocument.getRepository().getId(), resolvedPath);
    }

    /**
     * 상대 경로를 절대 경로로 변환한다.
     *
     * @param sourcePath 시작 문서 경로
     * @param linkPath   링크 경로
     * @return 해결된 절대 경로
     */
    private String resolveRelativePath(String sourcePath, String linkPath) {
        // 앵커 제거 (#section)
        if (linkPath.contains("#")) {
            linkPath = linkPath.substring(0, linkPath.indexOf("#"));
        }

        // 쿼리 파라미터 제거 (?param=value)
        if (linkPath.contains("?")) {
            linkPath = linkPath.substring(0, linkPath.indexOf("?"));
        }

        Path sourcePathObj = Paths.get(sourcePath).getParent();
        if (sourcePathObj == null) {
            sourcePathObj = Paths.get("");
        }

        Path resolvedPath = sourcePathObj.resolve(linkPath).normalize();
        return resolvedPath.toString().replace("\\", "/");
    }

    /**
     * 경로에서 부모 디렉토리를 추출한다.
     *
     * @param path 파일 경로
     * @return 부모 디렉토리 경로 (루트면 빈 문자열)
     */
    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return "";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * 특정 문서에서 나가는 링크 목록을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 나가는 링크 목록
     */
    public List<DocumentLink> getOutgoingLinks(java.util.UUID documentId) {
        return documentLinkRepository.findBySourceDocumentId(documentId);
    }

    /**
     * 특정 문서로 들어오는 링크 목록을 조회한다 (역참조).
     *
     * @param documentId 문서 ID
     * @return 들어오는 링크 목록
     */
    public List<DocumentLink> getIncomingLinks(java.util.UUID documentId) {
        return documentLinkRepository.findByTargetDocumentId(documentId);
    }

    /**
     * 레포지토리 내 깨진 링크 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 깨진 링크 목록
     */
    public List<DocumentLink> getBrokenLinks(java.util.UUID repositoryId) {
        return documentLinkRepository.findBrokenLinksByRepositoryId(repositoryId);
    }
}
