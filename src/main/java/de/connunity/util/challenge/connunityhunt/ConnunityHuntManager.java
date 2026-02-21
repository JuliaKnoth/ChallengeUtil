package de.connunity.util.challenge.connunityhunt;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages Connunity Hunt mode mechanics: automatic team assignment based on permissions
 * Players with "vup.creator" or "challenge.creator" → Streamer team (runners)
 * Everyone else → Viewer team (hunters)
 * Viewers get 2-minute headstart blindness/freeze, Streamers don't respawn
 */
public class ConnunityHuntManager {

    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private BukkitRunnable blindnessTask;
    private BukkitRunnable compassChargeTask;
    private BukkitRunnable compassUpdateTask;
    private long startTime;
    private static final long TWO_MINUTES = 2 * 60 * 1000; // 2 minutes in milliseconds
    private static final long FIRST_CHARGE_DELAY = 4 * 60 * 1000; // 4 minutes (2 min blindness + 2 min wait)
    private static final long COMPASS_CHARGE_TIME = 2 * 60 * 1000; // 2 minutes in milliseconds

    // Track compass charge status for each viewer
    private final Map<UUID, Long> compassLastCharged = new HashMap<>();
    private final Map<UUID, Boolean> compassCharged = new HashMap<>();

    // Track last target location to avoid unnecessary compass updates
    private final Map<UUID, Location> compassLastTarget = new HashMap<>();
    
    // Track last known streamer position for each viewer (to prevent compass spinning)
    private final Map<UUID, Location> lastKnownStreamerPosition = new HashMap<>();

    // Cache portal fallback scans to avoid heavy world block scanning every second
    private final Map<UUID, Location> cachedPortalTarget = new HashMap<>();
    private final Map<UUID, Location> portalScanOrigin = new HashMap<>();
    private final Map<UUID, Long> portalScanTime = new HashMap<>();
    private static final long PORTAL_SCAN_CACHE_MS = 10000; // 10 seconds
    private static final double PORTAL_SCAN_MOVE_THRESHOLD_SQ = 24 * 24;
    
    public static final String TEAM_STREAMER = "Streamer";
    public static final String TEAM_VIEWER = "Viewer";

    public ConnunityHuntManager(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Check if viewer movement is currently restricted (first 2 minutes)
     */
    public boolean isViewerMovementRestricted() {
        if (startTime == 0) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < TWO_MINUTES;
    }

    /**
     * Get remaining time (in seconds) that viewers are restricted
     */
    public long getViewerRestrictionTimeRemaining() {
        if (startTime == 0) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= TWO_MINUTES) {
            return 0;
        }

        return (TWO_MINUTES - elapsed) / 1000; // Convert to seconds
    }

    /**
     * Start Connunity Hunt mechanics when timer starts
     */
    public void start() {
        // Check if Connunity Hunt mode is enabled
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled == null || !connunityHuntEnabled) {
            return;
        }

        startTime = System.currentTimeMillis();

        // Assign all online players to teams based on permissions
        assignAllPlayersToTeams();
        
        // Announce team assignments
        announceTeamAssignments();
        
        // Start blindness task for viewers (first 2 minutes)
        startBlindnessTask();
        
        // Give viewers compasses
        giveViewersCompasses();
        
        // Start compass charge task (charges every 2 minutes after initial 4 minute delay)
        startCompassChargeTask();
        
