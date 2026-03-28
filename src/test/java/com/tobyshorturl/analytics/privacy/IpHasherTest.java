package com.tobyshorturl.analytics.privacy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IpHasherTest {

    private final IpHasher hasher = new IpHasher("test-secret");

    @Test
    void hashIsDeterministic() {
        String hash1 = hasher.hash("192.168.1.1");
        String hash2 = hasher.hash("192.168.1.1");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void differentIpsProduceDifferentHashes() {
        String hash1 = hasher.hash("192.168.1.1");
        String hash2 = hasher.hash("10.0.0.1");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashIs64CharHex() {
        String hash = hasher.hash("192.168.1.1");
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void differentSecretsProduceDifferentHashes() {
        IpHasher otherHasher = new IpHasher("other-secret");
        String hash1 = hasher.hash("192.168.1.1");
        String hash2 = otherHasher.hash("192.168.1.1");
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
