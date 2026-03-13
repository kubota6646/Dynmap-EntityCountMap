package com.github.kubota6646.dynmap.entitycountmap;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;

import java.util.Collections;
import java.util.List;

/**
 * Dynmap-EntityCountMap
 *
 * <p>Adds a heat-map layer to Dynmap that colours each loaded chunk according
 * to the number of entities present.  The colour gradient runs from blue
 * (very few entities) through green → yellow → orange → red (many entities).</p>
 *
 * <p>Compatible with Minecraft 1.21.x (Spigot / Paper) and Dynmap 3.x.</p>
 */
public class EntityCountMapPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final int MAX_DYNMAP_RETRIES = 10;

    private DynmapHook dynmapHook;
    private int dynmapRetryCount = 0;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Register /entitycountmap (alias /ecm) command
        if (getCommand("entitycountmap") != null) {
            getCommand("entitycountmap").setExecutor(this);
            getCommand("entitycountmap").setTabCompleter(this);
        }

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
    // Command handling
    // -----------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("entitycountmap.reload")) {
                sender.sendMessage("§cYou don't have permission to reload Dynmap-EntityCountMap.");
                return true;
            }
            reload();
            sender.sendMessage("§aDynmap-EntityCountMap reloaded.");
            return true;
        }

        sender.sendMessage("§eUsage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }

    // -----------------------------------------------------------------------
    // Reload
    // -----------------------------------------------------------------------

    /**
     * Cleans up the current {@link DynmapHook}, reloads the config from disk,
     * and re-initialises the Dynmap integration.  Safe to call from any thread
     * that can schedule tasks on the main thread (including the main thread
     * itself).
     */
    private void reload() {
        if (dynmapHook != null) {
            dynmapHook.cleanup();
            dynmapHook = null;
        }
        reloadConfig();
        dynmapRetryCount = 0;
        // Brief delay to let Dynmap react in case it was also reloaded
        Bukkit.getScheduler().runTaskLater(this, this::initializeDynmap, 20L);
        getLogger().info("Dynmap-EntityCountMap reloading…");
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
