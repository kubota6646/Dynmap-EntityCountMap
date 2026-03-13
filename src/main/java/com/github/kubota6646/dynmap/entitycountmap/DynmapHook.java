package com.github.kubota6646.dynmap.entitycountmap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the Dynmap marker set that visualises entity density per chunk.
 *
 * <p>Each loaded chunk is represented by a coloured rectangle whose fill
 * colour is driven by a blue → green → yellow → red gradient:
 * <ul>
 *   <li>0 entities  – no marker shown</li>
 *   <li>low count   – blue / green</li>
 *   <li>mid count   – yellow</li>
 *   <li>≥ max count – red</li>
 * </ul>
 * The {@code max-entities} config key controls the saturation point at which
 * the colour becomes fully red.
 * </p>
 */
public class DynmapHook {

    // Dynmap marker-set identifiers
    private static final String MARKER_SET_ID    = "entitycountmap";
    private static final String MARKER_SET_LABEL = "Entity Count";

    // Chunk corner indices for AreaMarker (clockwise from NW)
    private static final int CORNERS = 4;

    private final EntityCountMapPlugin plugin;
    private final DynmapAPI dynmapAPI;

    private MarkerSet  markerSet;
    private BukkitTask updateTask;

    // -----------------------------------------------------------------------
    // Config values (loaded once in initialize())
    // -----------------------------------------------------------------------
    private int          updateInterval;   // ticks between updates
    private int          maxEntities;      // entity count → fully red
    private boolean      includePlayers;
    private boolean      includeItemDrops;
    private double       fillOpacity;
    private int          layerPriority;
    private List<String> worldWhitelist;   // empty = all worlds

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public DynmapHook(EntityCountMapPlugin plugin, DynmapAPI dynmapAPI) {
        this.plugin    = plugin;
        this.dynmapAPI = dynmapAPI;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** Reads config, creates the Dynmap marker set, and starts the scheduler. */
    public void initialize() {
        loadConfig();

        MarkerAPI markerAPI = dynmapAPI.getMarkerAPI();
        if (markerAPI == null) {
            plugin.getLogger().warning("Dynmap MarkerAPI is not available – skipping initialization.");
            return;
        }

        // Get or create the persistent marker set
        markerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
        if (markerSet == null) {
            markerSet = markerAPI.createMarkerSet(MARKER_SET_ID, MARKER_SET_LABEL, null, false);
        }

        if (markerSet == null) {
            plugin.getLogger().severe("Failed to create Dynmap marker set!");
            return;
        }

        markerSet.setLayerPriority(layerPriority);
        markerSet.setHideByDefault(false);

        // Run the first update after 2 s, then every updateInterval ticks
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateMarkers, 40L, updateInterval);

        plugin.getLogger().info(
                "Entity count layer active – updating every " + updateInterval + " ticks, "
                + "max-entities=" + maxEntities + ".");
    }

