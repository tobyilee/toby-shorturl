package com.linkly.link.shortcode;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Base62EncoderTest {
    @Test
    void generateReturnsStringOfRequestedLength() {
        assertThat(Base62Encoder.generate(6)).hasSize(6);
    }
    @Test
    void generateOnlyContainsBase62Characters() {
        assertThat(Base62Encoder.generate(8)).matches("[a-zA-Z0-9]+");
    }
    @Test
    void generateProducesDifferentCodes() {
        assertThat(Base62Encoder.generate(6)).isNotEqualTo(Base62Encoder.generate(6));
    }
}
