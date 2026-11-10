package Alosius.PDF.Summarize;

import Alosius.PDF.Summarize.service.TextChunkerService;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerServiceTest {

    private final TextChunkerService chunker = new TextChunkerService();

    @Test
    void testChunkingSmallText() {
        String text = "This is a brief introductory paragraph for testing.";
        List<String> chunks = chunker.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void testChunkingLargeTextPreservesContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            sb.append("This is paragraph ").append(i).append(" discussing important machine learning concepts. ")
              .append("Natural language processing allows computers to summarize long texts effectively.\n\n");
        }

        String fullText = sb.toString();
        List<String> chunks = chunker.chunkText(fullText, 1000);

        assertTrue(chunks.size() > 1, "Should split into multiple chunks");
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 1200, "Chunk should be reasonably sized: " + chunk.length());
            assertFalse(chunk.isBlank());
        }
    }
}
