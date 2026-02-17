package de.connunity.util.challenge.data;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Manages persistent data for timer and world state
 */
public class DataManager {
    
    private final Plugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    public DataManager(Plugin plugin) {
        this.plugin = plugin;
        loadData();
    }
    
    /**
     * Load or create the data file
     */
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                ((ChallengeUtil) plugin).logWarning("Failed to create data.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Initialize default gamerules if not present
        initializeDefaultGamerules();
    }
    
    /**
     * Reload data from disk (used after full reset to ensure no stale data)
     */
    public void reloadData() {
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ((ChallengeUtil) plugin).logDebug("Data configuration reloaded from disk");
    }
    
    /**
     * Initialize default gamerules from config.yml if data.yml has no gamerules
     */
    private void initializeDefaultGamerules() {
        // Only initialize if no gamerules are saved yet
        if (hasGamerules()) {
            return;
        }
        
        ((ChallengeUtil) plugin).logDebug("Initializing default gamerules in data.yml...");
        
        // Get speedrun world gamerules from config
        org.bukkit.configuration.ConfigurationSection speedrunGamerules = 
            plugin.getConfig().getConfigurationSection("world.gamerules.speedrun-world");
        
        if (speedrunGamerules != null) {
            for (String key : speedrunGamerules.getKeys(false)) {
                Object value = speedrunGamerules.get(key);
                if (value instanceof Boolean) {
                    saveGamerule(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    saveGamerule(key, (Integer) value);
                }
            }
            ((ChallengeUtil) plugin).logDebug("Initialized " + speedrunGamerules.getKeys(false).size() + " default gamerules");
        }
    }
    
    /**
     * Save data to file
     */
    public void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            ((ChallengeUtil) plugin).logWarning("Failed to save data.yml: " + e.getMessage());
        }
    }
    
    /**
     * Save timer state
     */
    public void saveTimerState(long totalSeconds, boolean running, boolean paused) {
        dataConfig.set("timer.total-seconds", totalSeconds);
        dataConfig.set("timer.running", running);
        dataConfig.set("timer.paused", paused);
        dataConfig.set("timer.last-saved", System.currentTimeMillis());
        save();
    }
    
    /**
     * Get saved timer seconds
     */
    public long getTimerSeconds() {
        return dataConfig.getLong("timer.total-seconds", 0);
    }
    
    /**
     * Check if timer was running
     */
    public boolean wasTimerRunning() {
        return dataConfig.getBoolean("timer.running", false);
    }
    
    /**
     * Check if timer was paused
     */
    public boolean wasTimerPaused() {
        return dataConfig.getBoolean("timer.paused", false);
    }
    
    /**
     * Save current world seed
     */
    public void saveWorldSeed(long seed) {
        dataConfig.set("world.current-seed", seed);
        save();
    }
    
    /**
     * Get current world seed (returns 0 if never set)
     */
    public long getWorldSeed() {
        return dataConfig.getLong("world.current-seed", 0);
    }
    
    /**
     * Check if world exists (hasn't been fully reset)
     */
    public boolean hasWorldData() {
        return dataConfig.contains("world.current-seed");
    }
    
    /**
     * Clear all timer data (called on full reset)
     */
    public void clearTimerData() {
        dataConfig.set("timer", null);
        save();
    }
    
    /**
     * Clear all world data (called on full reset)
     */
    public void clearWorldData() {
        dataConfig.set("world", null);
        save();
    }
    
    /**
     * Save challenge settings (persistent across resets)
     */
    public void saveChallengeSettings(String difficulty, boolean structures, boolean allowRespawn) {
        dataConfig.set("settings.difficulty", difficulty);
        dataConfig.set("settings.generate-structures", structures);
        dataConfig.set("settings.allow-respawn", allowRespawn);
        dataConfig.set("settings.last-modified", System.currentTimeMillis());
        save();
    }
    
    /**
     * Get saved difficulty setting
     */
    public String getSavedDifficulty() {
        return dataConfig.getString("settings.difficulty");
    }
    
    /**
     * Get saved structures setting
     */
    public Boolean getSavedStructures() {
        if (!dataConfig.contains("settings.generate-structures")) {
            return null;
        }
        return dataConfig.getBoolean("settings.generate-structures");
    }
    
    /**
     * Get saved respawn setting
     */
    public Boolean getSavedRespawn() {
        if (!dataConfig.contains("settings.allow-respawn")) {
            return null;
        }
        return dataConfig.getBoolean("settings.allow-respawn");
    }
    
    /**
     * Check if settings have been saved before
     */
    public boolean hasSettings() {
        return dataConfig.contains("settings");
    }
    
    /**
     * Save a gamerule value (boolean)
     */
    public void saveGamerule(String gameruleName, boolean value) {
        dataConfig.set("gamerules." + gameruleName, value);
        save();
    }
    
    /**
     * Save a gamerule value (integer)
     */
    public void saveGamerule(String gameruleName, int value) {
        dataConfig.set("gamerules." + gameruleName, value);
        save();
    }
    
    /**
     * Get saved gamerule value (returns null if not set)
     */
    public Object getSavedGamerule(String gameruleName) {
        if (!dataConfig.contains("gamerules." + gameruleName)) {
            return null;
        }
        return dataConfig.get("gamerules." + gameruleName);
    }
    
    /**
     * Check if any gamerules have been saved
     */
    public boolean hasGamerules() {
        return dataConfig.contains("gamerules");
    }
    
    /**
     * Get all saved gamerule names
     */
    public java.util.Set<String> getSavedGameruleNames() {
        if (!dataConfig.contains("gamerules")) {
            return new java.util.HashSet<>();
        }
        return dataConfig.getConfigurationSection("gamerules").getKeys(false);
    }
    
    /**
     * Clear saved settings (restore to defaults)
     */
    public void clearSettings() {
        dataConfig.set("settings", null);
        dataConfig.set("gamerules", null);
        dataConfig.set("challenges", null);
        dataConfig.set("challenge-settings", null);
        save();
    }
    
    /**
     * Save a challenge value (boolean)
     */
    public void saveChallenge(String challengeName, boolean value) {
        dataConfig.set("challenges." + challengeName, value);
        save();
    }
    
    /**
     * Save a challenge setting value (integer)
     */
    public void saveChallengeSetting(String challengeName, String settingName, int value) {
        dataConfig.set("challenge-settings." + challengeName + "." + settingName, value);
        save();
    }
    
    /**
     * Get saved challenge value (returns null if not set)
     */
    public Boolean getSavedChallenge(String challengeName) {
        if (!dataConfig.contains("challenges." + challengeName)) {
            return null;
        }
        return dataConfig.getBoolean("challenges." + challengeName);
    }
    
    /**
     * Get saved challenge setting value (returns null if not set)
     */
    public Integer getSavedChallengeSetting(String challengeName, String settingName) {
        if (!dataConfig.contains("challenge-settings." + challengeName + "." + settingName)) {
            return null;
        }
        return dataConfig.getInt("challenge-settings." + challengeName + "." + settingName);
    }
    
    /**
     * Check if any challenges have been saved
     */
    public boolean hasChallenges() {
        return dataConfig.contains("challenges");
    }
    
    /**
     * Get all saved challenge names
     */
    public java.util.Set<String> getSavedChallengeNames() {
        if (!dataConfig.contains("challenges")) {
            return new java.util.HashSet<>();
        }
        return dataConfig.getConfigurationSection("challenges").getKeys(false);
    }
    
    /**
     * Set a player's team for manhunt mode
     */
    public void setPlayerTeam(java.util.UUID playerId, String team) {
        dataConfig.set("teams." + playerId.toString(), team);
        save();
    }
    
    /**
     * Get a player's team (returns null if not set)
     */
    public String getPlayerTeam(java.util.UUID playerId) {
        return dataConfig.getString("teams." + playerId.toString());
    }
    
    /**
     * Remove a player from their team
     */
    public void removePlayerTeam(java.util.UUID playerId) {
        dataConfig.set("teams." + playerId.toString(), null);
        save();
    }
    
    /**
     * Get all players in a specific team
     */
    public java.util.Set<java.util.UUID> getPlayersInTeam(String team) {
        java.util.Set<java.util.UUID> players = new java.util.HashSet<>();
        if (!dataConfig.contains("teams")) {
            return players;
        }
        
        org.bukkit.configuration.ConfigurationSection teamsSection = dataConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return players;
        }
        
        for (String uuidString : teamsSection.getKeys(false)) {
            String playerTeam = teamsSection.getString(uuidString);
            if (team.equals(playerTeam)) {
                try {
                    players.add(java.util.UUID.fromString(uuidString));
                } catch (IllegalArgumentException e) {
                    // Invalid UUID, skip
                }
            }
        }
        
        return players;
    }
    
    /**
     * Clear all team data
     */
    public void clearTeams() {
        dataConfig.set("teams", null);
        save();
    }
    
    /**
     * Clear teams that have no online members
     * This is used when starting a new match to remove teams from previous matches
     */
    public void clearEmptyTeams() {
        if (!dataConfig.contains("teams")) {
            return;
        }
        
        org.bukkit.configuration.ConfigurationSection teamsSection = dataConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return;
        }
        
        java.util.List<String> uuidsToRemove = new java.util.ArrayList<>();
        
        // Find all UUIDs where the player is not online
        for (String uuidString : teamsSection.getKeys(false)) {
            try {
                java.util.UUID playerId = java.util.UUID.fromString(uuidString);
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerId);
                
                // If player is not online, mark them for removal
                if (player == null || !player.isOnline()) {
                    uuidsToRemove.add(uuidString);
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID, remove it
                uuidsToRemove.add(uuidString);
            }
        }
        
        // Remove all offline players from teams
        for (String uuidString : uuidsToRemove) {
            dataConfig.set("teams." + uuidString, null);
        }
        
        if (!uuidsToRemove.isEmpty()) {
            save();
        }
    }
    
    /**
     * Increment kill count for a player in Team Race mode
     */
    public void incrementTeamRaceKills(java.util.UUID playerId) {
        int currentKills = getTeamRaceKills(playerId);
        dataConfig.set("teamrace.kills." + playerId.toString(), currentKills + 1);
        save();
    }
    
    /**
     * Get kill count for a player in Team Race mode
     */
    public int getTeamRaceKills(java.util.UUID playerId) {
        return dataConfig.getInt("teamrace.kills." + playerId.toString(), 0);
    }
    
    /**
     * Clear all Team Race kill data
     */
    public void clearTeamRaceKills() {
        dataConfig.set("teamrace.kills", null);
        save();
    }
    
    /**
     * Clear all data (called on full reset)
     * NOTE: This does NOT clear teams - teams persist until explicitly reset
     * NOTE: This does NOT clear settings/gamerules/challenges - those persist across resets
     */
    public void clearAllData() {
        // Clear timer data
        dataConfig.set("timer", null);
        
        // Clear world data (including seed)
        dataConfig.set("world", null);
        
        // Clear team race data
        dataConfig.set("teamrace", null);
        
        // Clear speedrun milestones (nether/end tracking)
        // This is critical to ensure nether and end portal data is reset
        dataConfig.set("speedrun", null);
        dataConfig.set("speedrun.nether-entered", null);
        dataConfig.set("speedrun.end-entered", null);
        dataConfig.set("speedrun.first-nether-time", null);
        dataConfig.set("speedrun.first-end-time", null);
        
        // Deliberately NOT clearing: teams, settings, gamerules, challenges
        
        // Force immediate save to disk to ensure data is cleared before world regeneration
        save();
        
        // Reload from disk to ensure in-memory config matches disk state
        reloadData();
        
        ((ChallengeUtil) plugin).logDebug("All speedrun data cleared and reloaded (nether/end milestones reset)");
    }
}
