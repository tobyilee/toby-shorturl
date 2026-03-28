package com.tobyshorturl.link.shortcode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private final int length;

    public ShortCodeGenerator(@Value("${tobyshorturl.short-code-length:6}") int length) {
        this.length = length;
    }

    public String generate() {
        return Base62Encoder.generate(length);
    }
}
