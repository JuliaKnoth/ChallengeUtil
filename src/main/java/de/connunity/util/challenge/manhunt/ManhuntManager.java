package de.connunity.util.challenge.manhunt;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;

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
 * Manages manhunt mode mechanics: blindness for hunters, compass tracking
 * system
 */
public class ManhuntManager {

    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private BukkitRunnable blindnessTask;
    private BukkitRunnable compassChargeTask;
    private BukkitRunnable compassUpdateTask;
    private BukkitRunnable glassPlacementTask;
    private long startTime;
    private static final long TWO_MINUTES = 2 * 60 * 1000; // 2 minutes in milliseconds (blindness duration)
    private static final long FIRST_CHARGE_DELAY = 4 * 60 * 1000; // 4 minutes (2 min blindness + 2 min wait)
    private static final long COMPASS_CHARGE_TIME = 2 * 60 * 1000; // 2 minutes in milliseconds

    // Track compass charge status for each hunter
    private final Map<UUID, Long> compassLastCharged = new HashMap<>();
    private final Map<UUID, Boolean> compassCharged = new HashMap<>();

    // Track last target location to avoid unnecessary compass updates
    private final Map<UUID, Location> compassLastTarget = new HashMap<>();
    
    // Track last known runner position for each hunter (to prevent compass spinning)
    private final Map<UUID, Location> lastKnownRunnerPosition = new HashMap<>();
    
    // Track glass blocks placed under hunters during blindness period
    private final java.util.Set<Location> placedGlassBlocks = new java.util.HashSet<>();

