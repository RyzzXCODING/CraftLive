package com.alpoyy.craftlive.config;

import com.alpoyy.craftlive.CraftLive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Secure-enough local storage for user credentials (EulerStream API key,
 * platform tokens).
 *
 * Threat model & limitations (documented honestly):
 *  - Java cannot access the OS keyring (Windows Credential Manager /
 *    GNOME Keyring / Keychain) without shipping extra native dependencies.
 *    If `secret-tool` (libsecret) is available on Linux we use it;
 *    otherwise we fall back to a local file with owner-only permissions.
 *  - The file lives OUTSIDE git-tracked sources, in the Minecraft config
 *    directory, and is never written into releases or logs.
 *  - Values are obfuscated (Base64) to avoid casual plaintext greps — this
 *    is NOT encryption and is not claimed to be: anyone with file access
 *    can read it. This is the safest practical method without a keyring.
 *  - The key never appears in logs, crash reports, chat, or HUD text.
 */
public final class SecureStore {
    private static final String STORE_FILE = "craftlive_secrets.json";
    private static final String KEYRING_ATTRIBUTE = "craftlive";

    private SecureStore() {
    }

    private static Path storePath() {
        String minecraftDir = System.getProperty("minecraft.client.dir", ".");
        return Paths.get(minecraftDir, "config", STORE_FILE);
    }

    // ---- EulerStream API key ----

    public static void saveEulerStreamKey(String key) {
        saveSecret("eulerstream_api_key", key);
    }

    public static String getEulerStreamKey() {
        return getSecret("eulerstream_api_key");
    }

    public static void removeEulerStreamKey() {
        removeSecret("eulerstream_api_key");
    }

    public static boolean hasEulerStreamKey() {
        String v = getEulerStreamKey();
        return v != null && !v.isBlank();
    }

    /** Masked representation for UI: shows at most last 4 chars. */
    public static String mask(String secret) {
        if (secret == null || secret.isBlank()) return "";
        String s = secret.trim();
        if (s.length() <= 4) return "••••";
        return "••••••••••••" + s.substring(s.length() - 4);
    }

    // ---- generic storage ----

    public static void saveSecret(String name, String value) {
        if (value == null || value.isBlank()) {
            removeSecret(name);
            return;
        }
        // Try OS keyring first when present (Linux libsecret)
        if (tryKeyringStore(name, value)) return;

        try {
            Path path = storePath();
            Files.createDirectories(path.getParent());
            StringBuilder json = readStore(path);
            String encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
            // naive but robust JSON field replace for our own controlled file
            String entry = "\"" + name + "\": \"" + encoded + "\"";
            String content = json.toString();
            java.util.regex.Pattern p = java.util.regex.Pattern
                    .compile("\"" + name + "\"\\s*:\\s*\"[^\"]*\"");
            if (p.matcher(content).find()) {
                content = p.matcher(content).replaceAll(entry);
            } else {
                if (content.isBlank()) {
                    content = "{\n  " + entry + "\n}";
                } else {
                    content = content.replaceFirst("\\}\\s*$", ",\n  " + entry + "\n}");
                }
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            restrictPermissions(path);
        } catch (IOException e) {
            CraftLive.LOGGER.error("SecureStore: failed to persist secret (name only, value never logged): {}", name);
        }
    }

    public static String getSecret(String name) {
        if (tryKeyringAvailable()) {
            String v = keyringGet(name);
            if (v != null) return v;
        }
        try {
            Path path = storePath();
            if (!Files.exists(path)) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"")
                    .matcher(Files.readString(path, StandardCharsets.UTF_8));
            if (m.find()) {
                return new String(Base64.getDecoder().decode(m.group(1)), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            CraftLive.LOGGER.error("SecureStore: failed to read secret: {}", name);
        }
        return null;
    }

    public static void removeSecret(String name) {
        if (tryKeyringAvailable()) {
            keyringDelete(name);
        }
        try {
            Path path = storePath();
            if (!Files.exists(path)) return;
            String content = Files.readString(path, StandardCharsets.UTF_8);
            content = java.util.regex.Pattern
                    .compile("\\s*\"" + name + "\"\\s*:\\s*\"[^\"]*\",?")
                    .matcher(content).replaceAll("");
            // clean possible leading comma artifact
            content = content.replaceFirst("\\{\\s*,", "{");
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CraftLive.LOGGER.error("SecureStore: failed to remove secret: {}", name);
        }
    }

    // ---- file permissions ----

    private static void restrictPermissions(Path path) {
        try {
            Set<PosixFilePermission> perms = new HashSet<>();
            perms.add(PosixFilePermission.OWNER_READ);
            perms.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows: file inherits user profile ACLs; acceptable fallback
        }
    }

    private static StringBuilder readStore(Path path) throws IOException {
        if (!Files.exists(path)) return new StringBuilder();
        return new StringBuilder(Files.readString(path, StandardCharsets.UTF_8));
    }

    // ---- optional OS keyring (libsecret / secret-tool) ----

    private static Boolean keyringAvailable = null;

    private static boolean tryKeyringAvailable() {
        if (keyringAvailable == null) {
            keyringAvailable = probe("secret-tool");
        }
        return keyringAvailable;
    }

    private static boolean probe(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean tryKeyringStore(String name, String value) {
        if (!tryKeyringAvailable()) return false;
        try {
            Process p = new ProcessBuilder("secret-tool", "store",
                    "--label=CraftLive " + name,
                    "service", KEYRING_ATTRIBUTE, "attribute", name)
                    .start();
            p.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8));
            p.getOutputStream().close();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String keyringGet(String name) {
        try {
            Process p = new ProcessBuilder("secret-tool", "lookup",
                    "service", KEYRING_ATTRIBUTE, "attribute", name).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (p.waitFor() == 0 && out.length > 0) {
                return new String(out, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void keyringDelete(String name) {
        try {
            new ProcessBuilder("secret-tool", "clear",
                    "service", KEYRING_ATTRIBUTE, "attribute", name).start().waitFor();
        } catch (Exception ignored) {
        }
    }
}
