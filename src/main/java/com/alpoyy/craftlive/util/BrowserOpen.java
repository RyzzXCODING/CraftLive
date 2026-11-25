package com.alpoyy.craftlive.util;

import com.alpoyy.craftlive.CraftLive;

import java.awt.Desktop;
import java.net.URI;

/**
 * Opens a URL in the user's default browser (permitted platform handoff,
 * e.g. "play this on YouTube Music"). Never used for anything else.
 */
public final class BrowserOpen {
    private BrowserOpen() {
    }

    public static void open(URI uri) {
        if (uri == null) return;
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(uri);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        // Fallback: common Linux openers
        for (String cmd : new String[]{"xdg-open", "gio open"}) {
            try {
                String[] parts = cmd.split(" ");
                String[] full = new String[parts.length + 1];
                System.arraycopy(parts, 0, full, 0, parts.length);
                full[parts.length] = uri.toString();
                if (new ProcessBuilder(full).start().waitFor() == 0) return;
            } catch (Throwable ignored) {
            }
        }
        CraftLive.LOGGER.info("Could not open browser automatically for link: {}", uri);
    }
}