    public ManhuntManager(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Check if hunter movement is currently restricted (first 2 minutes)
     */
    public boolean isHunterMovementRestricted() {
        if (startTime == 0) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed < TWO_MINUTES;
    }

    /**
     * Get remaining time (in seconds) that hunters are restricted
     */
    public long getHunterRestrictionTimeRemaining() {
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
     * Start manhunt mechanics when timer starts
     */
    public void start() {
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }

        startTime = System.currentTimeMillis();

        // Clear compass tracking data
        compassLastCharged.clear();
        compassCharged.clear();
        compassLastTarget.clear();
        lastKnownRunnerPosition.clear();
        placedGlassBlocks.clear();

        // Start blindness task for hunters (first 2 minutes)
        startBlindnessTask();
        
        // Start glass placement task for hunters (first 2 minutes)
        startGlassPlacementTask();

        // Give all hunters compasses and start compass tracking
        giveHuntersCompasses();
        startCompassChargeTask();
        startCompassUpdateTask();
    }

    /**
     * Stop all manhunt tasks
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
        
        if (glassPlacementTask != null) {
            glassPlacementTask.cancel();
            glassPlacementTask = null;
        }

        // Reset start time
        startTime = 0;

        // Remove blindness from all hunters
        removeBlindnessFromHunters();

        // Remove glow from all runners
        removeGlowFromRunners();
        
        // Remove placed glass blocks
        removeGlassBlocks();

        // Clear compass tracking data
        compassLastCharged.clear();
        compassCharged.clear();
        compassLastTarget.clear();
        lastKnownRunnerPosition.clear();
        placedGlassBlocks.clear();
    }

    /**
     * Apply blindness to all hunters for the first 2 minutes (uses real-world time)
     */
    private void startBlindnessTask() {
        blindnessTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Use real-world time instead of tick count
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed >= TWO_MINUTES) {
                    // 2 minutes have passed, remove blindness and cancel task
                    removeBlindnessFromHunters();

                    // Notify hunters
                    Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
                    for (UUID hunterId : hunters) {
                        Player hunter = Bukkit.getPlayer(hunterId);
                        if (hunter != null && hunter.isOnline()) {
                            hunter.sendMessage(lang.getComponent("manhunt.blindness-over"));
                            hunter.sendMessage(lang.getComponent("manhunt.can-see-and-move"));
                            hunter.playSound(hunter.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                            
                            // Remove invincibility
                            hunter.setInvulnerable(false);
                        }
                    }

                    cancel();
                    return;
                }

                // Apply blindness and invincibility to all hunters
                Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
                for (UUID hunterId : hunters) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter != null && hunter.isOnline()) {
                        // Apply blindness effect (2 minutes duration, but we reapply every second)
                        hunter.addPotionEffect(
                                new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, false));
                        
                        // Make hunter invulnerable to prevent mob deaths
                        hunter.setInvulnerable(true);
                    }
                }
            }
        };

        // Run every second - uses real-world milliseconds for elapsed time calculation
        blindnessTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Remove blindness from all hunters
     */
    private void removeBlindnessFromHunters() {
        Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
        for (UUID hunterId : hunters) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline()) {
                hunter.removePotionEffect(PotionEffectType.BLINDNESS);
                hunter.setInvulnerable(false);
            }
        }
    }
    
    /**
     * Start task that places glass blocks beneath hunters during blindness period
     * OPTIMIZED: Runs less frequently to reduce lag, uses real-world time
     */
    private void startGlassPlacementTask() {
        glassPlacementTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Use real-world time instead of tick count
                long elapsed = System.currentTimeMillis() - startTime;

                if (elapsed >= TWO_MINUTES) {
                    // 2 minutes have passed, remove glass and cancel task
                    removeGlassBlocks();
                    cancel();
                    return;
                }

                // Place glass beneath all hunters
                Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
                for (UUID hunterId : hunters) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter != null && hunter.isOnline()) {
                        Location hunterLoc = hunter.getLocation();
                        Location blockBelow = hunterLoc.clone().subtract(0, 1, 0);
                        
                        // Only place glass if the block below is air
                        if (blockBelow.getBlock().getType() == Material.AIR) {
                            blockBelow.getBlock().setType(Material.GLASS);
                            placedGlassBlocks.add(blockBelow.clone());
                        }
                    }
                }
            }
        };

        // OPTIMIZED: Run every 10 ticks (0.5 seconds) - uses real-world time for duration check
        glassPlacementTask.runTaskTimer(plugin, 0L, 10L);
    }
    
    /**
     * Remove all glass blocks that were placed during blindness period
     */
    private void removeGlassBlocks() {
        for (Location loc : placedGlassBlocks) {
            if (loc.getBlock().getType() == Material.GLASS) {
                loc.getBlock().setType(Material.AIR);
            }
        }
        placedGlassBlocks.clear();
    }

    /**
     * Give all hunters tracking compasses
     */
    private void giveHuntersCompasses() {
        Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
        for (UUID hunterId : hunters) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline()) {
                giveCompass(hunter, false);
            }
        }
    }

    /**
     * Give a compass to a specific hunter (public method for when player joins
     * hunter team)
     */
    public void giveCompassToHunter(Player hunter) {
        giveCompass(hunter, false);
    }

    /**
     * Give a compass to a hunter (with or without enchantment glint)
     */
    private void giveCompass(Player hunter, boolean charged) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        if (meta != null) {
            meta.displayName(lang.getComponent("manhunt.compass-name"));

            java.util.List<Component> lore = new java.util.ArrayList<>();

            if (charged) {
                meta.addEnchant(Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(lang.getComponent("manhunt.compass-charged-status"));
                lore.add(lang.getComponent("manhunt.compass-track-runners"));
            } else {
                lore.add(lang.getComponent("manhunt.compass-charging-status"));
                lore.add(lang.getComponent("manhunt.compass-ready-hint"));
            }

            meta.lore(lore);
            compass.setItemMeta(meta);
        }

        // Check if hunter already has a compass
        boolean hasCompass = false;
        for (ItemStack item : hunter.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) {
                hasCompass = true;
                break;
            }
        }

        if (!hasCompass) {
            hunter.getInventory().addItem(compass);
        }
    }

    /**
     * Update compass in hunter's inventory (add/remove enchantment glint)
     */
    private void updateCompassInInventory(Player hunter, boolean charged) {
        for (int i = 0; i < hunter.getInventory().getSize(); i++) {
            ItemStack item = hunter.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    java.util.List<Component> lore = new java.util.ArrayList<>();

                    if (charged) {
                        meta.addEnchant(Enchantment.LURE, 1, true);
                        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        lore.add(lang.getComponent("manhunt.compass-charged-status"));
                        lore.add(lang.getComponent("manhunt.compass-show-runners"));
                    } else {
                        meta.removeEnchant(Enchantment.LURE);
                        lore.add(lang.getComponent("manhunt.compass-loading"));
                        lore.add(lang.getComponent("manhunt.compass-ready-hint"));
                    }

                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Update compass target to point to a specific location (modern Minecraft
     * 1.16+)
     * Uses lodestone compass mechanics for compatibility with newer versions
     */
    /*
     * private void updateCompassTarget(Player hunter, Location target) {
     * for (int i = 0; i < hunter.getInventory().getSize(); i++) {
     * ItemStack item = hunter.getInventory().getItem(i);
     * if (item != null && item.getType() == Material.COMPASS) {
     * ItemMeta meta = item.getItemMeta();
     * if (meta instanceof org.bukkit.inventory.meta.CompassMeta) {
     * org.bukkit.inventory.meta.CompassMeta compassMeta =
     * (org.bukkit.inventory.meta.CompassMeta) meta;
     * 
     * // Set lodestone location (this makes the compass point to this location)
     * compassMeta.setLodestone(target);
     * // Set tracked to true so it points to the lodestone location
     * compassMeta.setLodestoneTracked(false); // false = doesn't need actual
     * lodestone block
     * 
     * item.setItemMeta(compassMeta);
     * }
     * }
     * }
     * }
     */

    /**
     * Start task that charges compasses every 2 minutes (uses real-world time)
     */
    private void startCompassChargeTask() {
        compassChargeTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
                long currentTime = System.currentTimeMillis();

                for (UUID hunterId : hunters) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter == null || !hunter.isOnline()) {
                        continue;
                    }

                    // Check if compass should be charged (using real-world milliseconds)
                    Long lastCharged = compassLastCharged.get(hunterId);
                    Boolean isCharged = compassCharged.get(hunterId);

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
                        compassCharged.put(hunterId, true);
                        updateCompassInInventory(hunter, true);

                        // Update last charged time if this is a new charge (not initial)
                        if (lastCharged != null) {
                            compassLastCharged.put(hunterId, currentTime);
                        }

                        hunter.sendMessage(lang.getComponent("manhunt.compass-charged"));
                        hunter.playSound(hunter.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f,
                                1.5f);
                    }
                }
            }
        };

        // Run every second - uses real-world milliseconds for timing calculations
        compassChargeTask.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Start task that updates compass direction to point to nearest runner
     * OPTIMIZED: Runs less frequently to reduce lag, independent of game ticks
     */
    private void startCompassUpdateTask() {
        compassUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> hunters = plugin.getDataManager().getPlayersInTeam("hunter");
                Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");

                if (hunters.isEmpty() || runners.isEmpty()) {
                    return;
                }

                for (UUID hunterId : hunters) {
                    Player hunter = Bukkit.getPlayer(hunterId);
                    if (hunter == null || !hunter.isOnline()) {
                        continue;
                    }

                    // Find nearest runner in same world
                    Player nearestRunner = null;
                    double nearestDistance = Double.MAX_VALUE;

                    for (UUID runnerId : runners) {
                        Player runner = Bukkit.getPlayer(runnerId);
                        if (runner == null || !runner.isOnline()) {
                            continue;
                        }

                        // Only track runners in same world
                        if (hunter.getWorld().equals(runner.getWorld())) {
                            double distance = hunter.getLocation().distance(runner.getLocation());
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestRunner = runner;
                            }
                        }
                    }

                    // Update compass to point to nearest runner or last known position
                    if (nearestRunner != null) {
                        Location targetLoc = nearestRunner.getLocation();
                        // Update compass needle (doesn't cause flickering)
                        hunter.setCompassTarget(targetLoc);
                        // Store this as the last known position for this hunter
                        lastKnownRunnerPosition.put(hunterId, targetLoc.clone());
                    } else {
                        // No runner in same dimension - point to last known position or portal
                        Location fallbackTarget = findFallbackCompassTarget(hunter, hunterId);
                        if (fallbackTarget != null) {
                            hunter.setCompassTarget(fallbackTarget);
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
     * Find a fallback target for compass when no runners are in same dimension
     * Returns last known runner position, or nearest portal, or null
     */
    private Location findFallbackCompassTarget(Player hunter, UUID hunterId) {
        // First, try to use last known runner position if it exists and is in same world
        Location lastKnown = lastKnownRunnerPosition.get(hunterId);
        if (lastKnown != null && lastKnown.getWorld().equals(hunter.getWorld())) {
            return lastKnown;
        }
        
        // Otherwise, try to find a portal to point to
        Location portalTarget = findNearestPortal(hunter);
        if (portalTarget != null) {
            return portalTarget;
        }
        
        // If we have a last known position in a different dimension, still use it
        // (compass will just spin, but at least it's consistent)
        if (lastKnown != null) {
            return lastKnown;
        }
        
        // No fallback available - return null (compass keeps current target)
        return null;
    }
    
    /**
     * Find the nearest portal (Nether or End) to the hunter
     * Returns the portal location or null if none found nearby
     */
    private Location findNearestPortal(Player hunter) {
        Location hunterLoc = hunter.getLocation();
        org.bukkit.World world = hunter.getWorld();
        
        // Search radius for portals (in blocks)
        int searchRadius = 128;
        
        Location nearestPortal = null;
        double nearestDistance = Double.MAX_VALUE;
        
        // Search for nether portals (obsidian frame)
        for (int x = -searchRadius; x <= searchRadius; x += 4) {
            for (int y = -searchRadius; y <= searchRadius; y += 4) {
                for (int z = -searchRadius; z <= searchRadius; z += 4) {
                    Location checkLoc = hunterLoc.clone().add(x, y, z);
                    
                    // Check if this location is within world bounds
                    if (checkLoc.getBlockY() < world.getMinHeight() || checkLoc.getBlockY() > world.getMaxHeight()) {
                        continue;
                    }
                    
                    Material blockType = checkLoc.getBlock().getType();
                    
                    // Check for nether portal or end portal frame
                    if (blockType == Material.NETHER_PORTAL || blockType == Material.END_PORTAL_FRAME || blockType == Material.END_PORTAL) {
                        double distance = hunterLoc.distance(checkLoc);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestPortal = checkLoc.clone();
                        }
                    }
                }
            }
        }
        
        return nearestPortal;
    }

    /**
     * Use compass charge (called when hunter right-clicks compass)
     * Returns true if successful, false if not charged
     */
    public boolean useCompassCharge(Player hunter) {
        Boolean charged = compassCharged.get(hunter.getUniqueId());

        if (charged == null || !charged) {
            return false;
        }

        // Discharge the compass
        compassCharged.put(hunter.getUniqueId(), false);
        compassLastCharged.put(hunter.getUniqueId(), System.currentTimeMillis());
        updateCompassInInventory(hunter, false);

        // Get all runners
        Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");

        if (runners.isEmpty()) {
            return true; // Still consume charge even if no runners
        }

        // Check if all runners are in different dimension
        boolean allRunnersInDifferentDimension = true;
        String runnerDimension = null; // Track which dimension runners are in
        
        for (UUID runnerId : runners) {
            Player runner = Bukkit.getPlayer(runnerId);
            if (runner != null && runner.isOnline()) {
                if (hunter.getWorld().equals(runner.getWorld())) {
                    allRunnersInDifferentDimension = false;
                    break;
                }
                // Determine which dimension the runner is in
                if (runnerDimension == null) {
                    String worldName = runner.getWorld().getName().toLowerCase();
                    if (worldName.contains("nether") || runner.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
                        runnerDimension = "nether";
                    } else if (worldName.contains("end") || runner.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
                        runnerDimension = "end";
                    } else {
                        runnerDimension = "overworld";
                    }
                }
            }
        }

        if (allRunnersInDifferentDimension && runnerDimension != null) {
            // Show dimension-specific effect
            if (runnerDimension.equals("end")) {
                showEndCircle(hunter);
                hunter.sendMessage(lang.getComponent("manhunt.runners-in-end"));
            } else if (runnerDimension.equals("overworld")) {
                showOverworldCircle(hunter);
                hunter.sendMessage(lang.getComponent("manhunt.runners-in-overworld"));
            } else {
                // Nether (default)
                showNetherCircle(hunter);
                hunter.sendMessage(lang.getComponent("manhunt.runners-in-nether"));
            }
        } else {
            // Apply glow effect to runners
            applyGlowToRunners(hunter);
            hunter.sendMessage(lang.getComponent("manhunt.runners-glowing"));
        }

        return true;
    }

    /**
     * Get remaining cooldown time in milliseconds for a hunter's compass
     * Returns 0 if charged or if no cooldown info available
     */
    public long getCompassCooldownRemaining(Player hunter) {
        Long lastCharged = compassLastCharged.get(hunter.getUniqueId());
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
     * Apply glowing effect to all runners (in team color if possible)
     */
    private void applyGlowToRunners(Player hunter) {
        Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");

        for (UUID runnerId : runners) {
            Player runner = Bukkit.getPlayer(runnerId);
            if (runner != null && runner.isOnline()) {
                // Apply glowing effect for 10 seconds
                runner.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));

                // Only play sound and notify if hunter is within 25 blocks and in same world
                boolean isClose = false;
                if (hunter.getWorld().equals(runner.getWorld())) {
                    double distance = hunter.getLocation().distance(runner.getLocation());
                    if (distance <= 25.0) {
                        isClose = true;
                    }
                }

                if (isClose) {
                    // Notify runner they're glowing
                    runner.sendMessage(lang.getComponent("manhunt.you-are-glowing"));
                    runner.playSound(runner.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.8f);
                }
            }
        }
    }

    /**
     * Remove glow effect from all runners
     */
    private void removeGlowFromRunners() {
        Set<UUID> runners = plugin.getDataManager().getPlayersInTeam("runner");
        for (UUID runnerId : runners) {
            Player runner = Bukkit.getPlayer(runnerId);
            if (runner != null && runner.isOnline()) {
                runner.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    /**
     * Show a purple circle around the hunter (when runners are in the Nether)
     */
    private void showNetherCircle(Player hunter) {
        Location center = hunter.getLocation().clone().add(0, 0.2, 0); // Slightly above ground

        // Create a circle of purple particles
        int particleCount = 32; // Number of particles in the circle
        double radius = 1.5; // Radius of the circle

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            // Purple particles (portal and soul particles)
            hunter.spawnParticle(Particle.PORTAL, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
            hunter.spawnParticle(Particle.SOUL, particleLoc, 1, 0, 0, 0, 0);
        }

        // Play eerie portal sound
        hunter.playSound(hunter.getLocation(), org.bukkit.Sound.BLOCK_PORTAL_TRAVEL, 0.3f, 1.8f);
    }

    /**
     * Show a dark purple circle around the hunter (when runners are in the End)
     */
    private void showEndCircle(Player hunter) {
        Location center = hunter.getLocation().clone().add(0, 0.2, 0); // Slightly above ground

        // Create a circle of end-themed particles
        int particleCount = 32; // Number of particles in the circle
        double radius = 1.5; // Radius of the circle

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            // Dark purple/end-themed particles
            hunter.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
            hunter.spawnParticle(Particle.REVERSE_PORTAL, particleLoc, 3, 0.1, 0.1, 0.1, 0.02);
        }

        // Play ender dragon sound
        hunter.playSound(hunter.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.4f, 1.5f);
    }

    /**
     * Show a green/earthy circle around the hunter (when runners are in the Overworld)
     */
    private void showOverworldCircle(Player hunter) {
        Location center = hunter.getLocation().clone().add(0, 0.2, 0); // Slightly above ground

        // Create a circle of overworld-themed particles
        int particleCount = 32; // Number of particles in the circle
        double radius = 1.5; // Radius of the circle

        for (int i = 0; i < particleCount; i++) {
            double angle = 2 * Math.PI * i / particleCount;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            Location particleLoc = center.clone().add(x, 0, z);

            // Green/nature-themed particles
            hunter.spawnParticle(Particle.COMPOSTER, particleLoc, 2, 0.1, 0.1, 0.1, 0.01);
            hunter.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1, 0.1, 0.1, 0.1, 0);
        }

        // Play bell sound (village/overworld themed)
        hunter.playSound(hunter.getLocation(), org.bukkit.Sound.BLOCK_BELL_USE, 0.5f, 1.2f);
    }
}
