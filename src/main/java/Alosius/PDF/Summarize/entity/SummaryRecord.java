package Alosius.PDF.Summarize.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "summaries", indexes = {
        @Index(name = "idx_summaries_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_summaries_user_saved", columnList = "user_id, is_saved, created_at")
})
public class SummaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "total_pages", nullable = false)
    private int totalPages;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "chunks_processed", nullable = false)
    private int chunksProcessed;

    @Column(name = "summary_format", nullable = false, length = 20)
    private String summaryFormat = "paragraph";

    @Column(name = "paragraph_summary", columnDefinition = "TEXT")
    private String paragraphSummary;

    @Column(name = "model_used", nullable = false, length = 150)
    private String modelUsed;

    @Column(name = "processing_time_ms", nullable = false)
    private long processingTimeMs;

    @Column(name = "is_saved", nullable = false)
    private boolean saved = false;

    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<SummaryBulletPoint> bulletPoints = new ArrayList<>();

    @OneToMany(mappedBy = "summary", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SummaryQna> qnaList = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void replaceBulletPoints(List<String> points) {
        this.bulletPoints.clear();
        if (points == null) return;
        for (int i = 0; i < points.size(); i++) {
            SummaryBulletPoint bp = new SummaryBulletPoint();
            bp.setSummary(this);
            bp.setSortOrder(i);
            bp.setBulletText(points.get(i));
            this.bulletPoints.add(bp);
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getWordCount() { return wordCount; }
    public void setWordCount(int wordCount) { this.wordCount = wordCount; }

    public int getChunksProcessed() { return chunksProcessed; }
    public void setChunksProcessed(int chunksProcessed) { this.chunksProcessed = chunksProcessed; }

    public String getSummaryFormat() { return summaryFormat; }
    public void setSummaryFormat(String summaryFormat) { this.summaryFormat = summaryFormat; }

    public String getParagraphSummary() { return paragraphSummary; }
    public void setParagraphSummary(String paragraphSummary) { this.paragraphSummary = paragraphSummary; }

    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }

    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public boolean isSaved() { return saved; }
    public void setSaved(boolean saved) { this.saved = saved; }

    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }

    public Instant getCreatedAt() { return createdAt; }

    public List<SummaryBulletPoint> getBulletPoints() { return bulletPoints; }
    public List<SummaryQna> getQnaList() { return qnaList; }
}
