package com.tobyshorturl.analytics.privacy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class IpHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec keySpec;

    public IpHasher(@Value("${tobyshorturl.ip-hash-secret}") String secret) {
        this.keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String hash(String ip) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            byte[] rawHash = mac.doFinal(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to hash IP address", e);
        }
    }
}
