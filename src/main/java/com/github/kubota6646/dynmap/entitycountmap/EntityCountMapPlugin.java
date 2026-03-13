package com.github.kubota6646.dynmap.entitycountmap;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;

/**
 * Dynmap-EntityCountMap
 *
 * <p>Adds a heat-map layer to Dynmap that colours each loaded chunk according
 * to the number of entities present.  The colour gradient runs from blue
 * (very few entities) through green → yellow → orange → red (many entities).</p>
 *
 * <p>Compatible with Minecraft 1.21.x (Spigot / Paper) and Dynmap 3.x.</p>
 */
public class EntityCountMapPlugin extends JavaPlugin {

    private static final int MAX_DYNMAP_RETRIES = 10;

    private DynmapHook dynmapHook;
    private int dynmapRetryCount = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Delay Dynmap initialisation by 1 s to ensure Dynmap has fully started.
        Bukkit.getScheduler().runTaskLater(this, this::initializeDynmap, 20L);

        getLogger().info("Dynmap-EntityCountMap enabled.");
    }

    @Override
    public void onDisable() {
        if (dynmapHook != null) {
            dynmapHook.cleanup();
            dynmapHook = null;
        }
        getLogger().info("Dynmap-EntityCountMap disabled.");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Attempts to connect to the Dynmap API.  If the Dynmap MarkerAPI is not
     * yet available (Dynmap is still starting), this method reschedules itself
     * up to {@link #MAX_DYNMAP_RETRIES} times before giving up.
     */

    private void initializeDynmap() {
        Plugin rawPlugin = Bukkit.getPluginManager().getPlugin("dynmap");

        if (rawPlugin == null) {
            getLogger().warning("Dynmap plugin not found – EntityCountMap will not function.");
            return;
        }

        if (!(rawPlugin instanceof DynmapAPI)) {
            getLogger().warning("Dynmap plugin is not compatible with DynmapAPI – EntityCountMap will not function.");
            return;
        }

        DynmapAPI dynmapAPI = (DynmapAPI) rawPlugin;

        if (dynmapAPI.getMarkerAPI() == null) {
            dynmapRetryCount++;
            if (dynmapRetryCount > MAX_DYNMAP_RETRIES) {
                getLogger().severe("Dynmap MarkerAPI never became available – giving up after "
                        + MAX_DYNMAP_RETRIES + " attempts.");
                return;
            }
            getLogger().info("Waiting for Dynmap MarkerAPI… (attempt " + dynmapRetryCount + ")");
            Bukkit.getScheduler().runTaskLater(this, this::initializeDynmap, 40L);
            return;
        }

        dynmapHook = new DynmapHook(this, dynmapAPI);
        dynmapHook.initialize();
        getLogger().info("Successfully hooked into Dynmap.");
    }
}
