package de.connunity.util.challenge;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages a pool of pre-generated worlds for Folia servers.
 * Since Folia doesn't support dynamic world creation, we maintain a rotating
 * pool of worlds with different seeds that get cycled through on each reset.
 */
public class FoliaWorldPoolManager {
    
    private final Plugin plugin;
    private final Logger logger;
    private final List<String> worldPool;
    private final Map<String, Long> worldSeeds;
    private final Map<String, Boolean> worldInUse;
    private int currentWorldIndex;
    private final int poolSize;
    private final String poolBaseName;
    private final boolean autoRegenerate;
    private final Random random;
    
    public FoliaWorldPoolManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.worldPool = new ArrayList<>();
        this.worldSeeds = new ConcurrentHashMap<>();
        this.worldInUse = new ConcurrentHashMap<>();
        this.random = new Random();
        
        // Load configuration
        this.poolSize = plugin.getConfig().getInt("world.folia-world-pool.pool-size", 5);
        this.poolBaseName = plugin.getConfig().getString("world.folia-world-pool.pool-base-name", "speedrun_pool");
        this.autoRegenerate = plugin.getConfig().getBoolean("world.folia-world-pool.auto-regenerate-unused", true);
        this.currentWorldIndex = 0;
        
        // Validate pool size
        if (poolSize < 2) {
            logger.warning("World pool size must be at least 2. Setting to 3.");
            plugin.getConfig().set("world.folia-world-pool.pool-size", 3);
        }
        if (poolSize > 20) {
            logger.warning("World pool size is very large (" + poolSize + "). This may use significant disk space.");
        }
        