        // Start compass update task (updates compass direction every second)
        startCompassUpdateTask();
    }

    /**
     * Stop all Connunity Hunt tasks
     */
    public void stop() {
        if (blindnessTask != null) {
            blindnessTask.cancel();
            blindnessTask = null;
        }

        if (compassChargeTask != null) {
            compassChargeTask.cancel();
            compassChargeTask = null;
        }

        if (compassUpdateTask != null) {
            compassUpdateTask.cancel();
            compassUpdateTask = null;
        }

        cachedPortalTarget.clear();
        portalScanOrigin.clear();
        portalScanTime.clear();

        // Reset start time
        startTime = 0;

        // Remove blindness from all viewers
        removeBlindnessFromViewers();
        
        // Remove glow from all streamers
        removeGlowFromStreamers();
        
        // Clear compass tracking data
        compassLastCharged.clear();
        compassCharged.clear();
        compassLastTarget.clear();
        lastKnownStreamerPosition.clear();
        cachedPortalTarget.clear();
        portalScanOrigin.clear();
        portalScanTime.clear();
    }
    
    /**
     * Apply blindness to all viewers for the first 2 minutes (uses real-world time)
     */
    private void startBlindnessTask() {
        blindnessTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Use real-world time instead of tick count
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed >= TWO_MINUTES) {
                    // 2 minutes have passed, remove blindness and cancel task
                    removeBlindnessFromViewers();

                    // Notify viewers
                    Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
                    for (UUID viewerId : viewers) {
                        Player viewer = Bukkit.getPlayer(viewerId);
                        if (viewer != null && viewer.isOnline()) {
                            viewer.sendMessage(lang.getComponent("connunityhunt.blindness-over"));
                        }
                    }

                    this.cancel();
                    return;
                }

                // Reapply blindness to all viewers
                Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
                for (UUID viewerId : viewers) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer != null && viewer.isOnline()) {
                        // Apply blindness effect (2 minutes duration, but we reapply every second)
                        viewer.addPotionEffect(
                                new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
                        
                        // Make viewer invulnerable to prevent mob deaths
                        viewer.setInvulnerable(true);
                    }
                }
            }
        };

        // Run every second - uses real-world milliseconds for elapsed time calculation
        blindnessTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Remove blindness from all viewers
     */
    private void removeBlindnessFromViewers() {
        Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                viewer.removePotionEffect(PotionEffectType.BLINDNESS);
                viewer.setInvulnerable(false);
            }
        }
    }

    /**
     * Assign a player to a team based on their permissions
     */
    public void assignPlayerToTeam(Player player) {
        // Check if Connunity Hunt mode is enabled
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled == null || !connunityHuntEnabled) {
            return;
        }

        // Don't reassign spectators - they chose to be spectators
        String currentTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if ("spectator".equals(currentTeam)) {
            plugin.logDebug("Skipping team assignment for spectator: " + player.getName());
            return;
        }

        String team = determineTeamForPlayer(player);
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), team);
        
        plugin.logDebug("Assigned player " + player.getName() + " to team: " + team);
    }

    /**
     * Assign all online players to teams based on permissions
     */
    public void assignAllPlayersToTeams() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            assignPlayerToTeam(player);
        }
    }

    /**
     * Determine which team a player should be on based on their permissions
     * @return TEAM_STREAMER if player has creator permissions, TEAM_VIEWER otherwise
     */
    public String determineTeamForPlayer(Player player) {
        if (player.hasPermission("vup.creator") || player.hasPermission("challenge.creator")) {
            return TEAM_STREAMER;
        } else {
            return TEAM_VIEWER;
        }
    }

    /**
     * Announce team assignments to all players
     */
    private void announceTeamAssignments() {
        Set<UUID> streamerTeam = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);
        Set<UUID> viewerTeam = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
        Set<UUID> spectatorTeam = plugin.getDataManager().getPlayersInTeam("spectator");
        
        // Build announcement message using language manager
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(lang.getComponent("start.connunityhunt-announcement-header"));
            player.sendMessage(lang.getComponent("start.connunityhunt-title"));
            player.sendMessage(Component.text(""));
            
            // Streamer team info
            Map<String, String> streamerPlaceholders = new java.util.HashMap<>();
            streamerPlaceholders.put("count", String.valueOf(streamerTeam.size()));
            streamerPlaceholders.put("plural", streamerTeam.size() != 1 ? "s" : "");
            player.sendMessage(lang.getComponent("start.connunityhunt-team-streamer", streamerPlaceholders));
            
            // Viewer team info
            Map<String, String> viewerPlaceholders = new java.util.HashMap<>();
            viewerPlaceholders.put("count", String.valueOf(viewerTeam.size()));
            viewerPlaceholders.put("plural", viewerTeam.size() != 1 ? "s" : "");
            player.sendMessage(lang.getComponent("start.connunityhunt-team-viewer", viewerPlaceholders));
            
            // Show spectators if any
            if (!spectatorTeam.isEmpty()) {
                Map<String, String> spectatorPlaceholders = new java.util.HashMap<>();
                spectatorPlaceholders.put("count", String.valueOf(spectatorTeam.size()));
                spectatorPlaceholders.put("plural", spectatorTeam.size() != 1 ? "s" : "");
                player.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                    .append(Component.text("Spectators: ", NamedTextColor.GRAY))
                    .append(Component.text(spectatorTeam.size() + " player" + (spectatorTeam.size() != 1 ? "s" : ""), NamedTextColor.YELLOW)));
            }
            
            player.sendMessage(Component.text(""));
            player.sendMessage(lang.getComponent("start.connunityhunt-announcement-footer"));
        }
    }

    /**
     * Check if all players have been assigned to teams
     * @return true if all online players have teams
     */
    public boolean allPlayersHaveTeams() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            if (team == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate team setup for Connunity Hunt mode
     * @return true if at least one player is in each team
     */
    public boolean validateTeamSetup() {
        Set<UUID> streamerTeam = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);
        Set<UUID> viewerTeam = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
        
        return !streamerTeam.isEmpty() && !viewerTeam.isEmpty();
    }

    /**
     * Get the color for team display
     */
    public TextColor getTeamColor(String teamName) {
        if (TEAM_STREAMER.equals(teamName)) {
            return TextColor.color(0x9146ff);
        } else if (TEAM_VIEWER.equals(teamName)) {
            return NamedTextColor.GRAY;
        }
        return NamedTextColor.WHITE;
    }

    /**
     * Give all viewers tracking compasses
     */
    private void giveViewersCompasses() {
        Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                giveCompass(viewer, false);
            }
        }
    }

    /**
     * Give a compass to a specific viewer (public method for when player joins viewer team)
     */
    public void giveCompassToViewer(Player viewer) {
        giveCompass(viewer, false);
    }

    /**
     * Give a compass to a viewer (with or without enchantment glint)
     */
    private void giveCompass(Player viewer, boolean charged) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        if (meta != null) {
            meta.displayName(lang.getComponent("connunityhunt.compass-name"));

            java.util.List<Component> lore = new java.util.ArrayList<>();

            if (charged) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(lang.getComponent("connunityhunt.compass-charged-status"));
                lore.add(lang.getComponent("connunityhunt.compass-track-streamers"));
            } else {
                lore.add(lang.getComponent("connunityhunt.compass-charging-status"));
                lore.add(lang.getComponent("connunityhunt.compass-ready-hint"));
            }

            meta.lore(lore);
            compass.setItemMeta(meta);
        }

        // Check if viewer already has a compass
        boolean hasCompass = false;
        for (ItemStack item : viewer.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) {
                hasCompass = true;
                break;
            }
        }

        if (!hasCompass) {
            viewer.getInventory().addItem(compass);
        }
    }

    /**
     * Update compass in viewer's inventory (add/remove enchantment glint)
     */
    private void updateCompassInInventory(Player viewer, boolean charged) {
        for (int i = 0; i < viewer.getInventory().getSize(); i++) {
            ItemStack item = viewer.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    java.util.List<Component> lore = new java.util.ArrayList<>();

                    if (charged) {
                        meta.addEnchant(Enchantment.LURE, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        lore.add(lang.getComponent("connunityhunt.compass-charged-status"));
                        lore.add(lang.getComponent("connunityhunt.compass-show-streamers"));
                    } else {
                        meta.removeEnchant(Enchantment.LURE);
                        lore.add(lang.getComponent("connunityhunt.compass-loading"));
                        lore.add(lang.getComponent("connunityhunt.compass-ready-hint"));
                    }

                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Start task that charges compasses every 2 minutes (uses real-world time)
     */
    private void startCompassChargeTask() {
        compassChargeTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
                long currentTime = System.currentTimeMillis();

                for (UUID viewerId : viewers) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer == null || !viewer.isOnline()) {
                        continue;
                    }

                    // Check if compass should be charged (using real-world milliseconds)
                    Long lastCharged = compassLastCharged.get(viewerId);
                    Boolean isCharged = compassCharged.get(viewerId);

                    if (isCharged != null && isCharged) {
                        // Already charged, skip
                        continue;
                    }

                    long timeUntilCharge;
                    if (lastCharged == null) {
                        // First charge - check if 4 minutes have passed since start
                        long elapsed = currentTime - startTime;
                        timeUntilCharge = FIRST_CHARGE_DELAY - elapsed;
                    } else {
                        // Subsequent charges - check if 2 minutes have passed since last use
                        timeUntilCharge = COMPASS_CHARGE_TIME - (currentTime - lastCharged);
                    }

                    if (timeUntilCharge <= 0) {
                        // Charge the compass
                        compassCharged.put(viewerId, true);
                        updateCompassInInventory(viewer, true);

                        // Update last charged time if this is a new charge (not initial)
                        if (lastCharged != null) {
                            compassLastCharged.put(viewerId, currentTime);
                        }

                        viewer.sendMessage(lang.getComponent("connunityhunt.compass-charged"));
                        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
                    }
                }
            }
        };

        // Run every second - uses real-world milliseconds for timing calculations
        compassChargeTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Start task that updates compass direction to point to nearest streamer
     * OPTIMIZED: Runs less frequently to reduce lag, independent of game ticks
     */
    private void startCompassUpdateTask() {
        compassUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Only update compasses while timer is running
                if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
                    return;
                }

                Set<UUID> viewers = plugin.getDataManager().getPlayersInTeam(TEAM_VIEWER);
                Set<UUID> streamers = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);

                if (viewers.isEmpty() || streamers.isEmpty()) {
                    return;
                }

                for (UUID viewerId : viewers) {
                    Player viewer = Bukkit.getPlayer(viewerId);
                    if (viewer == null || !viewer.isOnline()) {
                        continue;
                    }

                    // Find nearest streamer in same world
                    Player nearestStreamer = null;
                    double nearestDistance = Double.MAX_VALUE;

                    for (UUID streamerId : streamers) {
                        Player streamer = Bukkit.getPlayer(streamerId);
                        if (streamer == null || !streamer.isOnline()) {
                            continue;
                        }

                        // Only track streamers in same world
                        if (viewer.getWorld().equals(streamer.getWorld())) {
                            double distance = viewer.getLocation().distanceSquared(streamer.getLocation());
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestStreamer = streamer;
                            }
                        }
                    }

                    // Update compass to point to nearest streamer or last known position
                    if (nearestStreamer != null) {
                        Location targetLoc = nearestStreamer.getLocation();
                        // Update compass needle (doesn't cause flickering)
                        viewer.setCompassTarget(targetLoc);
                        // Store this as the last known position for this viewer
                        lastKnownStreamerPosition.put(viewerId, targetLoc.clone());
                    } else {
                        // No streamer in same dimension - point to last known position or portal
                        Location fallbackTarget = findFallbackCompassTarget(viewer, viewerId);
                        if (fallbackTarget != null) {
                            viewer.setCompassTarget(fallbackTarget);
                        }
                        // If no fallback found, compass keeps pointing to last set target
                    }
                }
            }
        };

        // OPTIMIZED: Run every 20 ticks (1 second) - independent of server lag
        compassUpdateTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Find a fallback target for compass when no streamers are in same dimension
     * Returns last known streamer position, or nearest portal, or null
     */
    private Location findFallbackCompassTarget(Player viewer, UUID viewerId) {
        // First, try to use last known streamer position if it exists and is in same world
        Location lastKnown = lastKnownStreamerPosition.get(viewerId);
        if (lastKnown != null && lastKnown.getWorld().equals(viewer.getWorld())) {
            return lastKnown;
        }
        
        // Otherwise, try to find a portal to point to
        Location portalTarget = getCachedNearestPortal(viewer, viewerId);
        if (portalTarget != null) {
            return portalTarget;
        }
        
        // If we have a last known position in a different dimension, still use it
        if (lastKnown != null) {
            return lastKnown;
        }
        
        // No fallback available - return null (compass keeps current target)
        return null;
    }

    private Location getCachedNearestPortal(Player viewer, UUID viewerId) {
        long now = System.currentTimeMillis();
        Location current = viewer.getLocation();
        Location scanOrigin = portalScanOrigin.get(viewerId);
        Long lastScan = portalScanTime.get(viewerId);

        if (lastScan != null && (now - lastScan) < PORTAL_SCAN_CACHE_MS && scanOrigin != null
                && scanOrigin.getWorld() != null && scanOrigin.getWorld().equals(current.getWorld())
                && scanOrigin.distanceSquared(current) <= PORTAL_SCAN_MOVE_THRESHOLD_SQ) {
            return cachedPortalTarget.get(viewerId);
        }

        Location nearest = findNearestPortal(current, current.getWorld());
        portalScanTime.put(viewerId, now);
        portalScanOrigin.put(viewerId, current.clone());

        if (nearest != null) {
            cachedPortalTarget.put(viewerId, nearest);
        } else {
            cachedPortalTarget.remove(viewerId);
        }

        return nearest;
    }

    /**
     * Find the nearest portal (Nether or End) to the viewer
     * Returns the portal location or null if none found nearby
     */
    private Location findNearestPortal(Location viewerLoc, org.bukkit.World world) {
        
        // Search radius for portals (in blocks)
        int searchRadius = 128;
        int baseX = viewerLoc.getBlockX();
        int baseY = viewerLoc.getBlockY();
        int baseZ = viewerLoc.getBlockZ();
        int minY = Math.max(world.getMinHeight(), baseY - searchRadius);
        int maxY = Math.min(world.getMaxHeight() - 1, baseY + searchRadius);
        
        int nearestX = 0;
        int nearestY = 0;
        int nearestZ = 0;
        double nearestDistanceSq = Double.MAX_VALUE;
        boolean found = false;
        
        // Search for nether portals (obsidian frame)
        for (int x = baseX - searchRadius; x <= baseX + searchRadius; x += 4) {
            for (int y = minY; y <= maxY; y += 4) {
                for (int z = baseZ - searchRadius; z <= baseZ + searchRadius; z += 4) {
                    Material blockType = world.getBlockAt(x, y, z).getType();
                    
                    // Check for nether portal or end portal frame
                    if (blockType == Material.NETHER_PORTAL || blockType == Material.END_PORTAL_FRAME || blockType == Material.END_PORTAL) {
                        double distanceSq = (x - baseX) * (double) (x - baseX)
                                + (y - baseY) * (double) (y - baseY)
                                + (z - baseZ) * (double) (z - baseZ);
                        if (distanceSq < nearestDistanceSq) {
                            nearestDistanceSq = distanceSq;
                            nearestX = x;
                            nearestY = y;
                            nearestZ = z;
                            found = true;
                        }
                    }
                }
            }
        }
        
        if (!found) {
            return null;
        }

        return new Location(world, nearestX + 0.5, nearestY, nearestZ + 0.5);
    }

    /**
     * Use compass charge (called when viewer right-clicks compass)
     * Returns true if successful, false if not charged
     */
    public boolean useCompassCharge(Player viewer) {
        Boolean charged = compassCharged.get(viewer.getUniqueId());

        if (charged == null || !charged) {
            return false;
        }

        // Discharge the compass
        compassCharged.put(viewer.getUniqueId(), false);
        compassLastCharged.put(viewer.getUniqueId(), System.currentTimeMillis());
        updateCompassInInventory(viewer, false);

        // Get all streamers
        Set<UUID> streamers = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);

        if (streamers.isEmpty()) {
            return true; // Still consume charge even if no streamers
        }

        // Check if all streamers are in different dimension
        boolean allStreamersInDifferentDimension = true;
        String streamerDimension = null; // Track which dimension streamers are in
        
        for (UUID streamerId : streamers) {
            Player streamer = Bukkit.getPlayer(streamerId);
            if (streamer != null && streamer.isOnline()) {
                if (viewer.getWorld().equals(streamer.getWorld())) {
                    allStreamersInDifferentDimension = false;
                    break;
                }
                // Determine which dimension the streamer is in
                if (streamerDimension == null) {
                    String worldName = streamer.getWorld().getName().toLowerCase();
                    if (worldName.contains("nether") || streamer.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
                        streamerDimension = "nether";
                    } else if (worldName.contains("end") || streamer.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
                        streamerDimension = "end";
                    } else {
                        streamerDimension = "overworld";
                    }
                }
            }
        }

        if (allStreamersInDifferentDimension && streamerDimension != null) {
            // Show dimension-specific effect
            if (streamerDimension.equals("end")) {
                showEndCircle(viewer);
                viewer.sendMessage(lang.getComponent("connunityhunt.streamers-in-end"));
            } else if (streamerDimension.equals("overworld")) {
                showOverworldCircle(viewer);
                viewer.sendMessage(lang.getComponent("connunityhunt.streamers-in-overworld"));
            } else {
                // Nether (default)
                showNetherCircle(viewer);
                viewer.sendMessage(lang.getComponent("connunityhunt.streamers-in-nether"));
            }
        } else {
            // Apply glow effect to streamers
            applyGlowToStreamers(viewer);
            viewer.sendMessage(lang.getComponent("connunityhunt.streamers-glowing"));
        }

        return true;
    }

    /**
     * Get remaining cooldown time in milliseconds for a viewer's compass
     * Returns 0 if charged or if no cooldown info available
     */
    public long getCompassCooldownRemaining(Player viewer) {
        Long lastCharged = compassLastCharged.get(viewer.getUniqueId());
        long currentTime = System.currentTimeMillis();

        if (lastCharged == null) {
            // First charge - calculate time until 4 minutes from start
            long elapsed = currentTime - startTime;
            long timeUntilCharge = FIRST_CHARGE_DELAY - elapsed;
            return timeUntilCharge > 0 ? timeUntilCharge : 0;
        } else {
            // Subsequent charges - calculate time since last use
            long timeUntilCharge = COMPASS_CHARGE_TIME - (currentTime - lastCharged);
            return timeUntilCharge > 0 ? timeUntilCharge : 0;
        }
    }

    /**
     * Apply glowing effect to all streamers
     */
    private void applyGlowToStreamers(Player viewer) {
        Set<UUID> streamers = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);

        for (UUID streamerId : streamers) {
            Player streamer = Bukkit.getPlayer(streamerId);
            if (streamer != null && streamer.isOnline()) {
                // Apply glowing effect for 10 seconds
                streamer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));

                // Only play sound and notify if viewer is within 25 blocks and in same world
                boolean isClose = false;
                if (viewer.getWorld().equals(streamer.getWorld())) {
                    double distanceSq = viewer.getLocation().distanceSquared(streamer.getLocation());
                    if (distanceSq <= 625.0) {
                        isClose = true;
                    }
                }

                if (isClose) {
                    // Notify streamer they're glowing
                    streamer.sendMessage(lang.getComponent("connunityhunt.you-are-glowing"));
                    streamer.playSound(streamer.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.8f);
                }
            }
        }
    }

    /**
     * Remove glow effect from all streamers
     */
    private void removeGlowFromStreamers() {
        Set<UUID> streamers = plugin.getDataManager().getPlayersInTeam(TEAM_STREAMER);
        for (UUID streamerId : streamers) {
            Player streamer = Bukkit.getPlayer(streamerId);
            if (streamer != null && streamer.isOnline()) {
                streamer.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    /**
     * Show a purple circle around the viewer (when streamers are in the Nether)
     */
    private void showNetherCircle(Player viewer) {
        Location center = viewer.getLocation().clone().add(0, 0.2, 0);

        int particleCount = 32;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            viewer.spawnParticle(Particle.PORTAL, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
            viewer.spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
        }

        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.8f);
    }

    /**
     * Show a dark purple circle around the viewer (when streamers are in the End)
     */
    private void showEndCircle(Player viewer) {
        Location center = viewer.getLocation().clone().add(0, 0.2, 0);

        int particleCount = 32;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            viewer.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
            viewer.spawnParticle(Particle.REVERSE_PORTAL, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
        }

        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.5f);
    }

    /**
     * Show a green/earthy circle around the viewer (when streamers are in the Overworld)
     */
    private void showOverworldCircle(Player viewer) {
        Location center = viewer.getLocation().clone().add(0, 0.2, 0);

        int particleCount = 32;
        double radius = 1.5;

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            viewer.spawnParticle(Particle.COMPOSTER, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
            viewer.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1, 0.1, 0.1, 0.1, 0);
        }

        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_BELL_USE, 0.5f, 1.2f);
    }
    
    /**
     * Handle a viewer rejoining during active Connunity Hunt
     * Applies blindness if rejoining during the 2-minute period
     * Ensures invulnerability is removed if rejoining after the 2-minute period
     */
    public void handleViewerRejoin(Player player) {
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Check if this is a viewer
        String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (!TEAM_VIEWER.equals(team)) {
            return;
        }
        
        if (isViewerMovementRestricted()) {
            // Still in the 2-minute blindness period - apply effects
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
            player.setInvulnerable(true);
            
            long remainingSeconds = getViewerRestrictionTimeRemaining();
            Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("seconds", String.valueOf(remainingSeconds));
            player.sendMessage(lang.getComponent("connunityhunt.blindness-remaining", placeholders));
            
            plugin.logDebug("Applied blindness to rejoining viewer " + player.getName() + 
                " (" + remainingSeconds + "s remaining)");
        } else {
            // 2-minute period has ended - ensure no invulnerability
            player.setInvulnerable(false);
            plugin.logDebug("Ensured viewer " + player.getName() + " is not invulnerable (rejoined after blindness period)");
        }
    }
}
