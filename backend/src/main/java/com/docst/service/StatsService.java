package com.docst.service;

import com.docst.repository.DocumentRepository;
import com.docst.repository.ProjectRepository;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통계 서비스.
 * 대시보드 통계 정보를 제공한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsService {

    private final ProjectRepository projectRepository;
    private final RepositoryRepository repositoryRepository;
    private final DocumentRepository documentRepository;

    /**
     * 전체 프로젝트 수를 조회한다.
     *
     * @return 프로젝트 수
     */
    public long countProjects() {
        return projectRepository.count();
    }

    /**
     * 전체 레포지토리 수를 조회한다.
     *
     * @return 레포지토리 수
     */
    public long countRepositories() {
        return repositoryRepository.count();
    }

    /**
     * 전체 문서 수를 조회한다. (삭제되지 않은 것만)
     *
     * @return 문서 수
     */
    public long countDocuments() {
        return documentRepository.countByDeletedFalse();
    }
}
