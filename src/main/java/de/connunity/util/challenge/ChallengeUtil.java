package de.connunity.util.challenge;

import de.connunity.util.challenge.commands.*;
import de.connunity.util.challenge.data.DataManager;
import de.connunity.util.challenge.integration.PlaceholderAPIExpansion;
import de.connunity.util.challenge.lang.LanguageManager;
import de.connunity.util.challenge.listeners.CompassProtectionListener;
import de.connunity.util.challenge.listeners.CompassTrackingListener;
import de.connunity.util.challenge.listeners.ChunkItemChallengeListener;
import de.connunity.util.challenge.listeners.EnderDragonDeathListener;
import de.connunity.util.challenge.listeners.FriendlyFireItemListener;
import de.connunity.util.challenge.listeners.KeepRNGListener;
import de.connunity.util.challenge.listeners.HostControlGUIListener;
import de.connunity.util.challenge.listeners.HostControlItemListener;
import de.connunity.util.challenge.listeners.ManhuntChatListener;
import de.connunity.util.challenge.listeners.ManhuntMovementListener;
import de.connunity.util.challenge.listeners.ManhuntTeamListener;
import de.connunity.util.challenge.listeners.PlayerDeathListener;
import de.connunity.util.challenge.listeners.PlayerJoinListener;
import de.connunity.util.challenge.listeners.PlayerQuitListener;
import de.connunity.util.challenge.listeners.PlayerRespawnListener;
import de.connunity.util.challenge.listeners.PortalTravelListener;
import de.connunity.util.challenge.listeners.PreStartPvPListener;
import de.connunity.util.challenge.listeners.SettingsGUIListener;
import de.connunity.util.challenge.listeners.TeamRaceEnderDragonListener;
import de.connunity.util.challenge.listeners.TeamRaceKillListener;
import de.connunity.util.challenge.listeners.TeamRaceTeamListener;
import de.connunity.util.challenge.listeners.WaitingRoomListener;
import de.connunity.util.challenge.manhunt.ManhuntManager;
import de.connunity.util.challenge.teamrace.TeamRaceManager;
import de.connunity.util.challenge.timer.TimerManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChallengeUtil extends JavaPlugin {

    private TimerManager timerManager;
    private DataManager dataManager;
    private LanguageManager languageManager;
    private ManhuntManager manhuntManager;
    private TeamRaceManager teamRaceManager;
    private PlaceholderAPIExpansion placeholderAPIExpansion;
    private ChunkItemChallengeListener chunkItemChallengeListener;
    private FriendlyFireItemListener friendlyFireItemListener;
    private VersionChecker versionChecker;
    private boolean resetInProgress = false;
    private final Set<String> playersToReset = new HashSet<>();

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize bStats
        int pluginId = 29066;
        Metrics metrics = new Metrics(this, pluginId);

        // Initialize language manager
        languageManager = new LanguageManager(this);

        // Register BungeeCord plugin messaging channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Check for pending seed and apply it to server.properties
        checkAndApplyPendingSeed();

        // Initialize data manager
        dataManager = new DataManager(this);

        // Initialize timer manager
        timerManager = new TimerManager(this, dataManager);

        // Initialize manhunt manager
        manhuntManager = new ManhuntManager(this);

        // Initialize team race manager
        teamRaceManager = new TeamRaceManager(this);

        // Initialize PlaceholderAPI expansion
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIExpansion = new PlaceholderAPIExpansion(this);
            placeholderAPIExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered! Use %ch_prefix% and %ch_suffix%");
        } else {
            getLogger().warning("PlaceholderAPI not found! Team prefixes will not work in chat.");
        }

        // Initialize chunk item challenge listener
        chunkItemChallengeListener = new ChunkItemChallengeListener(this);

        // Initialize friendly fire item listener
        friendlyFireItemListener = new FriendlyFireItemListener(this);

        // Register commands
        getCommand("start").setExecutor(new StartCommand(this, timerManager));
        getCommand("pause").setExecutor(new PauseCommand(this, timerManager));
        getCommand("reset").setExecutor(new ResetCommand(this, timerManager));
        getCommand("fullreset").setExecutor(new FullResetCommand(this, timerManager));
        getCommand("join").setExecutor(new JoinCommand(this));
        getCommand("settings").setExecutor(new SettingsCommand(this));
        getCommand("team").setExecutor(new TeamCommand(this));

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerRespawnListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new SettingsGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new HostControlGUIListener(this), this);
        getServer().getPluginManager().registerEvents(new HostControlItemListener(this), this);
        getServer().getPluginManager().registerEvents(new PortalTravelListener(this), this);
        getServer().getPluginManager().registerEvents(new ManhuntTeamListener(this), this);
        getServer().getPluginManager().registerEvents(new ManhuntChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ManhuntMovementListener(this), this);
        getServer().getPluginManager().registerEvents(new EnderDragonDeathListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamRaceTeamListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamRaceEnderDragonListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamRaceKillListener(this), this);
        getServer().getPluginManager().registerEvents(new CompassTrackingListener(this), this);
        getServer().getPluginManager().registerEvents(new CompassProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new WaitingRoomListener(this), this);
        getServer().getPluginManager().registerEvents(new PreStartPvPListener(this), this);
        getServer().getPluginManager().registerEvents(chunkItemChallengeListener, this);
        getServer().getPluginManager().registerEvents(friendlyFireItemListener, this);
        getServer().getPluginManager().registerEvents(new KeepRNGListener(this), this);

        // Apply gamerules to waiting room on startup
        Bukkit.getScheduler().runTaskLater(this, () -> applyWaitingRoomGameRules(), 20L);

        // Load speedrun world if it exists on disk (bug fix for world not recognized on
        // restart)
        Bukkit.getScheduler().runTaskLater(this, () -> loadSpeedrunWorldIfExists(), 20L);

        // Apply saved gamerules to speedrun world on startup
        Bukkit.getScheduler().runTaskLater(this, () -> applySpeedrunWorldSavedGameRules(), 30L);

        // Ensure speedrun world has a safe spawn point on startup
        Bukkit.getScheduler().runTaskLater(this, () -> ensureSpeedrunWorldSafeSpawn(), 40L);

        // OPTIMIZATION: Disable spawn chunk loading for all worlds to reduce lag
        Bukkit.getScheduler().runTaskLater(this, () -> optimizeWorldSettings(), 50L);

        // Check for plugin updates from Modrinth (24/7 server friendly)
        if (getConfig().getBoolean("update-checker.enabled", true)) {
            // Hardcoded Modrinth project ID for ChallengeUtil
            versionChecker = new VersionChecker(this, "EPkgUkCn");
            // Check 3 seconds after startup to avoid startup lag
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> versionChecker.notifyIfUpdateAvailable(), 60L);
        } else {
            getLogger().info("Update checker is disabled in config.yml");
        }

        getLogger().info("ChallengeUtil has been enabled!");
        getLogger().info("Holodeck Reset System active!");

    }

    @Override
    public void onDisable() {
        // Stop the timer if running
        if (timerManager != null) {
            timerManager.stop();
        }

        // Unregister plugin messaging channel
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");

        getLogger().info("ChallengeUtil has been disabled!");
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public ManhuntManager getManhuntManager() {
        return manhuntManager;
    }

    public TeamRaceManager getTeamRaceManager() {
        return teamRaceManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public ChunkItemChallengeListener getChunkItemChallengeListener() {
        return chunkItemChallengeListener;
    }

    public VersionChecker getVersionChecker() {
        return versionChecker;
    }

    /**
     * Check if a reset is currently in progress
     */
    public boolean isResetInProgress() {
        return resetInProgress;
    }

    /**
     * Set the reset in progress flag
     */
    public void setResetInProgress(boolean inProgress) {
        this.resetInProgress = inProgress;
    }

    /**
     * Add a player to the reset list (they'll be reset on next join)
     */
    public void markPlayerForReset(String playerName) {
        playersToReset.add(playerName);
    }

    /**
     * Check if a player should be reset on join
     */
    public boolean shouldResetPlayer(String playerName) {
        return playersToReset.remove(playerName);
    }

    /**
     * Clear all players from the reset list
     */
    public void clearPlayersToReset() {
        playersToReset.clear();
    }

    /**
     * Load the speedrun world if it exists on disk but isn't loaded yet
     * This fixes the bug where the world isn't recognized after server restart
     */
    private void loadSpeedrunWorldIfExists() {
        String speedrunWorldName = getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);

        if (speedrunWorld != null) {
            getLogger().info("Speedrun world '" + speedrunWorldName + "' is already loaded.");
            return;
        }

        // Check if the world folder exists on disk
        File worldFolder = new File(Bukkit.getWorldContainer(), speedrunWorldName);
        if (!worldFolder.exists() || !worldFolder.isDirectory()) {
            getLogger().info("Speedrun world '" + speedrunWorldName
                    + "' does not exist on disk. Will be created on first /fullreset.");
            return;
        }

        // World exists on disk but isn't loaded - load it now
        getLogger().info("Found speedrun world '" + speedrunWorldName + "' on disk. Loading...");

        try {
            // Create world with default settings
            WorldCreator creator = new WorldCreator(speedrunWorldName);
            creator.environment(World.Environment.NORMAL);

            // Apply saved world generation settings if available
            Boolean savedStructures = dataManager.getSavedStructures();
            if (savedStructures != null) {
                creator.generateStructures(savedStructures);
            }

            World loadedWorld = creator.createWorld();

            if (loadedWorld != null) {
                getLogger().info("Successfully loaded speedrun world '" + speedrunWorldName + "'!");

                // Load Nether and End dimensions too
                WorldCreator netherCreator = new WorldCreator(speedrunWorldName + "_nether");
                netherCreator.environment(World.Environment.NETHER);
                World netherWorld = netherCreator.createWorld();
                if (netherWorld != null) {
                    getLogger().info("Successfully loaded Nether dimension!");
                }

                WorldCreator endCreator = new WorldCreator(speedrunWorldName + "_the_end");
                endCreator.environment(World.Environment.THE_END);
                World endWorld = endCreator.createWorld();
                if (endWorld != null) {
                    getLogger().info("Successfully loaded End dimension!");
                }

                // Disable spawn chunk loading to prevent lag
                loadedWorld.setKeepSpawnInMemory(false);
                if (netherWorld != null)
                    netherWorld.setKeepSpawnInMemory(false);
                if (endWorld != null)
                    endWorld.setKeepSpawnInMemory(false);
            } else {
                getLogger().warning("Failed to load speedrun world '" + speedrunWorldName + "'!");
            }
        } catch (Exception e) {
            getLogger().severe("Error loading speedrun world: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply gamerules to the waiting room world
     */
    private void applyWaitingRoomGameRules() {
        String waitingRoomName = getConfig().getString("world.waiting-room", "waiting_room");
        World waitingRoom = Bukkit.getWorld(waitingRoomName);

        if (waitingRoom == null) {
            getLogger().warning("Waiting room '" + waitingRoomName + "' not found. Gamerules not applied.");
            getLogger().warning("The waiting room will be configured when it's created.");
            return;
        }

        applyGameRulesToWorld(waitingRoom, "waiting-room");
    }

    /**
     * Apply saved gamerules to the speedrun world on server startup
     */
    private void applySpeedrunWorldSavedGameRules() {
        String speedrunWorldName = getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);

        if (speedrunWorld == null) {
            getLogger().info("Speedrun world '" + speedrunWorldName
                    + "' not found on startup. Gamerules will be applied when world is created.");
            return;
        }

        // Apply saved gamerules to ALL worlds (speedrun + nether + end)
        for (World world : Bukkit.getWorlds()) {
            // Skip the waiting room
            String waitingRoomName = getConfig().getString("world.waiting-room", "world");
            if (world.getName().equals(waitingRoomName)) {
                continue;
            }
            applySavedGamerulesToWorld(world);
        }
    }

    /**
     * Apply saved gamerules from data.yml to a world
     */
    public void applySavedGamerulesToWorld(World world) {
        java.util.Set<String> savedGameruleNames = dataManager.getSavedGameruleNames();

        if (savedGameruleNames.isEmpty()) {
            getLogger().info("No saved gamerules found for " + world.getName() + " - using Minecraft defaults");
        } else {
            getLogger().info("Applying " + savedGameruleNames.size() + " saved gamerules to " + world.getName());

            int applied = 0;
            for (String gameruleName : savedGameruleNames) {
                Object value = dataManager.getSavedGamerule(gameruleName);
                GameRule<?> gameRule = GameRule.getByName(gameruleName);

                if (gameRule == null) {
                    getLogger().warning("Unknown gamerule: " + gameruleName);
                    continue;
                }

                try {
                    if (value instanceof Boolean && gameRule.getType() == Boolean.class) {
                        @SuppressWarnings("unchecked")
                        GameRule<Boolean> boolRule = (GameRule<Boolean>) gameRule;
                        world.setGameRule(boolRule, (Boolean) value);
                        applied++;
                    } else if (value instanceof Integer && gameRule.getType() == Integer.class) {
                        @SuppressWarnings("unchecked")
                        GameRule<Integer> intRule = (GameRule<Integer>) gameRule;
                        world.setGameRule(intRule, (Integer) value);
                        applied++;
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to apply gamerule " + gameruleName + ": " + e.getMessage());
                }
            }

            getLogger().info("Applied " + applied + " saved gamerules to " + world.getName());
        }

        // Always set spectators_generate_chunks to false (not user-configurable)
        try {
            GameRule<?> spectatorsRule = GameRule.getByName("spectators_generate_chunks");
            if (spectatorsRule != null) {
                @SuppressWarnings("unchecked")
                GameRule<Boolean> boolRule = (GameRule<Boolean>) spectatorsRule;
                world.setGameRule(boolRule, false);
                getLogger().info("Set spectators_generate_chunks to false for " + world.getName());
            }
        } catch (Exception e) {
            getLogger().warning("Failed to set spectators_generate_chunks: " + e.getMessage());
        }
    }

    /**
     * Apply gamerules from config to a world
     */
    public void applyGameRulesToWorld(World world, String configPath) {
        String gamerulePath = "world.gamerules." + configPath;

        if (!getConfig().contains(gamerulePath)) {
            getLogger().info("No gamerules configured for: " + configPath);
            return;
        }

        getLogger().info("Applying gamerules to world: " + world.getName());

        ConfigurationSection gamerules = getConfig().getConfigurationSection(gamerulePath);
        if (gamerules == null)
            return;

        int applied = 0;
        for (String key : gamerules.getKeys(false)) {
            try {
                Object value = gamerules.get(key);
                GameRule<?> gameRule = GameRule.getByName(key);

                if (gameRule == null) {
                    getLogger().warning("Unknown gamerule: " + key);
                    continue;
                }

                // Apply the gamerule with proper type casting
                if (value instanceof Boolean) {
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> boolRule = (GameRule<Boolean>) gameRule;
                    world.setGameRule(boolRule, (Boolean) value);
                    applied++;
                } else if (value instanceof Integer) {
                    @SuppressWarnings("unchecked")
                    GameRule<Integer> intRule = (GameRule<Integer>) gameRule;
                    world.setGameRule(intRule, (Integer) value);
                    applied++;
                }

            } catch (Exception e) {
                getLogger().warning("Failed to apply gamerule '" + key + "': " + e.getMessage());
            }
        }

        getLogger().info("Applied " + applied + " gamerules to " + world.getName());
    }

    /**
     * Ensure the speedrun world has a safe spawn point on server startup
     * This prevents players from spawning in the air on first join
     */
    private void ensureSpeedrunWorldSafeSpawn() {
        String speedrunWorldName = getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);

        if (speedrunWorld == null) {
            getLogger().info("Speedrun world '" + speedrunWorldName
                    + "' not found on startup. Will be configured when created.");
            return;
        }

        Location currentSpawn = speedrunWorld.getSpawnLocation();
        getLogger().info("Checking speedrun world spawn safety at Y=" + currentSpawn.getBlockY());

        // Check if the current spawn is safe (solid ground below, air above)
        Location groundCheck = currentSpawn.clone().subtract(0, 1, 0);
        org.bukkit.Material groundMaterial = groundCheck.getBlock().getType();

        boolean needsAdjustment = false;

        // Check if spawn is in the air or on unsafe blocks
        if (!groundMaterial.isSolid() || isUnsafeSpawnMaterial(groundMaterial)) {
            getLogger().warning("Current spawn point is unsafe! Ground material: " + groundMaterial);
            needsAdjustment = true;
        }

        // Check if spawn Y is suspiciously high (common when spawn chunks haven't
        // generated)
        if (currentSpawn.getBlockY() > 200) {
            getLogger().warning("Spawn point is too high (Y=" + currentSpawn.getBlockY() + ")");
            needsAdjustment = true;
        }

        if (needsAdjustment) {
            getLogger().info("Finding safe spawn location...");
            Location safeSpawn = findSafeSpawn(speedrunWorld, currentSpawn);
            speedrunWorld.setSpawnLocation(safeSpawn);
            getLogger().info("✓ Set safe spawn at X=" + safeSpawn.getBlockX() + " Y=" + safeSpawn.getBlockY() + " Z="
                    + safeSpawn.getBlockZ());
        } else {
            getLogger().info("✓ Speedrun world spawn is safe at Y=" + currentSpawn.getBlockY());
        }
    }

    /**
     * Find a safe spawn location near the given starting point
     */
    private Location findSafeSpawn(World world, Location start) {
        int x = start.getBlockX();
        int z = start.getBlockZ();

        // Search in a spiral pattern
        for (int radius = 0; radius <= 100; radius += 5) {
            for (int dx = -radius; dx <= radius; dx += 5) {
                for (int dz = -radius; dz <= radius; dz += 5) {
                    int checkX = x + dx;
                    int checkZ = z + dz;

                    int surfaceY = world.getHighestBlockYAt(checkX, checkZ);

                    // Skip if too low or too high
                    if (surfaceY < 60 || surfaceY > 250)
                        continue;

                    Location surfaceLoc = new Location(world, checkX, surfaceY, checkZ);
                    org.bukkit.Material surfaceMat = surfaceLoc.getBlock().getType();

                    // Check if it's safe solid ground
                    if (surfaceMat.isSolid() && !isUnsafeSpawnMaterial(surfaceMat)) {
                        Location spawn = new Location(world, checkX + 0.5, surfaceY + 1, checkZ + 0.5);
                        spawn.setPitch(0);
                        spawn.setYaw(0);
                        return spawn;
                    }
                }
            }
        }

        // Fallback: use the highest block at the original location
        int surfaceY = world.getHighestBlockYAt(x, z);
        Location fallback = new Location(world, x + 0.5, surfaceY + 1, z + 0.5);
        fallback.setPitch(0);
        fallback.setYaw(0);
        getLogger().warning("Could not find ideal safe spawn, using fallback at Y=" + surfaceY);
        return fallback;
    }

    /**
     * Check if a material is unsafe for spawning
     */
    private boolean isUnsafeSpawnMaterial(org.bukkit.Material material) {
        String name = material.name();
        return material == org.bukkit.Material.WATER ||
                material == org.bukkit.Material.LAVA ||
                material == org.bukkit.Material.AIR ||
                name.contains("WATER") ||
                name.contains("LAVA") ||
                name.contains("LEAVES") ||
                name.contains("ICE") ||
                name.equals("SNOW") ||
                name.equals("POWDER_SNOW");
    }

    /**
     * Optimize world settings to prevent server lag
     * Disable spawn chunk loading for all worlds
     */
    private void optimizeWorldSettings() {
        for (World world : Bukkit.getWorlds()) {
            world.setKeepSpawnInMemory(false);
            getLogger().info("Disabled spawn chunk loading for: " + world.getName());
        }
    }

    /**
     * Check if there's a pending seed from a previous fullreset and apply it to
     * server.properties
     */
    private void checkAndApplyPendingSeed() {
        File seedFile = new File(getDataFolder(), "pending_seed.txt");
        if (seedFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(seedFile.toPath());
                if (lines.isEmpty())
                    return;

                String seedStr = lines.get(0).trim();
                long seed = Long.parseLong(seedStr);

                getLogger().info("Found pending seed: " + seed);
                getLogger().info("Applying seed to server.properties...");

                // Update server.properties
                File serverProperties = new File("server.properties");
                if (serverProperties.exists()) {
                    List<String> propLines = Files.readAllLines(serverProperties.toPath());
                    List<String> newLines = new ArrayList<>();
                    boolean seedFound = false;

                    for (String line : propLines) {
                        if (line.startsWith("level-seed=")) {
                            newLines.add("level-seed=" + seed);
                            seedFound = true;
                            getLogger().info("Updated level-seed in server.properties");
                        } else {
                            newLines.add(line);
                        }
                    }

                    if (!seedFound) {
                        newLines.add("level-seed=" + seed);
                        getLogger().info("Added level-seed to server.properties");
                    }

                    Files.write(serverProperties.toPath(), newLines);
                    getLogger().info("server.properties updated with new seed!");
                }

                // Delete the pending seed file
                seedFile.delete();
                getLogger().info("Pending seed file removed.");

            } catch (Exception e) {
                getLogger().severe("Failed to apply pending seed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
