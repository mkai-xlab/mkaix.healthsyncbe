package com.g93.be.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlappingTokenTextSplitterTest {

    @Test
    void consecutiveChunksShareOverlappingContent() {
        OverlappingTokenTextSplitter splitter = OverlappingTokenTextSplitter.builder()
                .chunkSize(40)
                .chunkOverlap(10)
                .minChunkSizeChars(0)
                .minChunkLengthToEmbed(0)
                .build();

        List<Document> chunks = splitter.apply(List.of(new Document(numberedWords(300))));

        assertTrue(chunks.size() > 1, "expected the long text to be split into multiple chunks");
        Set<String> shared = sharedWords(chunks.get(0).getText(), chunks.get(1).getText());
        assertFalse(shared.isEmpty(), "expected consecutive chunks to share overlapping words");
    }

    @Test
    void zeroOverlapProducesDisjointConsecutiveChunks() {
        OverlappingTokenTextSplitter splitter = OverlappingTokenTextSplitter.builder()
                .chunkSize(40)
                .chunkOverlap(0)
                .minChunkSizeChars(0)
                .minChunkLengthToEmbed(0)
                .build();

        List<Document> chunks = splitter.apply(List.of(new Document(numberedWords(300))));

        assertTrue(chunks.size() > 1);
        Set<String> shared = sharedWords(chunks.get(0).getText(), chunks.get(1).getText());
        assertTrue(shared.isEmpty(), "expected no shared words when overlap is disabled");
    }

    @Test
    void textSmallerThanChunkSizeReturnsAsSingleChunk() {
        OverlappingTokenTextSplitter splitter = OverlappingTokenTextSplitter.builder()
                .chunkSize(700)
                .chunkOverlap(120)
                .minChunkSizeChars(250)
                .minChunkLengthToEmbed(20)
                .build();

        List<Document> chunks = splitter.apply(List.of(new Document("Knee osteoarthritis KL grade 3.")));

        assertEquals(1, chunks.size());
        assertEquals("Knee osteoarthritis KL grade 3.", chunks.get(0).getText());
    }

    @Test
    void rejectsOverlapThatIsNotSmallerThanChunkSize() {
        assertThrows(IllegalArgumentException.class, () -> OverlappingTokenTextSplitter.builder()
                .chunkSize(100)
                .chunkOverlap(100)
                .build());
    }

    private Set<String> sharedWords(String first, String second) {
        Set<String> firstWords = new HashSet<>(List.of(first.trim().split("\\s+")));
        Set<String> secondWords = new HashSet<>(List.of(second.trim().split("\\s+")));
        firstWords.retainAll(secondWords);
        return firstWords;
    }

    private String numberedWords(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append("w").append(String.format("%03d", i)).append(' ');
        }
        return text.toString();
    }
}