        logger.info("Folia World Pool Manager initialized (pool size: " + poolSize + ")");
    }
    
    /**
     * Initialize the world pool - create or load pool worlds
     */
    public void initializePool() {
        boolean pregenerate = plugin.getConfig().getBoolean("world.folia-world-pool.pregenerate-on-startup", true);
        
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("Initializing World Pool for Folia");
        logger.info("Pool Size: " + poolSize + " | Base Name: " + poolBaseName);
        logger.info("═══════════════════════════════════════════════════════");
        
        // Build list of worlds to initialize
        for (int i = 0; i < poolSize; i++) {
            String worldName = poolBaseName + "_" + i;
            worldPool.add(worldName);
            worldInUse.put(worldName, false);
        }
        
        // Initialize worlds asynchronously to avoid blocking scheduler
        initializeWorldsAsync(0, pregenerate);
    }
    
    /**
     * Recursively initialize worlds one at a time to avoid blocking
     */
    private void initializeWorldsAsync(int index, boolean pregenerate) {
        if (index >= poolSize) {
            // All worlds initialized - mark first as active
            String firstWorld = worldPool.get(0);
            worldInUse.put(firstWorld, true);
            
            logger.info("═══════════════════════════════════════════════════════");
            logger.info("World Pool initialized successfully!");
            logger.info("Active world: " + firstWorld);
            logger.info("═══════════════════════════════════════════════════════");
            return;
        }
        
        String worldName = poolBaseName + "_" + index;
        World existingWorld = Bukkit.getWorld(worldName);
        
        if (existingWorld != null) {
            // World already loaded
            worldSeeds.put(worldName, existingWorld.getSeed());
            logger.info("✓ Found existing pool world: " + worldName + " (seed: " + existingWorld.getSeed() + ")");
            // Continue with next world
            initializeWorldsAsync(index + 1, pregenerate);
        } else if (worldExists(worldName)) {
            // World folder exists but not loaded
            if (pregenerate || index == 0) {
                logger.info("→ Loading pool world: " + worldName);
                loadPoolWorldAsync(worldName, null, (world) -> {
                    if (world != null) {
                        worldSeeds.put(worldName, world.getSeed());
                        logger.info("✓ Loaded pool world: " + worldName + " (seed: " + world.getSeed() + ")");
                    }
                    // Continue with next world
                    initializeWorldsAsync(index + 1, pregenerate);
                });
            } else {
                logger.info("○ Pool world exists but not loaded: " + worldName + " (will load on demand)");
                initializeWorldsAsync(index + 1, pregenerate);
            }
        } else {
            // World doesn't exist - create it if pregenerating or it's the first world
            if (pregenerate || index == 0) {
                long seed = random.nextLong();
                logger.info("→ Creating new pool world: " + worldName + " (seed: " + seed + ")");
                createPoolWorldAsync(worldName, seed, (world) -> {
                    if (world != null) {
                        worldSeeds.put(worldName, seed);
                        logger.info("✓ Created pool world: " + worldName);
                    }
                    // Continue with next world
                    initializeWorldsAsync(index + 1, pregenerate);
                });
            } else {
                logger.info("○ Pool world will be created on demand: " + worldName);
                initializeWorldsAsync(index + 1, pregenerate);
            }
        }
    }
    
    /**
     * Get the currently active world from the pool
     */
    public World getCurrentWorld() {
        String worldName = worldPool.get(currentWorldIndex);
        World world = Bukkit.getWorld(worldName);
        
        if (world == null) {
            // World not loaded - load it
            Long seed = worldSeeds.get(worldName);
            world = loadPoolWorld(worldName, seed);
            if (world == null) {
                // Failed to load - create new
                seed = random.nextLong();
                world = createPoolWorld(worldName, seed);
                worldSeeds.put(worldName, seed);
            }
        }
        
        return world;
    }
    
    /**
     * Rotate to the next world in the pool and return it
     */
    public World rotateToNextWorld() {
        // Mark current world as not in use
        String currentWorld = worldPool.get(currentWorldIndex);
        worldInUse.put(currentWorld, false);
        
        // Move to next world
        currentWorldIndex = (currentWorldIndex + 1) % poolSize;
        String nextWorld = worldPool.get(currentWorldIndex);
        
        logger.info("Rotating from " + currentWorld + " to " + nextWorld);
        
        // Ensure next world exists and is loaded
        World world = Bukkit.getWorld(nextWorld);
        if (world == null) {
            Long seed = worldSeeds.get(nextWorld);
            if (seed == null) {
                // Generate new seed for this world
                seed = random.nextLong();
                worldSeeds.put(nextWorld, seed);
                logger.info("Generated new seed for " + nextWorld + ": " + seed);
            }
            
            // Try to load existing world first
            if (worldExists(nextWorld)) {
                world = loadPoolWorld(nextWorld, seed);
                logger.info("Loaded existing world: " + nextWorld);
            }
            
            // If still null, create it
            if (world == null) {
                world = createPoolWorld(nextWorld, seed);
                logger.info("Created new world: " + nextWorld + " with seed: " + seed);
            }
        }
        
        // Mark as in use
        worldInUse.put(nextWorld, true);
        
        // Schedule regeneration of old world if enabled
        if (autoRegenerate) {
            scheduleWorldRegeneration(currentWorld);
        }
        
        return world;
    }
    
    /**
     * Get the name of the current active world
     */
    public String getCurrentWorldName() {
        return worldPool.get(currentWorldIndex);
    }
    
    /**
     * Get the seed of the current world
     */
    public long getCurrentSeed() {
        String worldName = getCurrentWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world.getSeed();
        }
        return worldSeeds.getOrDefault(worldName, 0L);
    }
    
    /**
     * Callback interface for async world operations
     */
    @FunctionalInterface
    private interface WorldCallback {
        void onComplete(World world);
    }
    
    /**
     * Create a new pool world asynchronously with callback
     */
    private void createPoolWorldAsync(String worldName, long seed, WorldCallback callback) {
        // Schedule on global thread (works on both Folia and Paper)
        FoliaSchedulerUtil.runTask(plugin, () -> {
            try {
                World world = createWorldInternal(worldName, seed);
                callback.onComplete(world);
            } catch (Exception e) {
                logger.severe("Failed to create pool world " + worldName + ": " + e.getMessage());
                e.printStackTrace();
                callback.onComplete(null);
            }
        });
    }
    
    /**
     * Load pool world asynchronously with callback
     */
    private void loadPoolWorldAsync(String worldName, Long seed, WorldCallback callback) {
        // Schedule on global thread (works on both Folia and Paper)
        FoliaSchedulerUtil.runTask(plugin, () -> {
            try {
                World world = loadPoolWorldInternal(worldName, seed);
                callback.onComplete(world);
            } catch (Exception e) {
                logger.severe("Failed to load pool world " + worldName + ": " + e.getMessage());
                e.printStackTrace();
                callback.onComplete(null);
            }
        });
    }
    
    /**
     * Create a new pool world with the given seed (blocking - only use during /fullreset)
     * IMPORTANT: In Folia, world creation must happen on the global region thread.
     * This method should only be used when already on the global thread (e.g., during /fullreset).
     */
    private World createPoolWorld(String worldName, long seed) {
        try {
            return createWorldInternal(worldName, seed);
        } catch (Exception e) {
            logger.severe("Failed to create pool world " + worldName + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Internal method to create a world. Must be called on the global thread in Folia.
     */
    private World createWorldInternal(String worldName, long seed) {
        WorldCreator creator = new WorldCreator(worldName);
        creator.seed(seed);
        
        // Apply world generation settings from config
        String worldType = plugin.getConfig().getString("world.generation.type", "NORMAL");
        creator.type(WorldType.valueOf(worldType.toUpperCase()));
        
        boolean generateStructures = plugin.getConfig().getBoolean("world.generation.generate-structures", true);
        creator.generateStructures(generateStructures);
        
        World world = creator.createWorld();
        
        if (world != null) {
            applyWorldSettings(world);
            logger.info("Created pool world: " + worldName + " (seed: " + seed + ")");
        }
        
        return world;
    }
    
    /**
     * Load an existing pool world
     * IMPORTANT: In Folia, world loading must happen on the global region thread.
     */
    /**
     * Load pool world (blocking - only use during /fullreset when already on global thread)
     */
    private World loadPoolWorld(String worldName, Long seed) {
        try {
            return loadWorldInternal(worldName, seed);
        } catch (Exception e) {
            logger.severe("Failed to load pool world " + worldName + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Internal method to load a world from disk
     */
    private World loadPoolWorldInternal(String worldName, Long seed) {
        return loadWorldInternal(worldName, seed);
    }
    
    /**
     * Internal method to load a world. Must be called on the global thread in Folia.
     */
    private World loadWorldInternal(String worldName, Long seed) {
        WorldCreator creator = new WorldCreator(worldName);
        if (seed != null) {
            creator.seed(seed);
        }
        
        World world = creator.createWorld();
        if (world != null) {
            applyWorldSettings(world);
            worldSeeds.put(worldName, world.getSeed());
        }
        return world;
    }
    
    /**
     * Apply world settings (difficulty, game rules, etc.)
     */
    private void applyWorldSettings(World world) {
        // Apply difficulty
        String difficulty = plugin.getConfig().getString("world.difficulty", "NORMAL");
        try {
            world.setDifficulty(Difficulty.valueOf(difficulty.toUpperCase()));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid difficulty: " + difficulty + ", using NORMAL");
            world.setDifficulty(Difficulty.NORMAL);
        }
        
        // Apply game rules from config
        ConfigurationSection gamerules = plugin.getConfig().getConfigurationSection("world.gamerules.speedrun-world");
        if (gamerules != null) {
            for (String key : gamerules.getKeys(false)) {
                Object value = gamerules.get(key);
                try {
                    GameRule<?> gameRule = GameRule.getByName(key);
                    if (gameRule != null && value instanceof Boolean) {
                        @SuppressWarnings("unchecked")
                        GameRule<Boolean> boolRule = (GameRule<Boolean>) gameRule;
                        world.setGameRule(boolRule, (Boolean) value);
                    } else if (gameRule != null && value instanceof Integer) {
                        @SuppressWarnings("unchecked")
                        GameRule<Integer> intRule = (GameRule<Integer>) gameRule;
                        world.setGameRule(intRule, (Integer) value);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to set game rule " + key + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Check if a world folder exists
     */
    private boolean worldExists(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        return worldFolder.exists() && worldFolder.isDirectory();
    }
    
    /**
     * Schedule regeneration of a world that's no longer in use
     */
    private void scheduleWorldRegeneration(String worldName) {
        // Use async scheduler to avoid blocking
        FoliaSchedulerUtil.runTaskLaterAsynchronously(plugin, () -> {
            if (!worldInUse.get(worldName)) {
                logger.info("Scheduling regeneration of world: " + worldName);
                
                // Generate new seed
                long newSeed = random.nextLong();
                
                // Use global scheduler for world operations
                FoliaSchedulerUtil.runTask(plugin, () -> {
                    regenerateWorld(worldName, newSeed);
                });
            }
        }, 200L); // Wait 10 seconds before regenerating
    }
    
    /**
     * Regenerate a world with a new seed
     */
    private void regenerateWorld(String worldName, long newSeed) {
        logger.info("Regenerating world " + worldName + " with new seed: " + newSeed);
        
        try {
            // Unload world if loaded
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                // Teleport any remaining players out
                World waitingRoom = Bukkit.getWorld(plugin.getConfig().getString("world.waiting-room", "waiting_room"));
                if (waitingRoom != null) {
                    for (Player player : world.getPlayers()) {
                        player.teleport(waitingRoom.getSpawnLocation());
                    }
                }
                
                boolean unloaded = Bukkit.unloadWorld(world, false);
                if (!unloaded) {
                    logger.warning("Failed to unload world " + worldName);
                    return;
                }
            }
            
            // Delete world folder
            File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists()) {
                deleteWorldFolder(worldFolder);
                logger.info("Deleted old world folder: " + worldName);
            }
            
            // Create new world with new seed
            World newWorld = createPoolWorld(worldName, newSeed);
            if (newWorld != null) {
                worldSeeds.put(worldName, newSeed);
                logger.info("Successfully regenerated " + worldName + " with seed: " + newSeed);
                
                // Unload it again to save resources
                Bukkit.unloadWorld(newWorld, true);
            }
            
        } catch (Exception e) {
            logger.severe("Failed to regenerate world " + worldName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Delete a world folder recursively
     */
    private void deleteWorldFolder(File worldFolder) throws IOException {
        Path path = worldFolder.toPath();
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
    
    /**
     * Check if world pool mode is enabled
     */
    public static boolean isEnabled(Plugin plugin) {
        String enabled = plugin.getConfig().getString("world.folia-world-pool.enabled", "auto");
        
        if ("auto".equalsIgnoreCase(enabled)) {
            return FoliaSchedulerUtil.isFolia();
        }
        
        return Boolean.parseBoolean(enabled);
    }
    
    /**
     * Get pool statistics
     */
    public Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("poolSize", poolSize);
        stats.put("currentWorld", getCurrentWorldName());
        stats.put("currentSeed", getCurrentSeed());
        stats.put("worldsLoaded", worldPool.stream().filter(w -> Bukkit.getWorld(w) != null).count());
        return stats;
    }
}
