package com.docst.llm;

import com.docst.llm.model.Citation;
import com.docst.llm.model.StreamEvent;
import com.docst.llm.tools.DocumentTools;
import com.docst.llm.tools.GitTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * LLM 서비스
 *
 * DynamicChatClientFactory를 사용하여 프로젝트별 크리덴셜 기반 LLM 대화 처리.
 * @Tool annotation 기반 Tool Calling으로 문서 관리, 검색, Git 작업 자동 수행.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LlmService {

    private final DynamicChatClientFactory chatClientFactory;
    private final DocumentTools documentTools;
    private final GitTools gitTools;
    private final CitationCollector citationCollector;

    /**
     * LLM과 대화 (동기 호출)
     *
     * Spring AI ChatClient가 Tool Calling 루프를 자동 처리.
     * LLM이 필요한 경우 등록된 Tools를 자동으로 호출하고 결과를 사용하여 응답 생성.
     */
    public String chat(String userMessage, UUID projectId, String sessionId) {
        log.info("LLM chat request: projectId={}, sessionId={}, message length={}",
            projectId, sessionId, userMessage.length());

        try {
            // 프로젝트별 ChatClient 가져오기 (크리덴셜 기반)
            ChatClient chatClient = chatClientFactory.getChatClient(projectId);

            // User Message에 projectId 컨텍스트 추가
            String contextualizedMessage = String.format(
                "[Context: projectId=%s]\n\nUser Question: %s\n\n" +
                "IMPORTANT: When using searchDocuments, listDocuments, or getDocument tools, " +
                "you MUST use projectId=\"%s\"",
                projectId, userMessage, projectId
            );

            return chatClient.prompt()
                .user(contextualizedMessage)
                // @Tool annotation 기반 Tools 등록
                .tools(documentTools, gitTools)
                // ToolContext로 projectId 전달
                .advisors(spec -> spec
                    .param("projectId", projectId.toString())
                    .param("sessionId", sessionId)
                )
                .call()
                .content();
        } catch (Exception e) {
            log.error("Error during LLM chat", e);
            return "Sorry, an error occurred while processing your request: " + e.getMessage();
        }
    }

    /**
     * LLM과 대화 (스트리밍)
     *
     * 텍스트 청크 단위로 실시간 응답 스트리밍.
     * SSE (Server-Sent Events)를 통해 클라이언트로 전송.
     */
    public Flux<String> streamChat(String userMessage, UUID projectId, String sessionId) {
        log.info("LLM stream chat request: projectId={}, sessionId={}, message length={}",
            projectId, sessionId, userMessage.length());

        try {
            // 프로젝트별 ChatClient 가져오기 (크리덴셜 기반)
            ChatClient chatClient = chatClientFactory.getChatClient(projectId);

            // User Message에 projectId 컨텍스트 추가
            String contextualizedMessage = String.format(
                "[Context: projectId=%s]\n\nUser Question: %s\n\n" +
                "IMPORTANT: When using searchDocuments, listDocuments, or getDocument tools, " +
                "you MUST use projectId=\"%s\"",
                projectId, userMessage, projectId
            );

            return chatClient.prompt()
                .user(contextualizedMessage)
                .tools(documentTools, gitTools)
                .advisors(spec -> spec
                    .param("projectId", projectId.toString())
                    .param("sessionId", sessionId)
                )
                .stream()
                .content()
                .onErrorResume(e -> {
                    log.error("Error during LLM stream chat", e);
                    return Flux.just("Error: " + e.getMessage());
                });
        } catch (Exception e) {
            log.error("Error creating ChatClient", e);
            return Flux.just("Error: " + e.getMessage());
        }
    }

    /**
     * LLM과 대화 (스트리밍 + Citation)
     *
     * 텍스트 청크와 함께 RAG Citation 정보를 제공.
     * 스트리밍 완료 후 마지막에 CitationsEvent를 전송.
     */
    public Flux<StreamEvent> streamChatWithCitations(String userMessage, UUID projectId, String sessionId) {
        log.info("LLM stream chat with citations: projectId={}, sessionId={}, message length={}",
            projectId, sessionId, userMessage.length());

        // 시작 전 Citation 컬렉터 초기화 (sessionId 기반)
        citationCollector.clear(sessionId);

        try {
            // 프로젝트별 ChatClient 가져오기 (크리덴셜 기반)
            ChatClient chatClient = chatClientFactory.getChatClient(projectId);

            // User Message에 projectId, sessionId 컨텍스트 추가
            // Tool 호출 시 sessionId를 전달하도록 지시
            String contextualizedMessage = String.format(
                "[Context: projectId=%s, sessionId=%s]\n\nUser Question: %s\n\n" +
                "IMPORTANT: When using tools (searchDocuments, listDocuments, getDocument, etc.), " +
                "you MUST ALWAYS include these parameters:\n" +
                "- projectId=\"%s\"\n" +
                "- sessionId=\"%s\"",
                projectId, sessionId, userMessage, projectId, sessionId
            );

            // Content 이벤트 스트림
            Flux<StreamEvent> contentFlux = chatClient.prompt()
                .user(contextualizedMessage)
                .tools(documentTools, gitTools)
                .advisors(spec -> spec
                    .param("projectId", projectId.toString())
                    .param("sessionId", sessionId)
                )
                .stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isEmpty())
                .<StreamEvent>map(StreamEvent.ContentEvent::new)
                .onErrorResume(e -> {
                    log.error("Error during LLM stream chat", e);
                    return Flux.just(new StreamEvent.ContentEvent("Error: " + e.getMessage()));
                });

            // 스트리밍 완료 후 Citation 이벤트 추가 (sessionId 기반으로 조회)
            return contentFlux.concatWith(Mono.fromSupplier(() -> {
                List<Citation> citations = citationCollector.getAndClear(sessionId);
                log.info("Sending {} citations at end of stream for session {}", citations.size(), sessionId);
                return new StreamEvent.CitationsEvent(citations);
            }));

        } catch (Exception e) {
            log.error("Error creating ChatClient", e);
            citationCollector.clear(sessionId);
            return Flux.just(new StreamEvent.ContentEvent("Error: " + e.getMessage()));
        }
    }

    /**
     * 스트리밍 + ChatResponse (메타데이터 포함)
     *
     * 응답과 함께 사용된 토큰 수, 모델 정보 등 메타데이터 제공.
     */
    public Flux<ChatResponse> streamChatWithMetadata(
        String userMessage,
        UUID projectId,
        String sessionId
    ) {
        log.info("LLM stream chat (with metadata) request: projectId={}, sessionId={}",
            projectId, sessionId);

        try {
            // 프로젝트별 ChatClient 가져오기 (크리덴셜 기반)
            ChatClient chatClient = chatClientFactory.getChatClient(projectId);

            return chatClient.prompt()
                .user(userMessage)
                .tools(documentTools, gitTools)
                .advisors(spec -> spec
                    .param("projectId", projectId.toString())
                    .param("sessionId", sessionId)
                )
                .stream()
                .chatResponse()
                .onErrorResume(e -> {
                    log.error("Error during LLM stream chat with metadata", e);
                    return Flux.empty();
                });
        } catch (Exception e) {
            log.error("Error creating ChatClient", e);
            return Flux.empty();
        }
    }
}