    /** Cancels the update task and removes all markers we own. */
    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (markerSet != null) {
            clearAllMarkers();
        }
    }

    // -----------------------------------------------------------------------
    // Update cycle (runs on the main server thread)
    // -----------------------------------------------------------------------

    private void updateMarkers() {
        if (markerSet == null) return;

        Map<String, Integer> chunkCounts = collectEntityCounts();
        applyMarkersToMap(chunkCounts);
    }

    /**
     * Iterates all loaded chunks in all whitelisted worlds and counts their
     * entities.  Only chunks with at least one matching entity are included.
     */
    private Map<String, Integer> collectEntityCounts() {
        Map<String, Integer> counts = new HashMap<>();

        for (World world : Bukkit.getWorlds()) {
            if (!worldWhitelist.isEmpty() && !worldWhitelist.contains(world.getName())) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                int count = countEntitiesInChunk(chunk);
                if (count > 0) {
                    counts.put(chunkKey(world.getName(), chunk.getX(), chunk.getZ()), count);
                }
            }
        }

        return counts;
    }

    /** Counts entities in {@code chunk} according to the current filter config. */
    private int countEntitiesInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!includePlayers && entity instanceof Player) continue;
            if (!includeItemDrops && entity instanceof Item) continue;
            count++;
        }
        return count;
    }

    /**
     * Creates, updates, or removes {@link AreaMarker} objects so that the
     * Dynmap layer reflects the current entity-count snapshot.
     */
    private void applyMarkersToMap(Map<String, Integer> chunkCounts) {
        Set<String> activeIds = new HashSet<>();

        for (Map.Entry<String, Integer> entry : chunkCounts.entrySet()) {
            String[] parts     = entry.getKey().split("\\|", 3);
            String   worldName = parts[0];
            int      chunkX    = Integer.parseInt(parts[1]);
            int      chunkZ    = Integer.parseInt(parts[2]);
            int      count     = entry.getValue();

            String markerId = markerId(worldName, chunkX, chunkZ);
            activeIds.add(markerId);

            int    color = calculateColor(count);
            String label = count + " entities";

            AreaMarker marker = markerSet.findAreaMarker(markerId);
            if (marker == null) {
                marker = createChunkMarker(markerId, label, worldName, chunkX, chunkZ);
            } else {
                marker.setLabel(label);
            }

            if (marker != null) {
                marker.setFillStyle(fillOpacity, color);
                marker.setLineStyle(1, 0.15, color);
                marker.setDescription(buildDescription(worldName, chunkX, chunkZ, count));
            }
        }

        // Remove stale markers (chunks that are now empty or unloaded)
        removeStaleMarkers(activeIds);
    }

    /** Creates a new four-cornered {@link AreaMarker} covering one chunk. */
    private AreaMarker createChunkMarker(
            String markerId, String label, String worldName, int chunkX, int chunkZ) {

        double[] x = new double[CORNERS];
        double[] z = new double[CORNERS];

        // Clockwise from NW corner: NW → SW → SE → NE
        x[0] = chunkX * 16.0;        z[0] = chunkZ * 16.0;
        x[1] = chunkX * 16.0;        z[1] = (chunkZ + 1) * 16.0;
        x[2] = (chunkX + 1) * 16.0;  z[2] = (chunkZ + 1) * 16.0;
        x[3] = (chunkX + 1) * 16.0;  z[3] = chunkZ * 16.0;

        return markerSet.createAreaMarker(
                markerId, label, false, worldName, x, z, false);
    }

    /** Removes any marker whose ID is not in {@code activeIds}. */
    private void removeStaleMarkers(Set<String> activeIds) {
        Collection<AreaMarker> existing = markerSet.getAreaMarkers();
        if (existing == null || existing.isEmpty()) return;

        // Copy to avoid ConcurrentModificationException
        for (AreaMarker marker : new ArrayList<>(existing)) {
            if (!activeIds.contains(marker.getMarkerID())) {
                marker.deleteMarker();
            }
        }
    }

    /** Deletes every area marker in our marker set. */
    private void clearAllMarkers() {
        Collection<AreaMarker> markers = markerSet.getAreaMarkers();
        if (markers == null || markers.isEmpty()) return;
        for (AreaMarker marker : new ArrayList<>(markers)) {
            marker.deleteMarker();
        }
    }

    // -----------------------------------------------------------------------
    // Colour calculation
    // -----------------------------------------------------------------------

    /**
     * Maps an entity count to a 24-bit RGB colour using the HSB colour wheel.
     *
     * <ul>
     *   <li>0 / low  → hue 240° (blue)</li>
     *   <li>50 %     → hue 120° (green)</li>
     *   <li>100 %+   → hue   0° (red)</li>
     * </ul>
     *
     * @param entityCount number of entities in the chunk
     * @return 24-bit RGB colour integer (e.g. {@code 0xFF0000} for red)
     */
    private int calculateColor(int entityCount) {
        float ratio = Math.min(1.0f, entityCount / (float) maxEntities);
        // Sweep from blue (240°) down to red (0°) as ratio goes 0 → 1
        float hue   = (1.0f - ratio) * 240.0f;
        Color color = Color.getHSBColor(hue / 360.0f, 1.0f, 1.0f);
        return color.getRGB() & 0x00FFFFFF;
    }

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private void loadConfig() {
        updateInterval   = plugin.getConfig().getInt("update-interval", 200);
        maxEntities      = plugin.getConfig().getInt("max-entities",     50);
        includePlayers   = plugin.getConfig().getBoolean("include-players",    true);
        includeItemDrops = plugin.getConfig().getBoolean("include-item-drops", false);
        fillOpacity      = plugin.getConfig().getDouble("fill-opacity",  0.45);
        layerPriority    = plugin.getConfig().getInt("layer-priority",   10);
        worldWhitelist   = plugin.getConfig().getStringList("worlds");
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /** Stable key for a chunk, safe to use as a Map key. */
    private static String chunkKey(String world, int cx, int cz) {
        return world + "|" + cx + "|" + cz;
    }

    /** Stable, Dynmap-safe marker ID for a chunk. */
    private static String markerId(String world, int cx, int cz) {
        // Replace any characters that might be unsafe in marker IDs
        String safeWorld = world.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return "ecm_" + safeWorld + "_" + cx + "_" + cz;
    }

    /** HTML description shown in the Dynmap pop-up when clicking a chunk. */
    private static String buildDescription(String world, int cx, int cz, int count) {
        return "<b>World:</b> " + world
                + "<br><b>Chunk:</b> (" + cx + ", " + cz + ")"
                + "<br><b>Entities:</b> " + count;
    }
}
