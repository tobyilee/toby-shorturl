package com.linkly.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String referer;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(length = 100)
    private String browser;

    protected ClickEvent() {
    }

    public ClickEvent(Long linkId, Instant clickedAt, String ipHash, String userAgent,
                      String referer, String deviceType, String browser) {
        this.linkId = linkId;
        this.clickedAt = clickedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.referer = referer;
        this.deviceType = deviceType;
        this.browser = browser;
    }

    public Long getId() {
        return id;
    }

    public Long getLinkId() {
        return linkId;
    }

    public Instant getClickedAt() {
        return clickedAt;
    }

    public String getIpHash() {
        return ipHash;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getReferer() {
        return referer;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getBrowser() {
        return browser;
    }
}
