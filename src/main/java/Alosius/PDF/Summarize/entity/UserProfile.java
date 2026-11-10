package Alosius.PDF.Summarize.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "avatar_initials", length = 8)
    private String avatarInitials;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "default_summary_format", nullable = false, length = 20)
    private String defaultSummaryFormat = "paragraph";

    @Column(name = "preferred_model", nullable = false, length = 150)
    private String preferredModel = "qwen/qwen3.8-27b";

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onTouch() {
        this.updatedAt = Instant.now();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAvatarInitials() { return avatarInitials; }
    public void setAvatarInitials(String avatarInitials) { this.avatarInitials = avatarInitials; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getDefaultSummaryFormat() { return defaultSummaryFormat; }
    public void setDefaultSummaryFormat(String defaultSummaryFormat) { this.defaultSummaryFormat = defaultSummaryFormat; }

    public String getPreferredModel() { return preferredModel; }
    public void setPreferredModel(String preferredModel) { this.preferredModel = preferredModel; }

    public Instant getUpdatedAt() { return updatedAt; }
}
