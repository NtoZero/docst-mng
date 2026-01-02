package com.docst.llm;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 프롬프트 템플릿.
 *
 * 자주 사용하는 프롬프트 패턴을 템플릿으로 정의하고,
 * 변수 치환을 통해 사용자가 빠르게 프롬프트를 생성할 수 있도록 지원.
 */
@Getter
public class PromptTemplate {

    private final String id;
    private final String name;
    private final String description;
    private final String category;
    private final String template;
    private final List<TemplateVariable> variables;

    public PromptTemplate(String id, String name, String description, String category,
                          String template, List<TemplateVariable> variables) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.template = template;
        this.variables = variables;
    }

    /**
     * 변수 맵을 사용하여 템플릿을 프롬프트로 치환.
     *
     * @param values 변수 이름 → 값 맵
     * @return 치환된 프롬프트
     */
    public String render(Map<String, String> values) {
        String result = template;
        for (TemplateVariable var : variables) {
            String value = values.getOrDefault(var.name(), var.defaultValue() != null ? var.defaultValue() : "");
            result = result.replace("{{" + var.name() + "}}", value);
        }
        return result;
    }

    /**
     * 템플릿 변수 정의.
     *
     * @param name 변수 이름
     * @param label 사용자에게 표시될 라벨
     * @param placeholder 입력 필드 placeholder
     * @param defaultValue 기본값 (optional)
     */
    public record TemplateVariable(
        String name,
        String label,
        String placeholder,
        String defaultValue
    ) {}

    /**
     * 시스템 기본 템플릿 목록.
     */
    public static List<PromptTemplate> getSystemTemplates() {
        return List.of(
            new PromptTemplate(
                "search-docs",
                "문서 검색",
                "키워드로 관련 문서를 검색합니다",
                "search",
                "{{keyword}}에 관한 문서를 검색해서 관련된 내용을 알려줘",
                List.of(
                    new TemplateVariable("keyword", "검색 키워드", "예: authentication, API, deployment", null)
                )
            ),
            new PromptTemplate(
                "summarize-doc",
                "문서 요약",
                "특정 문서의 내용을 요약합니다",
                "summarize",
                "문서 ID {{documentId}}의 내용을 읽고 핵심 내용을 3-5줄로 요약해줘",
                List.of(
                    new TemplateVariable("documentId", "문서 ID", "문서 ID를 입력하세요", null)
                )
            ),
            new PromptTemplate(
                "create-doc",
                "문서 생성",
                "새로운 문서를 작성합니다",
                "create",
                "{{path}} 경로에 {{topic}}에 대한 {{language}} 문서를 작성해줘. 다음 섹션을 포함해야 해: {{sections}}",
                List.of(
                    new TemplateVariable("path", "파일 경로", "예: docs/guide.md", null),
                    new TemplateVariable("topic", "주제", "예: Quick Start Guide", null),
                    new TemplateVariable("language", "언어", "한국어 또는 English", "한국어"),
                    new TemplateVariable("sections", "포함할 섹션", "예: 개요, 설치, 사용법", "개요, 설치, 사용법")
                )
            ),
            new PromptTemplate(
                "update-doc",
                "문서 업데이트",
                "기존 문서를 수정합니다",
                "update",
                "문서 ID {{documentId}}를 읽고 다음 내용을 추가/수정해줘: {{changes}}",
                List.of(
                    new TemplateVariable("documentId", "문서 ID", "수정할 문서 ID", null),
                    new TemplateVariable("changes", "변경 내용", "추가하거나 수정할 내용을 설명하세요", null)
                )
            ),
            new PromptTemplate(
                "list-all-docs",
                "전체 문서 목록",
                "프로젝트의 모든 문서를 나열합니다",
                "list",
                "프로젝트의 모든 문서를 나열하고, {{docType}} 타입의 문서를 강조해줘",
                List.of(
                    new TemplateVariable("docType", "문서 타입 (선택)", "예: MD, ADOC, OPENAPI", "MD")
                )
            ),
            new PromptTemplate(
                "git-sync",
                "Git 동기화",
                "브랜치 전환 및 동기화를 수행합니다",
                "git",
                "{{branch}} 브랜치로 전환하고 최신 변경사항을 동기화해줘",
                List.of(
                    new TemplateVariable("branch", "브랜치 이름", "예: main, develop, feat/new-feature", "main")
                )
            ),
            new PromptTemplate(
                "explain-architecture",
                "아키텍처 설명",
                "프로젝트 아키텍처 문서를 찾아 설명합니다",
                "explain",
                "프로젝트의 아키텍처 또는 시스템 구조에 대한 문서를 찾아서 {{aspect}}에 대해 설명해줘",
                List.of(
                    new TemplateVariable("aspect", "설명할 측면", "예: 전체 구조, 데이터 플로우, 주요 컴포넌트", "전체 구조")
                )
            ),
            new PromptTemplate(
                "find-examples",
                "코드 예제 찾기",
                "특정 기능의 사용 예제를 찾습니다",
                "search",
                "{{feature}}를 사용하는 방법이나 예제가 있는 문서를 찾아줘",
                List.of(
                    new TemplateVariable("feature", "기능/라이브러리", "예: Spring AI, pgvector, authentication", null)
                )
            )
        );
    }
}
