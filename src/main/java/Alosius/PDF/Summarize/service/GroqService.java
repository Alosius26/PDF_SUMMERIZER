package Alosius.PDF.Summarize.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GroqService — calls Groq's OpenAI-compatible chat completions API.
 *
 * Endpoint: https://api.groq.com/openai/v1/chat/completions
 * Default model: llama-3.3-70b-versatile
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);
    private static final int DEFAULT_MAX_TOKENS = 2048;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.key:}")
    private String configuredApiKey;

    @Value("${groq.default.model:qwen/qwen3.8-27b}")
    private String defaultModel;

    private final ObjectMapper objectMapper;

    public record SummarizationResult(String paragraphSummary, List<String> bulletPoints) {}

    public GroqService() {
        this(new ObjectMapper());
    }

    public GroqService(ObjectMapper objectMapper) {
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    public SummarizationResult generateSummaries(List<String> chunks, String requestedModel, String clientApiKey) throws Exception {
        String apiKey = resolveApiKey(clientApiKey);
        String model = (requestedModel != null && !requestedModel.isBlank()) ? requestedModel.trim() : defaultModel;

        if (chunks == null || chunks.isEmpty()) {
            return new SummarizationResult("", List.of());
        }

        log.info("Summarizing {} chunk(s) with model '{}' via Groq", chunks.size(), model);

        List<String> chunkSummaries = new ArrayList<>();
        Exception lastException = null;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("  Chunk {}/{} ({} chars)", i + 1, chunks.size(), chunk.length());
            try {
                String summary = callChatCompletionsWithRetry(model, apiKey, buildSummaryPrompt(chunk), DEFAULT_MAX_TOKENS);
                if (summary != null && !summary.isBlank()) {
                    chunkSummaries.add(summary.trim());
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("  Chunk {}/{} failed — skipping. Reason: {}", i + 1, chunks.size(), e.getMessage());
            }
        }

        if (chunkSummaries.isEmpty()) {
            String reason = lastException != null ? lastException.getMessage() : "Empty response from Groq.";
            throw new RuntimeException("Could not generate summary using model '" + model + "': " + reason);
        }

        String paragraphSummary;
        if (chunkSummaries.size() == 1) {
            paragraphSummary = chunkSummaries.get(0);
        } else {
            String combined = String.join("\n\n", chunkSummaries);
            try {
                String merged = callChatCompletionsWithRetry(model, apiKey, buildMergePrompt(combined), DEFAULT_MAX_TOKENS);
                paragraphSummary = (merged != null && !merged.isBlank()) ? merged.trim() : combined;
            } catch (Exception e) {
                log.warn("Merge pass failed — using combined summaries. {}", e.getMessage());
                paragraphSummary = String.join(" ", chunkSummaries);
            }
        }

        List<String> bulletPoints;
        try {
            String bulletsRaw = callChatCompletionsWithRetry(model, apiKey, buildBulletsPrompt(paragraphSummary), DEFAULT_MAX_TOKENS);
            bulletPoints = parseBulletPoints(bulletsRaw);
            if (bulletPoints.isEmpty()) {
                bulletPoints = extractSentencesAsBullets(paragraphSummary);
            }
        } catch (Exception e) {
            log.warn("Bullet generation failed — extracting from paragraph. {}", e.getMessage());
            bulletPoints = extractSentencesAsBullets(paragraphSummary);
        }

        return new SummarizationResult(paragraphSummary, bulletPoints);
    }

    public String generateQnA(String summaryText, String requestedModel, String clientApiKey) throws Exception {
        if (summaryText == null || summaryText.isBlank()) {
            throw new IllegalArgumentException("Summary text is required to generate Q&A.");
        }
        String apiKey = resolveApiKey(clientApiKey);
        String model = (requestedModel != null && !requestedModel.isBlank()) ? requestedModel.trim() : defaultModel;
        String prompt = "Based on the following document summary, generate 5 concise Question and Answer pairs " +
                "that help a reader quickly understand the most important details.\n" +
                "Format each pair strictly as:\nQ: ...\nA: ...\n\nSummary:\n" + summaryText;
        return callChatCompletionsWithRetry(model, apiKey, prompt, DEFAULT_MAX_TOKENS);
    }

    /**
     * Answers a user question grounded in the extracted PDF text or summary context.
     */
    public String chatWithPdf(String documentContext, List<Map<String, String>> history,
                              String userQuestion, String requestedModel, String clientApiKey) throws Exception {
        if (userQuestion == null || userQuestion.isBlank()) {
            throw new IllegalArgumentException("Please enter a question.");
        }
        if (documentContext == null || documentContext.isBlank()) {
            throw new IllegalArgumentException("Please upload or open a PDF document first.");
        }

        String apiKey = resolveApiKey(clientApiKey);
        String model = (requestedModel != null && !requestedModel.isBlank()) ? requestedModel.trim() : defaultModel;
        String safeContext = documentContext.length() > 24000
                ? documentContext.substring(0, 24000) + "\n...[truncated]"
                : documentContext;

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful AI PDF assistant. Answer the user's questions accurately and concisely " +
                        "based on the provided PDF document content. If something is not mentioned in the document, say so clearly.\n\n" +
                        "PDF Document Content:\n" + safeContext
        ));

        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                Map<String, String> turn = history.get(i);
                String role = turn.getOrDefault("role", "");
                String content = turn.getOrDefault("content", "");
                if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }

        messages.add(Map.of("role", "user", "content", userQuestion.trim()));
        return callChatCompletionsWithRetry(model, apiKey, messages, DEFAULT_MAX_TOKENS);
    }

    private String buildSummaryPrompt(String text) {
        return "You are an expert document summarizer. Read the following document excerpt and write a clear, " +
               "informative paragraph summary (3-5 sentences). Be factual and concise. " +
               "Do not add opinions or introductory filler. Output only the summary paragraph.\n\n" +
               "Document excerpt:\n" + text;
    }

    private String buildMergePrompt(String combinedSummaries) {
        return "You are an expert editor. The following are paragraph summaries of different sections of one document. " +
               "Combine them into a single, coherent, well-written executive summary paragraph (4-6 sentences). " +
               "Remove repetition and output only the final summary paragraph.\n\n" +
               "Section summaries:\n" + combinedSummaries;
    }

    private String buildBulletsPrompt(String summary) {
        return "Based on the following document summary, extract 5 to 8 key takeaways as bullet points. " +
               "Each bullet point must:\n" +
               "- Start with a dash (-)\n" +
               "- Be one clear, complete sentence\n" +
               "- Capture an important fact, finding, or insight\n\n" +
               "Output only the bullet list, nothing else.\n\n" +
               "Summary:\n" + summary;
    }

    private String callChatCompletionsWithRetry(String model, String apiKey, String userMessage, int maxTokens) throws Exception {
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", userMessage));
        return callChatCompletionsWithRetry(model, apiKey, messages, maxTokens);
    }

    private String callChatCompletionsWithRetry(String model, String apiKey, List<Map<String, Object>> messages, int maxTokens) throws Exception {
        int maxAttempts = 3;
        long[] backoffsMs = {3000L, 6000L};
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return callChatCompletions(model, apiKey, messages, maxTokens);
            } catch (TransientGroqException e) {
                if (attempt == maxAttempts) {
                    throw new RuntimeException(e.getMessage(), e);
                }
                long waitMs = e.getRetryAfterMs() > 0 ? e.getRetryAfterMs() : backoffsMs[attempt - 1];
                log.warn("Transient Groq error ({}), retrying in {}ms...", e.getMessage(), waitMs);
                Thread.sleep(waitMs);
            }
        }
        throw new RuntimeException("Groq request failed after retries.");
    }

    private String callChatCompletions(String model, String apiKey, List<Map<String, Object>> messages, int maxTokens) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", 0.3);
        payload.put("stream", false);

        String jsonBody = objectMapper.writeValueAsString(payload);

        URL url = URI.create(groqApiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";

        if (status == 200) {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                throw new RuntimeException("Groq error: " + extractErrorMessage(root, body));
            }
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                String content = stripThinkBlocks(choices.get(0).path("message").path("content").asText(""));
                if (!content.isBlank()) {
                    return content;
                }
            }
            throw new RuntimeException("Empty or unexpected Groq response: " + body);
        }

        String details = extractErrorMessage(body);

        if (status == 401 || status == 403) {
            throw new IllegalArgumentException(
                "Invalid Groq API key. Please check your key at https://console.groq.com/keys. Details: " + details
            );
        }

        if (status == 404 || status == 400) {
            throw new IllegalArgumentException(
                "Groq rejected model '" + model + "' (HTTP " + status + "): " + details
            );
        }

        if (status == 429 || status == 502 || status == 503) {
            long retryAfterMs = parseRetryAfterMs(conn.getHeaderField("Retry-After"));
            throw new TransientGroqException(
                "Groq temporary error (HTTP " + status + "): " + details, retryAfterMs
            );
        }

        throw new RuntimeException("Groq API error (HTTP " + status + "): " + details);
    }

    private long parseRetryAfterMs(String header) {
        if (header == null || header.isBlank()) return 0;
        try {
            double seconds = Double.parseDouble(header.trim());
            return Math.min(15000L, Math.max(1000L, (long) (seconds * 1000L)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String stripThinkBlocks(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private String extractErrorMessage(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) return "No response body";
        try {
            return extractErrorMessage(objectMapper.readTree(rawBody), rawBody);
        } catch (Exception ignored) {
            return rawBody;
        }
    }

    private String extractErrorMessage(JsonNode root, String fallback) {
        JsonNode err = root.path("error");
        if (err.isObject() && err.has("message")) {
            return err.get("message").asText(fallback);
        }
        if (err.isTextual()) {
            return err.asText();
        }
        return fallback;
    }

    private List<String> parseBulletPoints(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> points = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^[-*•].*") || trimmed.matches("^\\d+[.)].*")) {
                String cleaned = trimmed.replaceAll("^[-*•\\d.)]+\\s*", "").trim();
                if (cleaned.length() >= 15) {
                    cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
                    if (!cleaned.endsWith(".") && !cleaned.endsWith("!") && !cleaned.endsWith("?")) {
                        cleaned += ".";
                    }
                    points.add(cleaned);
                }
            }
        }
        return points;
    }

    private List<String> extractSentencesAsBullets(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> bullets = new ArrayList<>();
        for (String s : text.split("(?<=[.?!])\\s+")) {
            String t = s.trim();
            if (t.length() >= 25 && t.split("\\s+").length >= 5) {
                bullets.add(t);
            }
        }
        return bullets;
    }

    private String resolveApiKey(String clientApiKey) {
        String key = (clientApiKey != null && !clientApiKey.isBlank()) ? clientApiKey.trim() : configuredApiKey;
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                "Groq API key is required. Configure groq.api.key in application.properties."
            );
        }
        return key;
    }

    private static class TransientGroqException extends Exception {
        private final long retryAfterMs;
        TransientGroqException(String message, long retryAfterMs) {
            super(message);
            this.retryAfterMs = retryAfterMs;
        }
        long getRetryAfterMs() { return retryAfterMs; }
    }
}
