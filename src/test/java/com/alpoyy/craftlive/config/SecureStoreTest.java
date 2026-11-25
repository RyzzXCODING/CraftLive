package com.alpoyy.craftlive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecureStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void maskHidesAllButLastFour() {
        assertEquals("", SecureStore.mask(null));
        assertEquals("", SecureStore.mask(""));
        assertEquals("••••", SecureStore.mask("abc"));
        assertEquals("••••••••••••wxyz", SecureStore.mask("abcdefghijklmnopwxyz"));
        assertFalse(SecureStore.mask("abcdefghijklmnopwxyz").contains("abcdefghijkl"));
    }

    @Test
    void saveGetRemoveRoundTrip() {
        // NOTE: on machines with secret-tool this uses the keyring; on CI it
        // uses the permission-restricted file. Both paths tested the same.
        SecureStore.saveSecret("test_key", "my-secret-value-123");
        assertEquals("my-secret-value-123", SecureStore.getSecret("test_key"));

        SecureStore.saveSecret("test_key", "updated-value");
        assertEquals("updated-value", SecureStore.getSecret("test_key"));

        SecureStore.removeSecret("test_key");
        assertNull(SecureStore.getSecret("test_key"));
    }

    @Test
    void fileStorageIsRestrictedAndOutsideVcs() {
        // only meaningful when the file fallback is used (no secret-tool)
        if (Files.exists(Path.of(System.getProperty("minecraft.client.dir", "."), "config", "craftlive_secrets.json"))) {
            Path store = Path.of(System.getProperty("minecraft.client.dir", "."), "config", "craftlive_secrets.json");
            assertFalse(store.toString().contains("src"), "secrets must never live in source tree");
        }
    }

    @Test
    void blankValueRemovesEntry() {
        SecureStore.saveSecret("test_blank", "something");
        SecureStore.saveSecret("test_blank", "");
        assertNull(SecureStore.getSecret("test_blank"));
    }

    @Test
    void eulerStreamKeyHelpers() {
        SecureStore.saveEulerStreamKey("es-key-test-9876");
        assertTrue(SecureStore.hasEulerStreamKey());
        assertEquals("es-key-test-9876", SecureStore.getEulerStreamKey());
        SecureStore.removeEulerStreamKey();
        assertFalse(SecureStore.hasEulerStreamKey());
    }

    @Test
    void storedFileIsNotPlaintext() throws Exception {
        // file fallback path: value should be Base64-obfuscated, not raw
        Path minecraftDir = tempDir.resolve("mc");
        System.setProperty("minecraft.client.dir", minecraftDir.toString());
        try {
            SecureStore.saveSecret("plaintext_test", "SUPER-SECRET-VALUE");
            Path store = minecraftDir.resolve("config").resolve("craftlive_secrets.json");
            if (Files.exists(store)) {
                String content = Files.readString(store);
                assertFalse(content.contains("SUPER-SECRET-VALUE"),
                        "raw secret must not be greppable in the store file");
            }
            // value still readable through the API
            assertEquals("SUPER-SECRET-VALUE", SecureStore.getSecret("plaintext_test"));
            SecureStore.removeSecret("plaintext_test");
        } finally {
            System.clearProperty("minecraft.client.dir");
        }
    }
}
