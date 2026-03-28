package com.tobyshorturl.qr.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void generateReturnsNonEmptyPngBytes() {
        byte[] result = qrCodeService.generateQrCode("https://example.com", 200);

        assertNotNull(result);
        assertTrue(result.length > 0);
        // PNG magic bytes: 0x89, 0x50 ('P')
        assertEquals((byte) 0x89, result[0]);
        assertEquals((byte) 0x50, result[1]);
    }

    @Test
    void generateRespectsSizeParameter() {
        byte[] small = qrCodeService.generateQrCode("https://example.com", 100);
        byte[] large = qrCodeService.generateQrCode("https://example.com", 500);

        assertTrue(large.length > small.length,
                "Larger QR image should produce more bytes than smaller one");
    }
}
