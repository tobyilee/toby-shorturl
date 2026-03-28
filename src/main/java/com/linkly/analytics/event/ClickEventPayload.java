package com.linkly.analytics.event;

import java.time.Instant;

public record ClickEventPayload(
    Long linkId, Instant clickedAt, String ipAddress, String userAgent, String referer
) {}
