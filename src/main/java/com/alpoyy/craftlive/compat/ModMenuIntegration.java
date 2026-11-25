package com.alpoyy.craftlive.compat;

import com.alpoyy.craftlive.CraftLiveServices;
import com.alpoyy.craftlive.gui.CraftLiveScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration: adds a Config button for CraftLive in the Mods menu
 * that opens the full CraftLive GUI (all 9 tabs incl. Settings).
 *
 * This class is only loaded when Mod Menu is installed (the "modmenu"
 * entrypoint is invoked by Mod Menu itself), so ModMenu staying an optional
 * compile-only dependency is safe.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new CraftLiveScreen(CraftLiveServices.get());
    }
}
