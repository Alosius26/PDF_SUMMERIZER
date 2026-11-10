package Alosius.PDF.Summarize.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "summary_bullet_points", indexes = {
        @Index(name = "idx_bullets_summary_order", columnList = "summary_id, sort_order")
})
public class SummaryBulletPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "summary_id", nullable = false)
    private SummaryRecord summary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "bullet_text", nullable = false, columnDefinition = "TEXT")
    private String bulletText;

    public Long getId() { return id; }
    public SummaryRecord getSummary() { return summary; }
    public void setSummary(SummaryRecord summary) { this.summary = summary; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public String getBulletText() { return bulletText; }
    public void setBulletText(String bulletText) { this.bulletText = bulletText; }
}
