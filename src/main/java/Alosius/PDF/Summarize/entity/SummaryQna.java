package Alosius.PDF.Summarize.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "summary_qna", indexes = {
        @Index(name = "idx_qna_summary", columnList = "summary_id")
})
public class SummaryQna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private SummaryRecord summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SummaryRecord getSummary() { return summary; }
    public void setSummary(SummaryRecord summary) { this.summary = summary; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Instant getCreatedAt() { return createdAt; }
}
