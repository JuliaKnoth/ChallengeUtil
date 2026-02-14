package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.HostControlGUI;
import de.connunity.util.challenge.gui.SettingsGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles clicks in the Settings GUI
 */
public class SettingsGUIListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public SettingsGUIListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();
        
        // Check if it's the settings GUI or confirmation GUI
        String title = PlainTextComponentSerializer.plainText().serialize(view.title());
        
        // Get titles from language manager
        String settingsTitle = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("gui.settings.title"));
        String confirmTitle = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("gui.settings.reset-confirm-title"));
        String gamerulesTitle = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("gui.settings.gamerules-title"));
        String challengesTitle = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("gui.settings.challenges-title"));
        
        if (!title.equals(settingsTitle) && 
            !title.equals(confirmTitle) && 
            !title.equals(gamerulesTitle) && 
            !title.equals(challengesTitle)) {
            return;
        }
        
        // Cancel all clicks in the GUI
        event.setCancelled(true);
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.closeInventory();
            player.sendMessage(lang.getComponent("host.reset-in-progress"));
            player.sendMessage(lang.getComponent("host.reset-wait"));
            return;
        }
        
        // Get clicked slot
        int slot = event.getRawSlot();
        
        // Only process clicks in the GUI inventory (not player inventory)
        if (slot < 0 || slot >= view.getTopInventory().getSize()) {
            return;
        }
        
        // Handle confirmation GUI
        if (title.equals(confirmTitle)) {
            handleConfirmationClick(player, slot);
            return;
        }
        
        // Handle gamerules GUI
        if (title.equals(gamerulesTitle)) {
            // Check for back button first
            if (slot == 49) {
                SettingsGUI gui = new SettingsGUI(plugin);
                gui.open(player);
                return;
            }
            // Handle all other gamerule clicks
            boolean isRightClick = event.getClick().isRightClick();
            boolean isShiftClick = event.getClick().isShiftClick();
            handleGamerulesClick(player, slot, isRightClick, isShiftClick);
            return;
        }
        
        // Handle challenges GUI
        if (title.equals(challengesTitle)) {
            // Check for back button first
            if (slot == 49) {
                SettingsGUI gui = new SettingsGUI(plugin);
                gui.open(player);
                return;
            }
            // Handle challenge clicks
            boolean isRightClick = event.getClick().isRightClick();
            boolean isShiftClick = event.getClick().isShiftClick();
            boolean isDropKey = event.getClick().toString().contains("DROP");
            handleChallengesClick(player, slot, isRightClick, isShiftClick, isDropKey);
            return;
        }
        
        // Handle settings GUI
        switch (slot) {
            case 11: // Structures
                handleStructuresClick(player);
                break;
            case 13: // Gamerules submenu
                handleGamerulesMenuClick(player);
                break;
            case 15: // Challenges submenu
                handleChallengesMenuClick(player);
                break;
            case 27: // Back button
                handleBackButtonClick(player);
                break;
            case 35: // Reset settings button
                handleResetButtonClick(player);
                break;
            default:
                // Ignore clicks on other slots
                break;
        }
    }
    
    /**
     * Handle clicks in the confirmation GUI
     */
    private void handleConfirmationClick(Player player, int slot) {
        if (slot == 11) {
            // Confirm - reset settings
            plugin.getDataManager().clearSettings();
            
            // Also update config to defaults
            plugin.getConfig().set("world.difficulty", "NORMAL");
            plugin.getConfig().set("world.generation.generate-structures", true);
            plugin.getConfig().set("challenge.allow-respawn", true);
            plugin.saveConfig();
            
            player.sendMessage(lang.getComponent("settings.reset-to-default"));
            player.closeInventory();
            
        } else if (slot == 15) {
            // Cancel - go back to settings
            player.sendMessage(lang.getComponent("settings.reset-cancelled"));
            SettingsGUI gui = new SettingsGUI(plugin);
            gui.open(player);
        }
    }
    
    /**
     * Handle reset button click
     */
    private void handleResetButtonClick(Player player) {
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openResetConfirmation(player);
    }
    
    /**
     * Handle back button click
     */
    private void handleBackButtonClick(Player player) {
        // Check if player has host permission
        if (!player.hasPermission("challenge.host")) {
            player.closeInventory();
            player.sendMessage(lang.getComponent("host.no-permission-gui"));
            return;
        }
        
        // Open the host control GUI
        HostControlGUI hostGUI = new HostControlGUI(plugin);
        hostGUI.open(player);
    }
    
    /**
     * Handle gamerules menu button click
     */
    private void handleGamerulesMenuClick(Player player) {
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openGamerulesMenu(player);
    }
    
    /**
     * Handle challenges menu button click
     */
    private void handleChallengesMenuClick(Player player) {
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openChallengesMenu(player);
    }
    
    /**
     * Handle difficulty setting click
     */
    private void handleDifficultyClick(Player player) {
        handleDifficultyClick(player, false);
    }
    
    /**
     * Handle difficulty setting click
     * @param fromGamerules whether this was clicked from the gamerules menu
     */
    private void handleDifficultyClick(Player player, boolean fromGamerules) {
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        // Load current setting from persistent data or world
        String savedDifficulty = plugin.getDataManager().getSavedDifficulty();
        Difficulty current;
        
        if (savedDifficulty != null) {
            try {
                current = Difficulty.valueOf(savedDifficulty);
            } catch (IllegalArgumentException e) {
                current = speedrunWorld != null ? speedrunWorld.getDifficulty() : Difficulty.NORMAL;
            }
        } else {
            current = speedrunWorld != null ? speedrunWorld.getDifficulty() : Difficulty.NORMAL;
        }
        
        Difficulty next;
        
        // Cycle through difficulties
        switch (current) {
            case PEACEFUL:
                next = Difficulty.EASY;
                break;
            case EASY:
                next = Difficulty.NORMAL;
                break;
            case NORMAL:
                next = Difficulty.HARD;
                break;
            case HARD:
                next = Difficulty.PEACEFUL;
                break;
            default:
                next = Difficulty.NORMAL;
        }
        
        // Apply immediately to the world if it exists
        if (speedrunWorld != null) {
            speedrunWorld.setDifficulty(next);
        }
        
        // Get current structures and respawn settings
        Boolean savedStructures = plugin.getDataManager().getSavedStructures();
        Boolean savedRespawn = plugin.getDataManager().getSavedRespawn();
        boolean structures = savedStructures != null ? savedStructures : plugin.getConfig().getBoolean("world.generation.generate-structures", true);
        boolean respawn = savedRespawn != null ? savedRespawn : plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        // Save all settings persistently
        plugin.getDataManager().saveChallengeSettings(next.name(), structures, respawn);
        
        // Also update config
        plugin.getConfig().set("world.difficulty", next.name());
        plugin.saveConfig();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("difficulty", next.name());
        player.sendMessage(lang.getComponent("settings.difficulty-set", placeholders));
        player.sendMessage(lang.getComponent("settings.difficulty-applied"));
        
        // Refresh GUI - stay in gamerules menu if clicked from there
        SettingsGUI gui = new SettingsGUI(plugin);
        if (fromGamerules) {
            gui.openGamerulesMenu(player);
        } else {
            gui.open(player);
        }
    }
    
    /**
     * Handle structures setting click
     */
    private void handleStructuresClick(Player player) {
        // Load current setting from persistent data
        Boolean savedStructures = plugin.getDataManager().getSavedStructures();
        boolean current = savedStructures != null ? savedStructures : plugin.getConfig().getBoolean("world.generation.generate-structures", true);
        boolean newValue = !current;
        
        // Get current difficulty and respawn settings
        String savedDifficulty = plugin.getDataManager().getSavedDifficulty();
        Boolean savedRespawn = plugin.getDataManager().getSavedRespawn();
        String difficulty = savedDifficulty != null ? savedDifficulty : plugin.getConfig().getString("world.difficulty", "NORMAL");
        boolean respawn = savedRespawn != null ? savedRespawn : plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        // Save all settings persistently
        plugin.getDataManager().saveChallengeSettings(difficulty, newValue, respawn);
        
        // Also update config
        plugin.getConfig().set("world.generation.generate-structures", newValue);
        plugin.saveConfig();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("status", newValue ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
        player.sendMessage(lang.getComponent("settings.structures-toggled", placeholders));
        player.sendMessage(lang.getComponent("settings.structures-saved"));
        
        // Refresh GUI
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.open(player);
    }
    
    /**
     * Handle respawn setting click
     */
    private void handleRespawnClick(Player player) {
        // Load current setting from persistent data
        Boolean savedRespawn = plugin.getDataManager().getSavedRespawn();
        boolean current = savedRespawn != null ? savedRespawn : plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        boolean newValue = !current;
        
        // Get current difficulty and structures settings
        String savedDifficulty = plugin.getDataManager().getSavedDifficulty();
        Boolean savedStructures = plugin.getDataManager().getSavedStructures();
        String difficulty = savedDifficulty != null ? savedDifficulty : plugin.getConfig().getString("world.difficulty", "NORMAL");
        boolean structures = savedStructures != null ? savedStructures : plugin.getConfig().getBoolean("world.generation.generate-structures", true);
        
        // Save all settings persistently
        plugin.getDataManager().saveChallengeSettings(difficulty, structures, newValue);
        
        // Also update config
        plugin.getConfig().set("challenge.allow-respawn", newValue);
        plugin.saveConfig();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("status", newValue ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
        player.sendMessage(lang.getComponent("settings.respawn-toggled", placeholders));

        if (newValue) {
            player.sendMessage(lang.getComponent("settings.respawn-enabled-desc"));
        } else {
            player.sendMessage(lang.getComponent("settings.respawn-disabled-desc"));
        }

        player.sendMessage(lang.getComponent("settings.respawn-applied"));
        
        // Refresh gamerules GUI
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openGamerulesMenu(player);
    }
    
    /**
     * Handle individual gamerule click
     */
    private void handleGamerulesClick(Player player, int slot, boolean isRightClick, boolean isShiftClick) {
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        // Note: world might be null if it hasn't been created yet - that's OK, we save to data.yml
        
        // Map slots to gamerule names (using underscore format)
        String gameruleName = null;
        switch (slot) {
            // Row 0: Common gameplay rules (top row)
            case 1: gameruleName = "keep_inventory"; break;
            case 2: gameruleName = "natural_health_regeneration"; break;
            case 3: gameruleName = "immediate_respawn"; break;
            case 4: gameruleName = "players_sleeping_percentage"; break; // integer
            case 5: gameruleName = "locator_bar"; break;
            
            // Row 1: Mob spawning rules
            case 10: gameruleName = "spawn_mobs"; break;
            case 11: gameruleName = "spawn_monsters"; break;
            case 12: gameruleName = "spawn_patrols"; break;
            case 13: gameruleName = "spawn_wandering_traders"; break;
            case 14: gameruleName = "spawn_wardens"; break;
            case 15: gameruleName = "spawner_blocks_work"; break;
            case 16: gameruleName = "mob_griefing"; break;
            
            // Row 2: Environmental rules
            case 19: gameruleName = "advance_time"; break;
            case 20: gameruleName = "advance_weather"; break;
            case 21: gameruleName = "random_tick_speed"; break; // integer
            case 22: gameruleName = "tnt_explodes"; break;
            case 23: gameruleName = "raids"; break;
            case 24: gameruleName = "forgive_dead_players"; break;
            
            // Row 3: Damage rules
            case 28: gameruleName = "fall_damage"; break;
            case 29: gameruleName = "fire_damage"; break;
            case 30: gameruleName = "drowning_damage"; break;
            case 31: gameruleName = "freeze_damage"; break;
            
            // Row 4: Drop rules
            case 37: gameruleName = "block_drops"; break;
            case 38: gameruleName = "entity_drops"; break;
            case 39: gameruleName = "mob_drops"; break;
            case 40: gameruleName = "limited_crafting"; break;
            
            // Row 5: Respawn/player settings
            case 42: // Difficulty (special case - not a gamerule)
                handleDifficultyClick(player, true);
                return;
            case 43: // Allow Respawn (special case - not a gamerule)
                handleRespawnClick(player);
                return;
            
            default:
                return; // Invalid slot
        }
        
        // Validate gamerule name
        if (gameruleName == null) {
            player.sendMessage(lang.getComponent("settings.gamerule-unknown"));
            return;
        }
        
        // Get the GameRule object by name
        GameRule<?> gameRule = GameRule.getByName(gameruleName);
        if (gameRule == null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("gamerule", gameruleName);
            player.sendMessage(lang.getComponent("settings.gamerule-unavailable", placeholders));
            return;
        }
        
        // Determine gamerule type (check if it's an integer gamerule)
        boolean isIntegerRule = gameruleName.equals("random_tick_speed") || gameruleName.equals("players_sleeping_percentage");
        
        // Handle boolean gamerules
        if (!isIntegerRule) {
            // Get current value from saved data or world
            Boolean currentValue;
            Object savedValue = plugin.getDataManager().getSavedGamerule(gameruleName);
            if (savedValue != null) {
                currentValue = (Boolean) savedValue;
            } else if (speedrunWorld != null) {
                @SuppressWarnings("unchecked")
                GameRule<Boolean> boolRule = (GameRule<Boolean>) gameRule;
                currentValue = speedrunWorld.getGameRuleValue(boolRule);
            } else {
                currentValue = getDefaultBooleanValue(gameruleName);
            }
            
            boolean newValue = currentValue == null ? true : !currentValue;
            
            // Prevent disabling keepInventory if Keep RNG is enabled
            if (gameruleName.equals("keep_inventory") && !newValue) {
                Boolean keepRNGEnabled = plugin.getDataManager().getSavedChallenge("keep_rng");
                if (keepRNGEnabled != null && keepRNGEnabled) {
                    player.sendMessage(lang.getComponent("settings.keep-inventory-locked"));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
            
            // Apply to ALL worlds immediately
            for (World world : Bukkit.getWorlds()) {
                try {
                    @SuppressWarnings("unchecked")
                    GameRule<Boolean> boolRule = (GameRule<Boolean>) gameRule;
                    world.setGameRule(boolRule, newValue);
                } catch (Exception e) {
                    // Gamerule might not be compatible with this world type
                }
            }
            
            // Save persistently
            plugin.getDataManager().saveGamerule(gameruleName, newValue);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("gamerule", formatGameruleName(gameruleName));
            placeholders.put("value", newValue ? lang.getMessage("common.on") : lang.getMessage("common.off"));
            player.sendMessage(lang.getComponent("settings.gamerule-set", placeholders));
        }
        // Handle integer gamerules
        else {
            // Get current value from saved data or world
            Integer currentValue;
            Object savedValue = plugin.getDataManager().getSavedGamerule(gameruleName);
            if (savedValue != null) {
                currentValue = (Integer) savedValue;
            } else if (speedrunWorld != null) {
                @SuppressWarnings("unchecked")
                GameRule<Integer> intRule = (GameRule<Integer>) gameRule;
                currentValue = speedrunWorld.getGameRuleValue(intRule);
            } else {
                // Default values
                if (gameruleName.equals("random_tick_speed")) {
                    currentValue = 3;
                } else if (gameruleName.equals("players_sleeping_percentage")) {
                    currentValue = 100;
                } else {
                    currentValue = 3;
                }
            }
            
            int current = currentValue == null ? 3 : currentValue;
            
            int change;
            if (isShiftClick) {
                change = isRightClick ? -10 : 10; // Shift+Right = -10, Shift+Left = +10
            } else {
                change = isRightClick ? -1 : 1;   // Right = -1, Left = +1
            }
            
            int newValue = Math.max(0, current + change); // Don't allow negative
            
            // For players_sleeping_percentage, clamp to 1-100
            if (gameruleName.equals("players_sleeping_percentage")) {
                newValue = Math.max(1, Math.min(100, newValue));
            }
            
            // Apply to ALL worlds immediately
            for (World world : Bukkit.getWorlds()) {
                try {
                    @SuppressWarnings("unchecked")
                    GameRule<Integer> intRule = (GameRule<Integer>) gameRule;
                    world.setGameRule(intRule, newValue);
                } catch (Exception e) {
                    // Gamerule might not be compatible with this world type
                }
            }
            
            // Save persistently
            plugin.getDataManager().saveGamerule(gameruleName, newValue);
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("gamerule", formatGameruleName(gameruleName));
            placeholders.put("value", String.valueOf(newValue));
            player.sendMessage(lang.getComponent("settings.gamerule-set", placeholders));
        }
        
        // Refresh GUI
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openGamerulesMenu(player);
    }
    
    /**
     * Get default boolean value for a gamerule
     */
    private boolean getDefaultBooleanValue(String gameruleName) {
        switch (gameruleName) {
            case "immediate_respawn":
            case "keep_inventory":
            case "limited_crafting":
            case "locator_bar":
            case "universal_anger":
                return false;
            default:
                return true;
        }
    }
    
    /**
     * Format gamerule name from underscore_case to Title Case
     */
    private String formatGameruleName(String gameruleName) {
        // Convert underscore_case to Title Case with spaces
        String[] parts = gameruleName.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String part = parts[i].toLowerCase();
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
    
    /**
     * Handle individual challenge click
     */
    private void handleChallengesClick(Player player, int slot, boolean isRightClick, boolean isShiftClick, boolean isDropKey) {
        // Map slots to challenge names
        String challengeName = null;
        switch (slot) {
            // Row 0: Team Modes (top row)
            case 1: challengeName = "manhunt_mode"; break;
            case 2: challengeName = "team_race_mode"; break;
            case 3: challengeName = "custom_end_fight"; break;
            
            // Row 2: RNG based stuff
            case 19: challengeName = "chunk_items"; break;
            case 20: challengeName = "timed_random_item"; break;
            case 21: challengeName = "friendly_fire_item"; break;
            case 22: challengeName = "block_break_randomizer"; break;
            
            // Row 3: Player stuff
            case 28: challengeName = "keep_rng"; break;
            
            default:
                return; // Invalid slot
        }
        
        // Validate challenge name
        if (challengeName == null) {
            return;
        }
        
        // Special handling for keep_rng percentage adjustment
        if (challengeName.equals("keep_rng")) {
            // Get current percentage
            Integer currentPercentage = plugin.getDataManager().getSavedChallengeSetting("keep_rng", "percentage");
            if (currentPercentage == null) {
                currentPercentage = 50; // Default
            }
            
            int newPercentage;
            if (isRightClick) {
                // Right-click: decrease by 10%
                newPercentage = Math.max(0, currentPercentage - 10);
            } else if (isDropKey) {
                // Ignore drop key
                return;
            } else {
                // Left-click: increase by 10%
                newPercentage = Math.min(100, currentPercentage + 10);
            }
            
            // Save the new percentage
            plugin.getDataManager().saveChallengeSetting("keep_rng", "percentage", newPercentage);
            
            // Automatically toggle challenge based on percentage
            boolean newEnabled = newPercentage > 0;
            plugin.getDataManager().saveChallenge("keep_rng", newEnabled);
            
            // Force keepInventory to true when Keep RNG is enabled
            if (newEnabled) {
                String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
                org.bukkit.World speedrunWorld = org.bukkit.Bukkit.getWorld(speedrunWorldName);
                if (speedrunWorld != null) {
                    org.bukkit.GameRule<Boolean> keepInventoryRule = org.bukkit.GameRule.KEEP_INVENTORY;
                    speedrunWorld.setGameRule(keepInventoryRule, true);
                    plugin.getDataManager().saveGamerule("keep_inventory", true);
                    
                    player.sendMessage(lang.getComponent("settings.keep-inventory-auto-enabled"));
                }
            }
            
            // Send feedback message
            if (newPercentage == 0) {
                player.sendMessage(net.kyori.adventure.text.Component.text("Keep RNG disabled (0%)", net.kyori.adventure.text.format.NamedTextColor.RED));
            } else {
                player.sendMessage(net.kyori.adventure.text.Component.text("Keep RNG percentage: ", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                    .append(net.kyori.adventure.text.Component.text(newPercentage + "%", net.kyori.adventure.text.format.NamedTextColor.YELLOW, net.kyori.adventure.text.format.TextDecoration.BOLD)));
            }
            
            // Refresh GUI
            SettingsGUI gui = new SettingsGUI(plugin);
            gui.openChallengesMenu(player);
            return;
        }
        
        // Get current value from saved data
        Boolean currentValue = plugin.getDataManager().getSavedChallenge(challengeName);
        if (currentValue == null) {
            currentValue = false; // Default to disabled
        }
        
        boolean newValue = !currentValue;
        
        // MUTUAL EXCLUSIVITY: Manhunt and Team Race cannot both be enabled
        if (newValue) { // Only check if we're enabling a mode
            if (challengeName.equals("manhunt_mode")) {
                // If enabling Manhunt, disable Team Race
                Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
                if (teamRaceEnabled != null && teamRaceEnabled) {
                    plugin.getDataManager().saveChallenge("team_race_mode", false);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("mode", "Manhunt Race");
                    player.sendMessage(lang.getComponent("settings.mode-switched-warning", placeholders));
                }
                // Reset teams when switching to Manhunt mode
                plugin.getDataManager().clearTeams();
                player.sendMessage(lang.getComponent("settings.teams-reset"));
            } else if (challengeName.equals("team_race_mode")) {
                // If enabling Team Race, disable Manhunt
                Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
                if (manhuntEnabled != null && manhuntEnabled) {
                    plugin.getDataManager().saveChallenge("manhunt_mode", false);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("mode", "Manhunt");
                    player.sendMessage(lang.getComponent("settings.mode-switched-warning", placeholders));
                }
                // Reset teams when switching to Team Race mode
                plugin.getDataManager().clearTeams();
                player.sendMessage(lang.getComponent("settings.teams-reset"));
            }
        } else {
            // When disabling a mode, also reset teams
            if (challengeName.equals("manhunt_mode") || challengeName.equals("team_race_mode")) {
                plugin.getDataManager().clearTeams();
                player.sendMessage(lang.getComponent("settings.teams-reset"));
            }
        }
        
        // Save persistently
        plugin.getDataManager().saveChallenge(challengeName, newValue);
        
        // Special hidden message for custom_end_fight
        if (challengeName.equals("custom_end_fight")) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Custom End Fight: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(newValue ? "ON" : "OFF", 
                    newValue ? net.kyori.adventure.text.format.NamedTextColor.GREEN : net.kyori.adventure.text.format.NamedTextColor.RED)));
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("challenge", formatChallengeName(challengeName));
            placeholders.put("status", newValue ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
            player.sendMessage(lang.getComponent("settings.challenge-toggled", placeholders));
            player.sendMessage(lang.getComponent("settings.challenge-saved"));
        }
        
        // Refresh GUI
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.openChallengesMenu(player);
    }
    
    /**
     * Format challenge name from underscore_case to Title Case
     */
    private String formatChallengeName(String challengeName) {
        // Convert underscore_case to Title Case with spaces
        String[] parts = challengeName.split("_");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String part = parts[i].toLowerCase();
            if (part.length() > 0) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
}
