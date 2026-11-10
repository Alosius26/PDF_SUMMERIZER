package Alosius.PDF.Summarize.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SummaryResponse {
    private Long id;
    private String fileName;
    private int totalPages;
    private int wordCount;
    private int chunksProcessed;
    private String paragraphSummary;
    private List<String> bulletPoints;
    private String modelUsed;
    private long processingTimeMs;
    private boolean saved;
    private String summaryType;
    private Instant createdAt;
    private List<Map<String, String>> qnaList;
    private String documentText;

    public SummaryResponse() {}

    public SummaryResponse(String fileName, int totalPages, int wordCount, int chunksProcessed,
                           String paragraphSummary, List<String> bulletPoints, String modelUsed, long processingTimeMs) {
        this.fileName = fileName;
        this.totalPages = totalPages;
        this.wordCount = wordCount;
        this.chunksProcessed = chunksProcessed;
        this.paragraphSummary = paragraphSummary;
        this.bulletPoints = bulletPoints;
        this.modelUsed = modelUsed;
        this.processingTimeMs = processingTimeMs;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getWordCount() {
        return wordCount;
    }

    public void setWordCount(int wordCount) {
        this.wordCount = wordCount;
    }

    public int getChunksProcessed() {
        return chunksProcessed;
    }

    public void setChunksProcessed(int chunksProcessed) {
        this.chunksProcessed = chunksProcessed;
    }

    public String getParagraphSummary() {
        return paragraphSummary;
    }

    public void setParagraphSummary(String paragraphSummary) {
        this.paragraphSummary = paragraphSummary;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(List<String> bulletPoints) {
        this.bulletPoints = bulletPoints;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public boolean isSaved() { return saved; }
    public void setSaved(boolean saved) { this.saved = saved; }

    public String getSummaryType() { return summaryType; }
    public void setSummaryType(String summaryType) { this.summaryType = summaryType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<Map<String, String>> getQnaList() { return qnaList; }
    public void setQnaList(List<Map<String, String>> qnaList) { this.qnaList = qnaList; }

    public String getDocumentText() { return documentText; }
    public void setDocumentText(String documentText) { this.documentText = documentText; }
}
