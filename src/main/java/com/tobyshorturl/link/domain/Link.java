package com.tobyshorturl.link.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "links", uniqueConstraints = {
        @UniqueConstraint(name = "uk_links_short_code", columnNames = "short_code")
})
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(length = 500)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Link() {
    }

    public Link(String shortCode, String originalUrl, String title, Long userId, Instant expiresAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.title = title;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isAccessible() {
        return active && !isDeleted() && !isExpired();
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public void incrementClickCount() {
        this.clickCount++;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getTitle() {
        return title;
    }

    public Long getUserId() {
        return userId;
    }

    public long getClickCount() {
        return clickCount;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
