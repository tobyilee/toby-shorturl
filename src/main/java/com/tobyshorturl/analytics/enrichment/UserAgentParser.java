package com.tobyshorturl.analytics.enrichment;

import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

@Component
public class UserAgentParser {

    private final Parser parser;

    public record ParsedUA(String browser, String deviceType) {}

    public UserAgentParser() {
        this.parser = new Parser();
    }

    public ParsedUA parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new ParsedUA("Unknown", "unknown");
        }

        Client client = parser.parse(userAgent);
        String browser = client.userAgent != null && client.userAgent.family != null
                ? client.userAgent.family
                : "Unknown";
        String deviceType = inferDeviceType(userAgent);

        return new ParsedUA(browser, deviceType);
    }

    private String inferDeviceType(String userAgent) {
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        return "desktop";
    }
}
