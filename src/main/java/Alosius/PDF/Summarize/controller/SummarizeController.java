package Alosius.PDF.Summarize.controller;

import Alosius.PDF.Summarize.dto.SummaryResponse;
import Alosius.PDF.Summarize.service.GroqService;
import Alosius.PDF.Summarize.service.JwtService;
import Alosius.PDF.Summarize.service.PdfExtractorService;
import Alosius.PDF.Summarize.service.SummaryPersistenceService;
import Alosius.PDF.Summarize.service.TextChunkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SummarizeController {

    private static final Logger log = LoggerFactory.getLogger(SummarizeController.class);

    private final PdfExtractorService pdfExtractorService;
    private final TextChunkerService textChunkerService;
    private final GroqService groqService;
    private final JwtService jwtService;
    private final SummaryPersistenceService persistenceService;

    public SummarizeController(PdfExtractorService pdfExtractorService,
                               TextChunkerService textChunkerService,
                               GroqService groqService,
                               JwtService jwtService,
                               SummaryPersistenceService persistenceService) {
        this.pdfExtractorService = pdfExtractorService;
        this.textChunkerService = textChunkerService;
        this.groqService = groqService;
        this.jwtService = jwtService;
        this.persistenceService = persistenceService;
    }

    @PostMapping(value = "/summarize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> summarizePdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "apiKey", required = false) String formApiKey,
            @RequestHeader(value = "X-Groq-Token", required = false) String headerApiKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "model", required = false, defaultValue = "qwen/qwen3.8-27b") String model,
            @RequestParam(value = "summaryType", required = false, defaultValue = "paragraph") String summaryType
    ) {
        long startTime = System.currentTimeMillis();

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a valid PDF file."));
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "The uploaded file is not a PDF. Please upload a .pdf file."));
        }

        String effectiveApiKey = (formApiKey != null && !formApiKey.isBlank()) ? formApiKey : headerApiKey;

        try {
            // 1. Extract text from PDF
            log.info("Extracting text from uploaded PDF: {}", originalFilename);
            PdfExtractorService.ExtractedPdf extracted = pdfExtractorService.extractText(file);

            if (extracted.text().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "No readable text found in this PDF. It might be a scanned image or empty. Please use a text-based PDF."
                ));
            }

            // 2. Break into manageable chunks
            List<String> chunks = textChunkerService.chunkText(extracted.text());
            log.info("Extracted {} words across {} pages. Partitioned into {} chunks.",
                    extracted.wordCount(), extracted.totalPages(), chunks.size());

            // 3. Summarize using Groq
            GroqService.SummarizationResult result = groqService.generateSummaries(chunks, model, effectiveApiKey);

            long elapsed = System.currentTimeMillis() - startTime;

            // Return both paragraph and bullet points so the UI can switch formats instantly
            String paragraphSummary = result.paragraphSummary() != null ? result.paragraphSummary() : "";
            List<String> bulletPoints = result.bulletPoints() != null ? result.bulletPoints() : Collections.emptyList();

            SummaryResponse response = new SummaryResponse(
                    originalFilename,
                    extracted.totalPages(),
                    extracted.wordCount(),
                    chunks.size(),
                    paragraphSummary,
                    bulletPoints,
                    model,
                    elapsed
            );
            response.setSummaryType(summaryType);
            String rawText = extracted.text();
            response.setDocumentText(rawText.length() > 24000 ? rawText.substring(0, 24000) : rawText);

            Optional<Long> userId = jwtService.extractUserId(authHeader);
            if (userId.isPresent()) {
                response = persistenceService.saveSummary(userId.get(), response, file.getSize(), summaryType);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing PDF summarization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to summarize PDF: " + (e.getMessage() != null ? e.getMessage() : "Internal server error")
            ));
        }
    }

    @PostMapping("/qa")
    public ResponseEntity<?> generateQnA(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> payload) {
        try {
            String summary = payload.get("summary") != null ? String.valueOf(payload.get("summary")) : "";
            String model = payload.get("model") != null ? String.valueOf(payload.get("model")) : null;
            String apiKey = payload.get("apiKey") != null ? String.valueOf(payload.get("apiKey")) : null;
            String qa = groqService.generateQnA(summary, model, apiKey);

            Optional<Long> userId = jwtService.extractUserId(authHeader);
            Object sidObj = payload.get("summaryId");
            if (userId.isPresent() && sidObj != null) {
                try {
                    long summaryId = Long.parseLong(String.valueOf(sidObj));
                    persistenceService.saveQnaText(userId.get(), summaryId, qa);
                } catch (NumberFormatException ignored) {}
            }

            return ResponseEntity.ok(Map.of("qa", qa));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error generating Q&A", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to generate Q&A: " + (e.getMessage() != null ? e.getMessage() : "Internal server error")
            ));
        }
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> extractPdfForChat(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a valid PDF file."));
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf";
        if (!originalFilename.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a .pdf file."));
        }
        try {
            PdfExtractorService.ExtractedPdf extracted = pdfExtractorService.extractText(file);
            if (extracted.text().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No readable text found in this PDF."));
            }
            String text = extracted.text().length() > 24000 ? extracted.text().substring(0, 24000) : extracted.text();
            return ResponseEntity.ok(Map.of(
                    "fileName", originalFilename,
                    "totalPages", extracted.totalPages(),
                    "wordCount", extracted.wordCount(),
                    "documentText", text
            ));
        } catch (Exception e) {
            log.error("Error extracting PDF for chat", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Failed to extract PDF: " + (e.getMessage() != null ? e.getMessage() : "Internal server error")
            ));
        }
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public ResponseEntity<?> chatWithPdf(@RequestBody Map<String, Object> payload) {
        try {
            String context = payload.get("context") != null ? String.valueOf(payload.get("context")) : "";
            String message = payload.get("message") != null ? String.valueOf(payload.get("message")) : "";
            String model = payload.get("model") != null ? String.valueOf(payload.get("model")) : null;
            String apiKey = payload.get("apiKey") != null ? String.valueOf(payload.get("apiKey")) : null;
            List<Map<String, String>> history = payload.get("history") instanceof List<?> rawList
                    ? (List<Map<String, String>>) rawList
                    : Collections.emptyList();

            String reply = groqService.chatWithPdf(context, history, message, model, apiKey);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error in Chat with PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Chat failed: " + (e.getMessage() != null ? e.getMessage() : "Internal server error")
            ));
        }
    }

    @GetMapping("/summaries/history")
    public ResponseEntity<?> getHistory(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in to view history."));
        }
        return ResponseEntity.ok(persistenceService.getHistory(userId.get()));
    }

    @GetMapping("/summaries/saved")
    public ResponseEntity<?> getSaved(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in to view saved summaries."));
        }
        return ResponseEntity.ok(persistenceService.getSaved(userId.get()));
    }

    @GetMapping("/summaries/{id}")
    public ResponseEntity<?> getSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in."));
        }
        try {
            return ResponseEntity.ok(persistenceService.getOne(userId.get(), id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/summaries/{id}/save")
    public ResponseEntity<?> toggleSave(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in to save summaries."));
        }
        try {
            boolean saved = persistenceService.toggleSaved(userId.get(), id);
            return ResponseEntity.ok(Map.of("id", id, "saved", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/summaries/{id}")
    public ResponseEntity<?> deleteSummary(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long id) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in."));
        }
        try {
            persistenceService.deleteSummary(userId.get(), id);
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/summaries/history")
    public ResponseEntity<?> clearHistory(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<Long> userId = jwtService.extractUserId(authHeader);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Please sign in."));
        }
        persistenceService.clearHistory(userId.get());
        return ResponseEntity.ok(Map.of("cleared", true));
    }

    @GetMapping("/models")
    public List<Map<String, String>> getSupportedModels() {
        return List.of(
                Map.of(
                        "id", "qwen/qwen3.8-27b",
                        "name", "Qwen 3.8 27B (Default)",
                        "description", "Configured default Qwen model via Groq API."
                ),
                Map.of(
                        "id", "qwen/qwen3-32b",
                        "name", "Qwen 3 32B",
                        "description", "Alibaba Qwen 3 32B hosted on Groq."
                ),
                Map.of(
                        "id", "llama-3.3-70b-versatile",
                        "name", "Llama 3.3 70B Versatile",
                        "description", "Fast, high-accuracy 70B model on Groq."
                ),
                Map.of(
                        "id", "llama-3.1-8b-instant",
                        "name", "Llama 3.1 8B Instant",
                        "description", "Ultra-fast compact model on Groq."
                )
        );
    }
}
