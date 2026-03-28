package com.linkly.analytics.enrichment;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    private final UserAgentParser parser = new UserAgentParser();

    @Test
    void chromeDesktopUA() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        UserAgentParser.ParsedUA result = parser.parse(ua);
        assertThat(result.browser()).isEqualTo("Chrome");
        assertThat(result.deviceType()).isEqualTo("desktop");
    }

    @Test
    void safariMobileUA() {
        String ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        UserAgentParser.ParsedUA result = parser.parse(ua);
        assertThat(result.browser()).isEqualTo("Mobile Safari");
        assertThat(result.deviceType()).isEqualTo("mobile");
    }

    @Test
    void nullUserAgent() {
        UserAgentParser.ParsedUA result = parser.parse(null);
        assertThat(result.browser()).isEqualTo("Unknown");
        assertThat(result.deviceType()).isEqualTo("unknown");
    }
}
