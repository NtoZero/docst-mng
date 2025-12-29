package com.docst.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RagMode enum 테스트.
 * Phase 4-A: RAG 모드 enum 검증
 */
class RagModeTest {

    @Test
    @DisplayName("RagMode enum 값 확인")
    void ragMode_hasCorrectValues() {
        // When
        RagMode[] values = RagMode.values();

        // Then
        assertEquals(3, values.length);
        assertArrayEquals(
            new RagMode[]{RagMode.PGVECTOR, RagMode.NEO4J, RagMode.HYBRID},
            values
        );
    }

    @Test
    @DisplayName("RagMode.PGVECTOR 존재 확인")
    void ragMode_pgVector_exists() {
        // When
        RagMode mode = RagMode.PGVECTOR;

        // Then
        assertNotNull(mode);
        assertEquals("PGVECTOR", mode.name());
    }

    @Test
    @DisplayName("RagMode.NEO4J 존재 확인")
    void ragMode_neo4j_exists() {
        // When
        RagMode mode = RagMode.NEO4J;

        // Then
        assertNotNull(mode);
        assertEquals("NEO4J", mode.name());
    }

    @Test
    @DisplayName("RagMode.HYBRID 존재 확인")
    void ragMode_hybrid_exists() {
        // When
        RagMode mode = RagMode.HYBRID;

        // Then
        assertNotNull(mode);
        assertEquals("HYBRID", mode.name());
    }

    @Test
    @DisplayName("valueOf로 문자열에서 변환")
    void ragMode_valueOf_convertsFromString() {
        // When & Then
        assertEquals(RagMode.PGVECTOR, RagMode.valueOf("PGVECTOR"));
        assertEquals(RagMode.NEO4J, RagMode.valueOf("NEO4J"));
        assertEquals(RagMode.HYBRID, RagMode.valueOf("HYBRID"));
    }

    @Test
    @DisplayName("잘못된 값 → IllegalArgumentException")
    void ragMode_valueOf_invalidValue_throwsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            RagMode.valueOf("INVALID");
        });
    }

    @Test
    @DisplayName("enum ordinal 순서 확인")
    void ragMode_ordinal_correctOrder() {
        // When & Then
        assertEquals(0, RagMode.PGVECTOR.ordinal());
        assertEquals(1, RagMode.NEO4J.ordinal());
        assertEquals(2, RagMode.HYBRID.ordinal());
    }

    @Test
    @DisplayName("enum toString() 기본 동작 확인")
    void ragMode_toString_returnsName() {
        // When & Then
        assertEquals("PGVECTOR", RagMode.PGVECTOR.toString());
        assertEquals("NEO4J", RagMode.NEO4J.toString());
        assertEquals("HYBRID", RagMode.HYBRID.toString());
    }
}
