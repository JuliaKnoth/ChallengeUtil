package de.connunity.util.challenge.endfight;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the custom end fight mechanics where players chase the dragon egg holder
 */
public class CustomEndFightManager {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    private boolean active = false;
    private boolean eggCollected = false;
    private Player eggHolder = null;
    private String eggHolderTeam = null;
    private Player lastDamager = null; // Track who last damaged the egg holder
    private BossBar eggHolderBossBar = null;
    private BukkitTask healthUpdateTask = null;
    private BukkitTask portalRemovalTask = null;
    private BukkitTask teamTimerTask = null;
    private final Map<UUID, Boolean> immortalPlayers = new HashMap<>();
    private Location endSpawnLocation = null;
    
    // Team-based timer system
    private final Map<String, Integer> teamHoldTimes = new HashMap<>(); // Team name -> seconds held
    private static final int WIN_TIME_SECONDS = 600; // 10 minutes = 600 seconds
    private int currentHoldTime = 0; // Current continuous hold time in seconds
    
    public CustomEndFightManager(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Activate the custom end fight mode
     */
    public void activate() {
        this.active = true;
        this.eggCollected = false;
        
        // Remove end portals and gateways to prevent escape
        removeEndPortalsAndGateways();
        
        // Start continuous portal removal task
        startPortalRemovalTask();
    }
    
    /**
     * Deactivate the custom end fight mode
     */
    public void deactivate() {
        this.active = false;
        this.eggCollected = false;
        
        // Clean up egg holder
        if (eggHolder != null && eggHolder.isOnline()) {
            try {
                // Reset max health back to normal
                eggHolder.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
                if (eggHolder.getHealth() > 20.0) {
                    eggHolder.setHealth(20.0);
                }
                // Remove all potion effects
                for (PotionEffect effect : eggHolder.getActivePotionEffects()) {
                    eggHolder.removePotionEffect(effect.getType());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error cleaning up egg holder: " + e.getMessage());
            }
        }
        this.eggHolder = null;
        
        // Remove boss bar from all players
        if (eggHolderBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    player.hideBossBar(eggHolderBossBar);
                } catch (Exception e) {
                    // Ignore errors during cleanup
                }
            }
            eggHolderBossBar = null;
        }
        
        if (healthUpdateTask != null) {
            healthUpdateTask.cancel();
            healthUpdateTask = null;
        }
        
        if (portalRemovalTask != null) {
            portalRemovalTask.cancel();
            portalRemovalTask = null;
        }
        
        if (teamTimerTask != null) {
            teamTimerTask.cancel();
            teamTimerTask = null;
        }
        
        // Clean up all immortal players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (immortalPlayers.containsKey(player.getUniqueId())) {
                removeImmortal(player);
            }
        }
        immortalPlayers.clear();
        
        // Reset team timer data
        teamHoldTimes.clear();
        currentHoldTime = 0;
        eggHolderTeam = null;
        lastDamager = null;
        
        endSpawnLocation = null;
    }
    
    /**
     * Remove all END_PORTAL and END_GATEWAY blocks in the End dimension
     * Note: This only removes the portal/gateway blocks themselves, not the surrounding structures
     * (like END_PORTAL_FRAME blocks or gateway platform blocks)
     */
    private void removeEndPortalsAndGateways() {
        // Find all End worlds
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            
            final World end = world;
            plugin.getLogger().info("Found End world for portal removal: " + end.getName());
            
            // Run with a delay to ensure chunks are loaded and portal blocks have been created
            new BukkitRunnable() {
                @Override
                public void run() {
                    int portalCount = 0;
                    int gatewayCount = 0;
                    
                    // Search area around spawn (0,0) where the main island and exit portal are
                    // Focus on the exit portal area first (0, 64, 0)
                    Location exitPortalCenter = new Location(end, 0, 64, 0);
                    
                    // Load the chunks around the portal area first
                    for (int cx = -1; cx <= 1; cx++) {
                        for (int cz = -1; cz <= 1; cz++) {
                            end.loadChunk(cx, cz);
                        }
                    }
                    
                    // Remove only END_PORTAL and END_GATEWAY blocks (not frames or other structures)
                    for (int x = -10; x <= 10; x++) {
                        for (int z = -10; z <= 10; z++) {
                            for (int y = 50; y <= 80; y++) {
                                Block block = end.getBlockAt(exitPortalCenter.getBlockX() + x, y, exitPortalCenter.getBlockZ() + z);
                                Material type = block.getType();
                                
                                if (type == Material.END_PORTAL) {
                                    dropAttachedBlocks(block);
                                    block.setType(Material.AIR);
                                    portalCount++;
                                } else if (type == Material.END_GATEWAY) {
                                    dropAttachedBlocks(block);
                                    block.setType(Material.AIR);
                                    gatewayCount++;
                                }
                            }
                        }
                    }
                    
                    // Then search the broader area for any other END_PORTAL/END_GATEWAY blocks (only loaded chunks)
                    int radius = 200;
                    for (int x = -radius; x <= radius; x += 16) {
                        for (int z = -radius; z <= radius; z += 16) {
                            // Only process loaded chunks
                            if (!end.isChunkLoaded(x >> 4, z >> 4)) {
                                continue;
                            }
                            
                            // Scan the chunk
                            for (int dx = 0; dx < 16; dx++) {
                                for (int dz = 0; dz < 16; dz++) {
                                    for (int y = 0; y <= 256; y++) {
                                        Block block = end.getBlockAt(x + dx, y, z + dz);
                                        Material type = block.getType();
                                        
                                        if (type == Material.END_PORTAL) {
                                            dropAttachedBlocks(block);
                                            block.setType(Material.AIR);
                                            portalCount++;
                                        } else if (type == Material.END_GATEWAY) {
                                            dropAttachedBlocks(block);
                                            block.setType(Material.AIR);
                                            gatewayCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    plugin.getLogger().info("Removed " + portalCount + " END_PORTAL blocks and " + 
                                           gatewayCount + " END_GATEWAY blocks from the End dimension (" + end.getName() + ")");
                }
            }.runTaskLater(plugin, 5L);
        }
    }
    
    /**
     * Start a continuous task to remove END_PORTAL blocks every tick
     * This ensures portals NEVER exist while the end fight is active
     */
    private void startPortalRemovalTask() {
        // Find all End worlds
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            
            final World end = world;
            plugin.getLogger().info("Starting continuous portal removal task for End world: " + end.getName());
            
            portalRemovalTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!active) {
                        this.cancel();
                        return;
                    }
                    
                    // Scan the exit portal area continuously
                    for (int x = -15; x <= 15; x++) {
                        for (int z = -15; z <= 15; z++) {
                            for (int y = 50; y <= 85; y++) {
                                Block block = end.getBlockAt(x, y, z);
                                Material type = block.getType();
                                if (type == Material.END_PORTAL || type == Material.END_GATEWAY) {
                                    dropAttachedBlocks(block);
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 5L); // Run every 5 ticks (4 times per second)
        }
    }
    
    /**
     * Drop items from blocks attached to the given block (like torches, signs, etc.)
     * This prevents items from being destroyed when the supporting block is removed
     */
    private void dropAttachedBlocks(Block block) {
        if (block == null || block.getWorld() == null) {
            return;
        }
        
        // Check all 6 faces for attached blocks
        Block[] neighbors = new Block[] {
            block.getRelative(BlockFace.UP),
            block.getRelative(BlockFace.DOWN),
            block.getRelative(BlockFace.NORTH),
            block.getRelative(BlockFace.SOUTH),
            block.getRelative(BlockFace.EAST),
            block.getRelative(BlockFace.WEST)
        };
        
        for (Block neighbor : neighbors) {
            if (neighbor == null) continue;
            
            Material mat = neighbor.getType();
            // Check if this is an attachable block that would break
            if (isAttachableBlock(mat)) {
                // Drop as item and remove
                neighbor.breakNaturally();
            }
        }
    }
    
    /**
     * Check if a material is an attachable block that breaks when its support is removed
     */
    private boolean isAttachableBlock(Material mat) {
        return mat == Material.TORCH ||
               mat == Material.WALL_TORCH ||
               mat == Material.SOUL_TORCH ||
               mat == Material.SOUL_WALL_TORCH ||
               mat == Material.REDSTONE_TORCH ||
               mat == Material.REDSTONE_WALL_TORCH ||
               mat == Material.LEVER ||
               mat == Material.STONE_BUTTON ||
               mat == Material.OAK_BUTTON ||
               mat == Material.SPRUCE_BUTTON ||
               mat == Material.BIRCH_BUTTON ||
               mat == Material.JUNGLE_BUTTON ||
               mat == Material.ACACIA_BUTTON ||
               mat == Material.DARK_OAK_BUTTON ||
               mat == Material.CRIMSON_BUTTON ||
               mat == Material.WARPED_BUTTON ||
               mat == Material.LADDER ||
               mat == Material.RAIL ||
               mat == Material.POWERED_RAIL ||
               mat == Material.DETECTOR_RAIL ||
               mat == Material.ACTIVATOR_RAIL ||
               mat == Material.TRIPWIRE ||
               mat == Material.TRIPWIRE_HOOK ||
               mat.name().contains("SIGN") ||
               mat.name().contains("BANNER") ||
               mat.name().contains("CARPET");
    }
    
    /**
     * Check if custom end fight is active
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Check if the egg has been collected
     */
    public boolean isEggCollected() {
        return eggCollected;
    }
    
    /**
     * Get the current egg holder
     */
    public Player getEggHolder() {
        return eggHolder;
    }
    
    /**
     * Set the last player who damaged the egg holder
     */
    public void setLastDamager(Player damager) {
        this.lastDamager = damager;
    }
    
    /**
     * Get the last player who damaged the egg holder
     */
    public Player getLastDamager() {
        return lastDamager;
    }
    
    /**
     * Handle when a player collects the dragon egg
     */
    public void onEggCollected(Player collector) {
        if (!active || eggCollected) {
            return;
        }
        
        this.eggCollected = true;
        this.eggHolder = collector;
        
        // Remove end portals immediately when egg is collected
        removeEndPortalsAndGateways();
        
        // Make all players immortal temporarily
        for (Player player : Bukkit.getOnlinePlayers()) {
            makeImmortal(player);
        }
        
        // Set spawn in the End
        setEndSpawn(collector.getLocation());
        
        // Start the epic animation sequence
        startEpicAnimation(collector);
    }
    
    /**
     * Make a player temporarily immortal
     */
    private void makeImmortal(Player player) {
        immortalPlayers.put(player.getUniqueId(), true);
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.DAMAGE_RESISTANCE,
            Integer.MAX_VALUE,
            255,
            false,
            false
        ));
    }
    
    /**
     * Remove immortality from a player
     */
    private void removeImmortal(Player player) {
        immortalPlayers.remove(player.getUniqueId());
        // Remove all active potion effects to ensure player can move
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }
    
    /**
     * Check if a player is currently immortal
     */
    public boolean isImmortal(UUID playerId) {
        return immortalPlayers.containsKey(playerId);
    }
    
    /**
     * Set the spawn location in the End
     */
    private void setEndSpawn(Location location) {
        World end = location.getWorld();
        if (end != null && end.getEnvironment() == World.Environment.THE_END) {
            this.endSpawnLocation = location.clone();
        }
    }
    
    /**
     * Get the End spawn location
     */
    public Location getEndSpawnLocation() {
        return endSpawnLocation;
    }
    
    /**
     * Start the epic animation sequence for the egg collector
     * Uses potion effects instead of teleportation to avoid flying kick
     */
    private void startEpicAnimation(Player collector) {
        try {
            // Phase 1: Levitation (rise up) - 2.5 seconds
            collector.addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION,
                50, // 2.5 seconds
                3, // Amplifier for moderate rise
                false,
                false
            ));
            
            // Add resistance for the entire animation duration to prevent death
            collector.addPotionEffect(new PotionEffect(
                PotionEffectType.DAMAGE_RESISTANCE,
                100, // 5 seconds (full animation)
                255, // Maximum protection
                false,
                false
            ));
            
            // Play initial sound
            collector.getWorld().playSound(collector.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
            
            BukkitTask task = new BukkitRunnable() {
                int ticks = 0;
                final int totalTicks = 100; // 5 seconds animation
                
                @Override
                public void run() {
                    try {
                        if (!collector.isOnline()) {
                            this.cancel();
                            return;
                        }
                        
                        // Spawn epic particles around the player throughout animation
                        try {
                            // Spawn particles at player's center (chest level) for better visibility
                            Location particleLoc = collector.getLocation().clone().add(0, 1.0, 0);
                            spawnEpicParticles(particleLoc);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error spawning particles: " + e.getMessage());
                        }
                        
                        // Play sound periodically
                        if (ticks % 10 == 0) {
                            try {
                                collector.getWorld().playSound(collector.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f + (ticks / 100.0f));
                            } catch (Exception e) {
                                plugin.getLogger().warning("Error playing sound: " + e.getMessage());
                            }
                        }
                        
                        // Phase 2: Apply slow falling at halfway point (2.5 seconds)
                        if (ticks == 50) {
                            // Remove levitation and add slow falling
                            collector.removePotionEffect(PotionEffectType.LEVITATION);
                            collector.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOW_FALLING,
                                50, // 2.5 seconds
                                0,
                                false,
                                false
                            ));
                        }
                        
                        ticks++;
                        
                        if (ticks >= totalTicks) {
                            // Animation complete, finalize setup
                            try {
                                // Remove any remaining effects
                                collector.removePotionEffect(PotionEffectType.LEVITATION);
                                collector.removePotionEffect(PotionEffectType.SLOW_FALLING);
                                collector.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                                finalizeEggHolderSetup(collector);
                            } catch (Exception e) {
                                plugin.getLogger().severe("Error in finalizeEggHolderSetup: " + e.getMessage());
                                e.printStackTrace();
                            }
                            this.cancel();
                            return;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error in animation runnable: " + e.getMessage());
                        e.printStackTrace();
                        this.cancel();
                        // Try to finalize anyway to not leave player stuck
                        try {
                            collector.removePotionEffect(PotionEffectType.LEVITATION);
                            collector.removePotionEffect(PotionEffectType.SLOW_FALLING);
                            collector.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
                            finalizeEggHolderSetup(collector);
                        } catch (Exception ex) {
                            plugin.getLogger().severe("Error in emergency finalize: " + ex.getMessage());
                        }
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);
            
            if (task == null) {
                // Skip animation and go straight to finalization
                finalizeEggHolderSetup(collector);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Exception when starting animation: " + e.getMessage());
            e.printStackTrace();
            // Skip animation and go straight to finalization
            try {
                finalizeEggHolderSetup(collector);
            } catch (Exception ex) {
                plugin.getLogger().severe("Error in emergency finalize after animation failure: " + ex.getMessage());
            }
        }
    }
    
    /**
     * Spawn epic particle effects around a location
     */
    private void spawnEpicParticles(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        try {
            // End-themed and experience particles - highly visible
            world.spawnParticle(Particle.DRAGON_BREATH, location, 80, 0.8, 0.8, 0.8, 0.05);
            world.spawnParticle(Particle.END_ROD, location, 50, 0.6, 0.6, 0.6, 0.1);
            world.spawnParticle(Particle.PORTAL, location, 100, 0.7, 0.7, 0.7, 1.0);
            world.spawnParticle(Particle.VILLAGER_HAPPY, location, 40, 0.6, 0.6, 0.6, 0.3);
            world.spawnParticle(Particle.FIREWORKS_SPARK, location, 30, 0.5, 0.5, 0.5, 0.15);
        } catch (Exception e) {
            // Silently fail if particles don't work
        }
    }
    
    /**
     * Finalize the setup for the egg holder (set health, boss bar, teams, etc.)
     */
    private void finalizeEggHolderSetup(Player eggHolder) {
        // Set the egg holder's team (important for first pickup!)
        this.eggHolderTeam = plugin.getDataManager().getPlayerTeam(eggHolder.getUniqueId());
        
        try {
            // Set egg holder's max health to 250
            eggHolder.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(200.0);
            eggHolder.setHealth(200.0);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting egg holder health: " + e.getMessage());
        }
        
        try {
            // Apply glow effect for egg holder
            eggHolder.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                Integer.MAX_VALUE,
                0,
                false,
                false
            ));
        } catch (Exception e) {
            plugin.getLogger().severe("Error applying egg holder effects: " + e.getMessage());
        }
        
        try {
            // Create boss bar for egg holder's health
            createEggHolderBossBar(eggHolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Error creating boss bar: " + e.getMessage());
        }
        
        try {
            // Change all other players to the same team
            setupTeams(eggHolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting up teams: " + e.getMessage());
        }
        
        try {
            // Remove immortality from all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeImmortal(player);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error removing immortality: " + e.getMessage());
        }
        
        try {
            // Send messages to all players
            announceEggHolderPhase(eggHolder);
        } catch (Exception e) {
            plugin.getLogger().severe("Error announcing phase: " + e.getMessage());
        }
        
        try {
            // Play dramatic sound
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error playing sound: " + e.getMessage());
        }
    }
    
    /**
     * Create and display the boss bar for the egg holder's health
     */
    private void createEggHolderBossBar(Player eggHolder) {
        updateBossBarTitle(eggHolder);
        
        eggHolderBossBar = BossBar.bossBar(
            Component.text(""),
            1.0f,
            BossBar.Color.PURPLE,
            BossBar.Overlay.NOTCHED_20
        );
        
        updateBossBarTitle(eggHolder);
        
        // Show to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(eggHolderBossBar);
        }
        
        // Start health update task
        startHealthUpdateTask(eggHolder);
        
        // Start team timer task
        startTeamTimerTask();
    }
    
    /**
     * Update the boss bar title with current holder and timer
     */
    private void updateBossBarTitle(Player eggHolder) {
        if (eggHolderBossBar == null) {
            return;
        }
        
        // Get team time for display (use empty string as key if team is null)
        String teamKey = eggHolderTeam != null ? eggHolderTeam : "";
        int totalTeamTime = teamHoldTimes.getOrDefault(teamKey, 0) + currentHoldTime;
        int remainingTime = Math.max(0, WIN_TIME_SECONDS - totalTeamTime);
        int minutes = remainingTime / 60;
        int seconds = remainingTime % 60;
        
        Component title = lang.getComponent("endfight.boss-bar-title")
            .replaceText(builder -> builder.matchLiteral("{player}").replacement(eggHolder.getName()))
            .append(Component.text(" "))
            .append(Component.text(String.format("⏱ %d:%02d", minutes, seconds), NamedTextColor.YELLOW));
        
        eggHolderBossBar.name(title);
    }
    
    /**
     * Start a task to continuously update the boss bar with egg holder's health
     */
    private void startHealthUpdateTask(Player initialEggHolder) {
        healthUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Use the current egg holder, not the initial one
                if (eggHolder == null || !eggHolder.isOnline() || !active) {
                    this.cancel();
                    return;
                }
                
                double health = eggHolder.getHealth();
                double maxHealth = eggHolder.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                float progress = (float) Math.max(0.0, Math.min(1.0, health / maxHealth));
                
                if (eggHolderBossBar != null) {
                    eggHolderBossBar.progress(progress);
                    updateBossBarTitle(eggHolder);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
    
    /**
     * Start the team timer task that increments hold time every second
     */
    private void startTeamTimerTask() {
        teamTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || eggHolder == null || !eggHolder.isOnline()) {
                    this.cancel();
                    return;
                }
                
                // Increment hold time
                currentHoldTime++;
                
                // Check win condition (use empty string as key if team is null)
                String teamKey = eggHolderTeam != null ? eggHolderTeam : "";
                int totalTeamTime = teamHoldTimes.getOrDefault(teamKey, 0) + currentHoldTime;
                if (totalTeamTime >= WIN_TIME_SECONDS) {
                    onTeamWins(eggHolderTeam != null ? eggHolderTeam : "No Team");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second (20 ticks)
    }
    
    private void setupTeams(Player eggHolder) {
        // Keep original teams - no changes made
    }
    
    /**
     * Announce the egg holder phase to all players
     */
    private void announceEggHolderPhase(Player eggHolder) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.LIGHT_PURPLE));
            
            if (player.getUniqueId().equals(eggHolder.getUniqueId())) {
                player.sendMessage(lang.getComponent("endfight.you-are-boss"));
                player.sendMessage(lang.getComponent("endfight.survive-instructions"));
            } else {
                player.sendMessage(lang.getComponent("endfight.defeat-boss")
                    .replaceText(builder -> builder.matchLiteral("{player}").replacement(eggHolder.getName())));
            }
            
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text(""));
        }
    }
    
    /**
     * Handle when a team wins by holding the egg for 10 minutes
     */
    private void onTeamWins(String winningTeam) {
        // Create title screen
        Component titleText = lang.getComponent("endfight.team-won")
            .replaceText(builder -> builder.matchLiteral("{team}").replacement(winningTeam));
        Component subtitleText = lang.getComponent("endfight.team-won-subtitle");
        
        // Create title with timings
        Title gameTitle = Title.title(
            titleText,
            subtitleText,
            Title.Times.times(
                java.time.Duration.ofMillis(500),  // Fade in
                java.time.Duration.ofSeconds(5),    // Stay
                java.time.Duration.ofSeconds(2)     // Fade out
            )
        );
        
        // Announce team victory to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Show title
            player.showTitle(gameTitle);
            
            // Send chat messages
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            player.sendMessage(lang.getComponent("endfight.team-won")
                .replaceText(builder -> builder.matchLiteral("{team}").replacement(winningTeam)));
            player.sendMessage(lang.getComponent("endfight.team-won-subtitle"));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Deactivate the end fight
        deactivate();
    }
    
    /**
     * Handle when the egg holder is killed by another player
     */
    public void onEggHolderKilled(Player killer) {
        if (!active || eggHolder == null) {
            return;
        }
        
        Player previousHolder = eggHolder;
        String previousTeam = eggHolderTeam;
        String newTeam = plugin.getDataManager().getPlayerTeam(killer.getUniqueId());
        
        // Use empty strings as keys for null teams to ensure consistent map lookups
        String prevTeamKey = previousTeam != null ? previousTeam : "";
        String newTeamKey = newTeam != null ? newTeam : "";
        
        // Check if the new holder is from the same team
        boolean sameTeam = prevTeamKey.equals(newTeamKey);
        
        if (sameTeam) {
            // Same team keeps the egg - don't reset anything, just transfer
            // The timer continues counting for this team
        } else {
            // Different team - save the previous team's accumulated time
            int accumulated = teamHoldTimes.getOrDefault(prevTeamKey, 0) + currentHoldTime;
            teamHoldTimes.put(prevTeamKey, accumulated);
            
            // Load the new team's previously accumulated time (if any) and reset currentHoldTime to 0
            // The new team's total time will be teamHoldTimes.get(newTeamKey) + currentHoldTime
            // This ensures teams retain their progress even if they lose and regain the egg
            currentHoldTime = 0;
            // Note: The new team's accumulated time is already stored in teamHoldTimes.get(newTeamKey)
            // and will be added to currentHoldTime when checking win conditions and displaying time
        }
        
        // Transfer egg holder status
        transferEggHolder(killer, newTeam, previousHolder);
    }
    
    /**
     * Transfer egg holder status to a new player
     */
    private void transferEggHolder(Player newHolder, String newTeam, Player previousHolder) {
        // Remove dragon egg from previous holder and give to new holder
        try {
            if (previousHolder != null && previousHolder.isOnline()) {
                previousHolder.getInventory().remove(Material.DRAGON_EGG);
            }
            // Force dragon egg into new holder's inventory
            newHolder.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG, 1));
        } catch (Exception e) {
            plugin.getLogger().warning("Error transferring dragon egg: " + e.getMessage());
        }
        
        // Clean up previous holder
        try {
            if (previousHolder != null && previousHolder.isOnline()) {
                previousHolder.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
                if (previousHolder.getHealth() > 20.0) {
                    previousHolder.setHealth(20.0);
                }
                for (PotionEffect effect : previousHolder.getActivePotionEffects()) {
                    previousHolder.removePotionEffect(effect.getType());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error cleaning up previous egg holder: " + e.getMessage());
        }
        
        // Set new holder
        this.eggHolder = newHolder;
        this.eggHolderTeam = newTeam;
        this.lastDamager = null; // Reset last damager for new holder
        
        // Set new holder's health
        try {
            newHolder.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(200.0);
            newHolder.setHealth(200.0);
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting new egg holder health: " + e.getMessage());
        }
        
        // Apply glow effect for new egg holder
        try {
            newHolder.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                Integer.MAX_VALUE,
                0,
                false,
                false
            ));
        } catch (Exception e) {
            plugin.getLogger().severe("Error applying new egg holder effects: " + e.getMessage());
        }
        
        // Update boss bar
        updateBossBarTitle(newHolder);
        
        // Announce transfer
        announceEggHolderTransfer(newHolder, newTeam);
    }
    
    /**
     * Announce egg holder transfer to all players
     */
    private void announceEggHolderTransfer(Player newHolder, String team) {
        // Calculate remaining time for the new team (use empty string as key if null)
        String teamKey = team != null ? team : "";
        int totalTeamTime = teamHoldTimes.getOrDefault(teamKey, 0) + currentHoldTime;
        int remainingTime = Math.max(0, WIN_TIME_SECONDS - totalTeamTime);
        int minutes = remainingTime / 60;
        int seconds = remainingTime % 60;
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(lang.getComponent("endfight.new-holder")
                .replaceText(builder -> builder.matchLiteral("{player}").replacement(newHolder.getName())));
            if (team != null) {
                player.sendMessage(lang.getComponent("endfight.team-time-remaining")
                    .replaceText(builder -> builder.matchLiteral("{team}").replacement(team))
                    .replaceText(builder -> builder.matchLiteral("{time}").replacement(String.format("%d:%02d", minutes, seconds))));
            } else {
                player.sendMessage(Component.text("  Time remaining: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%d:%02d", minutes, seconds), NamedTextColor.GOLD)));
            }
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.LIGHT_PURPLE));
            player.sendMessage(Component.text(""));
            
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }
    }
    
    /**
     * Handle when a new player joins during the end fight
     */
    public void handlePlayerJoin(Player player) {
        if (!active || !eggCollected) {
            return;
        }
        
        // Keep original team - no team assignment
        // Players joining during end fight keep their existing teams
        
        // Show boss bar
        if (eggHolderBossBar != null) {
            player.showBossBar(eggHolderBossBar);
        }
    }
}
