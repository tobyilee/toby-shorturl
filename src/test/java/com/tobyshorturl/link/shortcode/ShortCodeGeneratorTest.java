package com.tobyshorturl.link.shortcode;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {
    @Test
    void generateReturnsBase62CodeOfConfiguredLength() {
        var generator = new ShortCodeGenerator(6);
        assertThat(generator.generate()).hasSize(6).matches("[a-zA-Z0-9]+");
    }
}
