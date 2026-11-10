package Alosius.PDF.Summarize.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkerService {

    // Large-context LLMs via OpenRouter comfortably handle 12,000+ chars (~3,000 tokens) per chunk,
    // keeping API calls fast and well within free-tier rate limits.
    private static final int DEFAULT_CHUNK_SIZE = 12000;
    private static final int MIN_CHUNK_SIZE = 200;

    public List<String> chunkText(String text) {
        return chunkText(text, DEFAULT_CHUNK_SIZE);
    }

    public List<String> chunkText(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        String cleaned = text.trim();
        if (cleaned.length() <= maxChunkSize) {
            chunks.add(cleaned);
            return chunks;
        }

        // Split text by paragraphs first
        String[] paragraphs = cleaned.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (currentChunk.length() + para.length() + 2 <= maxChunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(para);
            } else {
                // If the single paragraph itself is larger than maxChunkSize, split by sentence
                if (para.length() > maxChunkSize) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk.setLength(0);
                    }
                    List<String> sentenceChunks = splitBySentences(para, maxChunkSize);
                    for (int i = 0; i < sentenceChunks.size(); i++) {
                        if (i == sentenceChunks.size() - 1 && sentenceChunks.get(i).length() < maxChunkSize / 2) {
                            currentChunk.append(sentenceChunks.get(i));
                        } else {
                            chunks.add(sentenceChunks.get(i).trim());
                        }
                    }
                } else {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk.setLength(0);
                    }
                    currentChunk.append(para);
                }
            }
        }

        if (currentChunk.length() > 0) {
            String remaining = currentChunk.toString().trim();
            if (remaining.length() >= MIN_CHUNK_SIZE || chunks.isEmpty()) {
                chunks.add(remaining);
            } else {
                // Append remaining small text to the last chunk if possible
                int lastIdx = chunks.size() - 1;
                chunks.set(lastIdx, chunks.get(lastIdx) + "\n\n" + remaining);
            }
        }

        return chunks;
    }

    private List<String> splitBySentences(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        // Split on sentence boundary
        String[] sentences = text.split("(?<=[.?!])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            if (current.length() + sentence.length() + 1 <= maxChunkSize) {
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(sentence);
            } else {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                if (sentence.length() > maxChunkSize) {
                    // Fallback hard split if an individual sentence is abnormally long
                    for (int i = 0; i < sentence.length(); i += maxChunkSize) {
                        chunks.add(sentence.substring(i, Math.min(i + maxChunkSize, sentence.length())).trim());
                    }
                } else {
                    current.append(sentence);
                }
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }
}
