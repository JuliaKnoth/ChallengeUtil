package de.connunity.util.challenge.gui;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings GUI for configuring challenge settings
 */
public class SettingsGUI {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public SettingsGUI(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Open the settings GUI for a player
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, lang.getComponent("gui.settings.title"));
        
        // Load persistent settings from data manager
        Boolean savedStructures = plugin.getDataManager().getSavedStructures();
        
        // Use saved settings if available, otherwise use config/world defaults
        boolean structuresEnabled = savedStructures != null ? savedStructures : plugin.getConfig().getBoolean("world.generation.generate-structures", true);
        
        // Structures setting (slot 11)
        gui.setItem(11, createStructuresItem(structuresEnabled));
        
        // Gamerules submenu (slot 13)
        gui.setItem(13, createGamerulesMenuItem());
        
        // Challenges submenu (slot 15)
        gui.setItem(15, createChallengesMenuItem());
        
        // Back button (slot 27 - bottom left)
        gui.setItem(27, createBackButton());
        
        // Reset settings button (slot 35 - bottom right)
        gui.setItem(35, createResetButton());
        
        // Filler items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 36; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Create difficulty setting item
     */
    private ItemStack createDifficultyItem(Difficulty current) {
        Material material;
        NamedTextColor color;
        
        switch (current) {
            case PEACEFUL:
                material = Material.BLUE_WOOL;
                color = NamedTextColor.AQUA;
                break;
            case EASY:
                material = Material.LIME_WOOL;
                color = NamedTextColor.GREEN;
                break;
            case NORMAL:
                material = Material.YELLOW_WOOL;
                color = NamedTextColor.YELLOW;
                break;
            case HARD:
                material = Material.RED_WOOL;
                color = NamedTextColor.RED;
                break;
            default:
                material = Material.YELLOW_WOOL;
                color = NamedTextColor.YELLOW;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("difficulty", current.name());
        meta.displayName(lang.getComponent("gui.settings.difficulty.name", placeholders)
            .color(NamedTextColor.GOLD)
            .append(Component.text(": ", NamedTextColor.GOLD))
            .append(Component.text(current.name(), color, TextDecoration.BOLD)));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        
        Map<String, String> currentPlaceholders = new HashMap<>();
        currentPlaceholders.put("difficulty", current.name());
        lore.add(lang.getComponent("gui.settings.difficulty.current", currentPlaceholders)
            .append(Component.text(current.name(), color)));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.difficulty.click-to-change"));
        lore.add(lang.getComponent("gui.settings.difficulty.peaceful")
            .color(current == Difficulty.PEACEFUL ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.easy")
            .color(current == Difficulty.EASY ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.normal")
            .color(current == Difficulty.NORMAL ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.hard")
            .color(current == Difficulty.HARD ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.difficulty.applied-immediately"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create structures setting item
     */
    private ItemStack createStructuresItem(boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.BRICK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("status", enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"));
        meta.displayName(lang.getComponent("gui.settings.structures.name")
            .color(NamedTextColor.GOLD)
            .append(Component.text(" ").color(NamedTextColor.GOLD))
            .append(Component.text(enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"), 
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, TextDecoration.BOLD)));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.structures.lore-desc"));
        lore.add(Component.text(""));
        
        Map<String, String> statusPlaceholders = new HashMap<>();
        statusPlaceholders.put("status", enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
        lore.add(lang.getComponent("gui.settings.structures.lore-status", statusPlaceholders));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.structures.lore-click"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.structures.lore-warning").decorate(TextDecoration.ITALIC));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create respawn setting item
     */
    private ItemStack createRespawnItem(boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.RED_BED : Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.settings.respawn.name")
            .color(NamedTextColor.GOLD)
            .append(Component.text(": ", NamedTextColor.GOLD))
            .append(Component.text(enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"), 
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, 
                TextDecoration.BOLD)));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        if (enabled) {
            lore.add(lang.getComponent("gui.settings.respawn.enabled-desc1"));
            lore.add(lang.getComponent("gui.settings.respawn.enabled-desc2"));
        } else {
            lore.add(lang.getComponent("gui.settings.respawn.disabled-desc1"));
            lore.add(lang.getComponent("gui.settings.respawn.disabled-desc2"));
        }
        lore.add(Component.text(""));
        
        Map<String, String> statusPlaceholders = new HashMap<>();
        statusPlaceholders.put("status", enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
        lore.add(lang.getComponent("gui.settings.respawn.status", statusPlaceholders));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.respawn.click-toggle"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.respawn.applied"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create info item
     */
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
    meta.displayName(Component.text("ℹ Einstellungen", NamedTextColor.AQUA, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
    lore.add(Component.text("Konfiguriere Challenge-Einstellungen", NamedTextColor.GRAY));
    lore.add(Component.text("für deine Speedrun-Welt.", NamedTextColor.GRAY));
        lore.add(Component.text(""));
    lore.add(Component.text("Einstellungen werden gespeichert", NamedTextColor.GREEN));
    lore.add(Component.text("und bleiben über Resets erhalten.", NamedTextColor.GREEN));
        lore.add(Component.text(""));
    lore.add(Component.text("Einige Einstellungen erfordern", NamedTextColor.YELLOW));
    lore.add(Component.text("/fullreset, um wirksam zu werden.", NamedTextColor.YELLOW));
        lore.add(Component.text(""));
    lore.add(Component.text("Nur verfügbar im", NamedTextColor.GRAY));
    lore.add(Component.text("Warteraum.", NamedTextColor.GRAY));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create gamerules menu item
     */
    private ItemStack createGamerulesMenuItem() {
        ItemStack item = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("⚙ Gamerules", NamedTextColor.AQUA, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Configure world gamerules", NamedTextColor.GRAY));
        lore.add(Component.text("for the speedrun world.", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("Click to open", NamedTextColor.YELLOW));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create challenges menu item
     */
    private ItemStack createChallengesMenuItem() {
        ItemStack item = new ItemStack(Material.TARGET);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("🎯 Challenges", NamedTextColor.GOLD, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Toggle special challenge modes", NamedTextColor.GRAY));
        lore.add(Component.text("for added difficulty.", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("Click to open", NamedTextColor.YELLOW));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create back button
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("← Back", NamedTextColor.YELLOW, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Return to host controls", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create reset settings button
     */
    private ItemStack createResetButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("↻ Reset Settings", NamedTextColor.RED, TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Reset all settings", NamedTextColor.GRAY));
        lore.add(Component.text("to default values", NamedTextColor.GRAY));
        lore.add(Component.text(""));
        lore.add(Component.text("Click to reset", NamedTextColor.YELLOW));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Open confirmation GUI for resetting settings
     */
    public void openResetConfirmation(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, lang.getComponent("gui.settings.reset-confirm-title"));
        
        // Confirm button (slot 11)
        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(Component.text("✓ YES - Reset to Defaults", NamedTextColor.GREEN, TextDecoration.BOLD));
        List<Component> confirmLore = new ArrayList<>();
        confirmLore.add(Component.text(""));
        confirmLore.add(Component.text("This will reset all settings", NamedTextColor.GRAY));
        confirmLore.add(Component.text("to their default values.", NamedTextColor.GRAY));
        confirmLore.add(Component.text(""));
        confirmLore.add(Component.text("Click to confirm", NamedTextColor.GREEN));
        confirmMeta.lore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);
        
        // Cancel button (slot 15)
        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("✗ NO - Keep Settings", NamedTextColor.RED, TextDecoration.BOLD));
        List<Component> cancelLore = new ArrayList<>();
        cancelLore.add(Component.text(""));
        cancelLore.add(Component.text("Cancel and return to", NamedTextColor.GRAY));
        cancelLore.add(Component.text("the settings menu", NamedTextColor.GRAY));
        cancelLore.add(Component.text(""));
        cancelLore.add(Component.text("Click to cancel", NamedTextColor.YELLOW));
        cancelMeta.lore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        
        // Warning sign (slot 13)
        ItemStack warning = new ItemStack(Material.BARRIER);
        ItemMeta warningMeta = warning.getItemMeta();
        warningMeta.displayName(Component.text("⚠ WARNING", NamedTextColor.GOLD, TextDecoration.BOLD));
        List<Component> warningLore = new ArrayList<>();
        warningLore.add(Component.text(""));
        warningLore.add(Component.text("This will reset all", NamedTextColor.GRAY));
        warningLore.add(Component.text("settings to defaults!", NamedTextColor.GRAY));
        warningLore.add(Component.text(""));
        warningLore.add(Component.text("This action cannot", NamedTextColor.RED));
        warningLore.add(Component.text("be undone!", NamedTextColor.RED));
        warningMeta.lore(warningLore);
        warning.setItemMeta(warningMeta);
        gui.setItem(13, warning);
        
        // Filler items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Open gamerules submenu
     */
    public void openGamerulesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, lang.getComponent("gui.settings.gamerules-title"));
        
        String speedrunWorldName = plugin.getConfig().getString("world.speedrun-world", "speedrun_world");
        World speedrunWorld = Bukkit.getWorld(speedrunWorldName);
        
        // Note: speedrunWorld can be null if world hasn't been created yet - that's OK!
        
        // Row 0: Common gameplay rules (top row)
        gui.setItem(1, createGameruleItem("keep_inventory", speedrunWorld, Material.CHEST));
        gui.setItem(2, createGameruleItem("natural_health_regeneration", speedrunWorld, Material.GOLDEN_APPLE));
        gui.setItem(3, createGameruleItem("immediate_respawn", speedrunWorld, Material.RED_BED));
        gui.setItem(4, createGameruleItem("players_sleeping_percentage", speedrunWorld, Material.WHITE_BED));
        gui.setItem(5, createGameruleItem("locator_bar", speedrunWorld, Material.RECOVERY_COMPASS));        
        // Row 1: Mob spawning rules
        gui.setItem(10, createGameruleItem("spawn_mobs", speedrunWorld, Material.ZOMBIE_HEAD));
        gui.setItem(11, createGameruleItem("spawn_monsters", speedrunWorld, Material.CREEPER_HEAD));
        gui.setItem(12, createGameruleItem("spawn_patrols", speedrunWorld, Material.CROSSBOW));
        gui.setItem(13, createGameruleItem("spawn_wandering_traders", speedrunWorld, Material.EMERALD));
        gui.setItem(14, createGameruleItem("spawn_wardens", speedrunWorld, Material.SCULK_SHRIEKER));
        gui.setItem(15, createGameruleItem("spawner_blocks_work", speedrunWorld, Material.SPAWNER));
        gui.setItem(16, createGameruleItem("mob_griefing", speedrunWorld, Material.TNT));
        
        // Row 2: Environmental rules  
        gui.setItem(19, createGameruleItem("advance_time", speedrunWorld, Material.CLOCK));
        gui.setItem(20, createGameruleItem("advance_weather", speedrunWorld, Material.WATER_BUCKET));
        gui.setItem(21, createGameruleItem("random_tick_speed", speedrunWorld, Material.WHEAT_SEEDS));
        gui.setItem(22, createGameruleItem("tnt_explodes", speedrunWorld, Material.TNT));
        gui.setItem(23, createGameruleItem("raids", speedrunWorld, Material.WHITE_BANNER));
        gui.setItem(24, createGameruleItem("forgive_dead_players", speedrunWorld, Material.TOTEM_OF_UNDYING));
        
        // Row 3: Damage rules
        gui.setItem(28, createGameruleItem("fall_damage", speedrunWorld, Material.LEATHER_BOOTS));
        gui.setItem(29, createGameruleItem("fire_damage", speedrunWorld, Material.BLAZE_POWDER));
        gui.setItem(30, createGameruleItem("drowning_damage", speedrunWorld, Material.WATER_BUCKET));
        gui.setItem(31, createGameruleItem("freeze_damage", speedrunWorld, Material.POWDER_SNOW_BUCKET));
        
        // Row 4: Drop rules
        gui.setItem(37, createGameruleItem("block_drops", speedrunWorld, Material.DIAMOND_PICKAXE));
        gui.setItem(38, createGameruleItem("entity_drops", speedrunWorld, Material.MINECART));
        gui.setItem(39, createGameruleItem("mob_drops", speedrunWorld, Material.BONE));
        gui.setItem(40, createGameruleItem("limited_crafting", speedrunWorld, Material.CRAFTING_TABLE));
        
        // Row 5: Respawn/player settings
        gui.setItem(42, createDifficultyGameruleItem(speedrunWorld));
        gui.setItem(43, createRespawnGameruleItem(speedrunWorld));
        
        // Back button (bottom row center - slot 49)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("← Back to Settings", NamedTextColor.YELLOW));
        back.setItemMeta(backMeta);
        gui.setItem(49, back);
        
        // Filler items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Open challenges submenu
     */
    public void openChallengesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, lang.getComponent("gui.settings.challenges-title"));
        
        // Challenge items
        gui.setItem(10, createChallengeItem("manhunt_mode", Material.COMPASS));
        gui.setItem(11, createChallengeItem("team_race_mode", Material.RECOVERY_COMPASS));
        gui.setItem(12, createChallengeItem("chunk_items", Material.CHEST));
        gui.setItem(13, createChallengeItem("friendly_fire_item", Material.GOLDEN_SWORD));
        gui.setItem(14, createChallengeItem("keep_rng", Material.ENDER_CHEST));
        
        // Back button (bottom row center - slot 49)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("← Back to Settings", NamedTextColor.YELLOW));
        back.setItemMeta(backMeta);
        gui.setItem(49, back);
        
        // Filler items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Create a challenge toggle item
     */
    private ItemStack createChallengeItem(String challengeName, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Get current value - check saved value first
        Boolean savedValue = plugin.getDataManager().getSavedChallenge(challengeName);
        boolean enabled = savedValue != null ? savedValue : false;
        
        // Get description based on challenge name
        String description = getChallengeDescription(challengeName);
        
        // For keep_rng, also get the percentage setting
        Integer keepPercentage = null;
        if (challengeName.equals("keep_rng")) {
            keepPercentage = plugin.getDataManager().getSavedChallengeSetting("keep_rng", "percentage");
            if (keepPercentage == null) {
                keepPercentage = 50; // Default
            }
        }
        
        // Display name
        if (challengeName.equals("keep_rng") && enabled) {
            meta.displayName(Component.text(formatChallengeName(challengeName) + ": ", NamedTextColor.GOLD)
                .append(Component.text(keepPercentage + "%", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        } else {
            meta.displayName(Component.text(formatChallengeName(challengeName) + ": ", NamedTextColor.GOLD)
                .append(Component.text(enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"), 
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED, 
                    TextDecoration.BOLD)));
        }
        
        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(description, NamedTextColor.GRAY));
        lore.add(Component.text(""));
        
        // Special handling for keep_rng to show percentage controls
        if (challengeName.equals("keep_rng")) {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"), 
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
            if (enabled) {
                lore.add(Component.text("Keep Percentage: " + keepPercentage + "%", NamedTextColor.YELLOW));
            }
            lore.add(Component.text(""));
            lore.add(Component.text("Left-click: +10%", NamedTextColor.GREEN));
            lore.add(Component.text("Right-click: -10%", NamedTextColor.RED));
            lore.add(Component.text(""));
            lore.add(Component.text("0% = Keep RNG off", NamedTextColor.GRAY, TextDecoration.ITALIC));
            lore.add(Component.text("Range: 0-100% in 10% increments", NamedTextColor.GRAY, TextDecoration.ITALIC));
            lore.add(Component.text("Rounds down (13 items → " + (13 * keepPercentage / 100) + " kept)", NamedTextColor.AQUA, TextDecoration.ITALIC));
        } else {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"), 
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
            lore.add(Component.text(""));
            lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW));
            lore.add(Component.text(""));
        }
        
        // Add mutual exclusivity warning for Manhunt and Team Race
        if (challengeName.equals("manhunt_mode")) {
            Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
            if (teamRaceEnabled != null && teamRaceEnabled) {
                lore.add(Component.text("⚠ Manhunt Race is active!", NamedTextColor.RED, TextDecoration.BOLD));
                lore.add(Component.text("Will be automatically disabled", NamedTextColor.YELLOW, TextDecoration.ITALIC));
                lore.add(Component.text(""));
            }
            lore.add(Component.text("Use /team runner or /team hunter", NamedTextColor.AQUA, TextDecoration.ITALIC));
            lore.add(Component.text("Choose teams before /start", NamedTextColor.GRAY, TextDecoration.ITALIC));
            lore.add(Component.text("Hunters immobilized for 2 minutes", NamedTextColor.GRAY, TextDecoration.ITALIC));
        } else if (challengeName.equals("team_race_mode")) {
            Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
            if (manhuntEnabled != null && manhuntEnabled) {
                lore.add(Component.text("⚠ Manhunt Mode is active!", NamedTextColor.RED, TextDecoration.BOLD));
                lore.add(Component.text("Will be automatically disabled", NamedTextColor.YELLOW, TextDecoration.ITALIC));
                lore.add(Component.text(""));
            }
            lore.add(Component.text("Use /team <TeamName> to join", NamedTextColor.AQUA, TextDecoration.ITALIC));
            lore.add(Component.text("2-10 teams race to the Ender Dragon", NamedTextColor.GRAY, TextDecoration.ITALIC));
            lore.add(Component.text("Compasses point to nearest team", NamedTextColor.GOLD, TextDecoration.ITALIC));
        } else if (challengeName.equals("friendly_fire_item")) {
            lore.add(Component.text("Damage is synchronized in team", NamedTextColor.AQUA, TextDecoration.ITALIC));
            lore.add(Component.text("Lower HP = Better items", NamedTextColor.GOLD, TextDecoration.ITALIC));
        } else if (challengeName.equals("keep_rng") && !enabled) {
            // Only show when disabled, since enabled case is handled above
            lore.add(Component.text("✓ Applied on /start", NamedTextColor.GREEN, TextDecoration.ITALIC));
        } else if (challengeName.equals("one_heart") || challengeName.equals("half_health")) {
            lore.add(Component.text("⚠ Very difficult!", NamedTextColor.RED, TextDecoration.ITALIC));
        } else if (challengeName.equals("no_mining") || challengeName.equals("no_crafting")) {
            lore.add(Component.text("⚠ Extremely restrictive!", NamedTextColor.RED, TextDecoration.ITALIC));
        } else if (!challengeName.equals("keep_rng")) {
            lore.add(Component.text("✓ Applied on /start", NamedTextColor.GREEN, TextDecoration.ITALIC));
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
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
    
    /**
     * Create a gamerule toggle item
     */
    private ItemStack createGameruleItem(String gamerule, World world, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // Get description
        String description = getGameruleDescription(gamerule);
        
        // Get current value - check saved value first, then world value
        Object value;
        Object savedValue = plugin.getDataManager().getSavedGamerule(gamerule);
        
        if (savedValue != null) {
            value = savedValue;
        } else if (world != null) {
            GameRule<?> gameRule = GameRule.getByName(gamerule);
            if (gameRule != null) {
                value = world.getGameRuleValue(gameRule);
            } else {
                value = null;
            }
        } else {
            value = null;
        }
        
        // If value is still null, use default
        if (value == null) {
            value = getDefaultGameruleValue(gamerule);
        }
        
        boolean isBoolean = value instanceof Boolean;
        boolean enabled = isBoolean && (Boolean) value;
        Integer intValue = !isBoolean && value instanceof Integer ? (Integer) value : null;
        
        // Display name
        if (isBoolean) {
            meta.displayName(Component.text(formatGameruleName(gamerule) + ": ", NamedTextColor.GOLD)
                    .append(Component.text(enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"), 
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED, 
                        TextDecoration.BOLD)));
        } else {
            meta.displayName(Component.text(formatGameruleName(gamerule) + ": ", NamedTextColor.GOLD)
                    .append(Component.text(String.valueOf(intValue), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        }
        
        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text(description, NamedTextColor.GRAY));
        lore.add(Component.text(""));

        if (isBoolean) {
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"), 
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
            lore.add(Component.text(""));
            lore.add(Component.text("Click to toggle", NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Current: " + intValue, NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(intValue), NamedTextColor.YELLOW)));
            lore.add(Component.text(""));
            lore.add(Component.text("Left-click: +1", NamedTextColor.GREEN));
            lore.add(Component.text("Right-click: -1", NamedTextColor.RED));
            lore.add(Component.text("Shift+Left-click: +10", NamedTextColor.GREEN));
            lore.add(Component.text("Shift+Right-click: -10", NamedTextColor.RED));
        }

        lore.add(Component.text(""));
        lore.add(Component.text("✓ Saved & applied when world is created", NamedTextColor.GREEN, TextDecoration.ITALIC));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the Difficulty gamerule item (special case)
     */
    private ItemStack createDifficultyGameruleItem(World world) {
        // Load current setting from persistent data or world
        String savedDifficulty = plugin.getDataManager().getSavedDifficulty();
        Difficulty current;
        
        if (savedDifficulty != null) {
            try {
                current = Difficulty.valueOf(savedDifficulty);
            } catch (IllegalArgumentException e) {
                current = world != null ? world.getDifficulty() : Difficulty.NORMAL;
            }
        } else {
            current = world != null ? world.getDifficulty() : Difficulty.NORMAL;
        }
        
        Material material;
        NamedTextColor color;
        
        switch (current) {
            case PEACEFUL:
                material = Material.BLUE_WOOL;
                color = NamedTextColor.AQUA;
                break;
            case EASY:
                material = Material.LIME_WOOL;
                color = NamedTextColor.GREEN;
                break;
            case NORMAL:
                material = Material.YELLOW_WOOL;
                color = NamedTextColor.YELLOW;
                break;
            case HARD:
                material = Material.RED_WOOL;
                color = NamedTextColor.RED;
                break;
            default:
                material = Material.YELLOW_WOOL;
                color = NamedTextColor.YELLOW;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.settings.difficulty.name")
            .color(NamedTextColor.GOLD)
            .append(Component.text(": ", NamedTextColor.GOLD))
            .append(Component.text(current.name(), color, TextDecoration.BOLD)));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        
        Map<String, String> currentPlaceholders = new HashMap<>();
        currentPlaceholders.put("difficulty", current.name());
        lore.add(lang.getComponent("gui.settings.difficulty.current", currentPlaceholders)
            .append(Component.text(current.name(), color)));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.difficulty.click-to-change"));
        lore.add(lang.getComponent("gui.settings.difficulty.peaceful")
            .color(current == Difficulty.PEACEFUL ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.easy")
            .color(current == Difficulty.EASY ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.normal")
            .color(current == Difficulty.NORMAL ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(lang.getComponent("gui.settings.difficulty.hard")
            .color(current == Difficulty.HARD ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.difficulty.applied-immediately"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the Allow Respawn gamerule item (special case)
     */
    private ItemStack createRespawnGameruleItem(World world) {
        // Get current value - check saved value first
        Boolean savedRespawn = plugin.getDataManager().getSavedRespawn();
        boolean enabled = savedRespawn != null ? savedRespawn : plugin.getConfig().getBoolean("challenge.allow-respawn", true);
        
        ItemStack item = new ItemStack(enabled ? Material.RED_BED : Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.settings.respawn.name")
            .color(NamedTextColor.GOLD)
            .append(Component.text(": ", NamedTextColor.GOLD))
            .append(Component.text(enabled ? lang.getMessage("common.on") : lang.getMessage("common.off"), 
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, 
                TextDecoration.BOLD)));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        if (enabled) {
            lore.add(lang.getComponent("gui.settings.respawn.enabled-desc1"));
            lore.add(lang.getComponent("gui.settings.respawn.enabled-desc2"));
        } else {
            lore.add(lang.getComponent("gui.settings.respawn.disabled-desc1"));
            lore.add(lang.getComponent("gui.settings.respawn.disabled-desc2"));
        }
        lore.add(Component.text(""));
        
        Map<String, String> statusPlaceholders = new HashMap<>();
        statusPlaceholders.put("status", enabled ? lang.getMessage("common.enabled") : lang.getMessage("common.disabled"));
        lore.add(lang.getComponent("gui.settings.respawn.status", statusPlaceholders));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.respawn.click-toggle"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.settings.respawn.applied"));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Get default gamerule value
     */
    private Object getDefaultGameruleValue(String gamerule) {
        // Return Minecraft's default values for common gamerules
        switch (gamerule) {
            // Numeric gamerules
            case "random_tick_speed": return 3;
            case "players_sleeping_percentage": return 100;
            
            // Drop rules
            case "block_drops": return true;
            case "entity_drops": return true;
            case "mob_drops": return true;
            
            // System rules
            case "tnt_explodes": return true;
            
            // Mob behavior rules
            case "forgive_dead_players": return true;
            case "mob_griefing": return true;
            case "raids": return true;
            case "universal_anger": return false;
            
            // Damage rules
            case "drowning_damage": return true;
            case "elytra_movement_check": return true;
            case "fall_damage": return true;
            case "fire_damage": return true;
            case "freeze_damage": return true;
            
            // Respawn and inventory rules
            case "immediate_respawn": return false;
            case "keep_inventory": return false;
            case "limited_crafting": return false;
            case "locator_bar": return false;
            case "natural_health_regeneration": return true;
            
            // Spawn rules
            case "spawn_monsters": return true;
            case "spawn_mobs": return true;
            case "spawn_patrols": return true;
            case "spawn_wandering_traders": return true;
            case "spawn_wardens": return true;
            case "spawner_blocks_work": return true;
            case "spectators_generate_chunks": return false; // Always false
            
            // Time and environment rules
            case "advance_time": return true;
            case "advance_weather": return true;
            
            default: return true; // Most gamerules default to true
        }
    }
    
    /**
     * Format gamerule name for display
     */
    private String formatGameruleName(String gamerule) {
        // Convert UPPER_CASE to Title Case
        String[] parts = gamerule.split("_");
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
     * Get challenge description
     */
    private String getChallengeDescription(String challengeName) {
        switch (challengeName) {
            case "manhunt_mode":
                return "Activate Manhunt Mode";
            case "team_race_mode":
                return "Manhunt Race - 2-10 Teams";
            case "chunk_items":
                return "Receive random item per chunk";
            case "friendly_fire_item":
                return "Friendly Fire = OP Items";
            case "keep_rng":
                return "Keep X% of inventory on death (random)";
            default:
                return "Challenge: " + formatChallengeName(challengeName);
        }
    }
    
    /**
     * Get gamerule description
     */
    private String getGameruleDescription(String gamerule) {
        switch (gamerule) {
            case "keep_inventory":
                return "Keep items on death";
            case "natural_health_regeneration":
                return "Natural health regeneration";
            case "immediate_respawn":
                return "Immediate respawn (no death screen)";
            case "players_sleeping_percentage":
                return "% of players needed to sleep";
            case "locator_bar":
                return "Show locator bar";
            case "spawn_mobs":
                return "Spawn hostile mobs";
            case "spawn_monsters":
                return "Spawn monsters";
            case "spawn_patrols":
                return "Spawn patrols (pillagers)";
            case "spawn_wandering_traders":
                return "Spawn wandering traders";
            case "spawn_wardens":
                return "Spawn wardens";
            case "spawner_blocks_work":
                return "Spawner blocks work";
            case "mob_griefing":
                return "Mob griefing (Creeper, Endermen)";
            case "advance_time":
                return "Day-night cycle";
            case "advance_weather":
                return "Weather effects";
            case "random_tick_speed":
                return "Random tick speed (default 3)";
            case "tnt_explodes":
                return "TNT explodes";
            case "raids":
                return "Enable raids";
            case "forgive_dead_players":
                return "Forgive dead players";
            case "fall_damage":
                return "Fall damage";
            case "fire_damage":
                return "Fire damage";
            case "drowning_damage":
                return "Drowning damage";
            case "freeze_damage":
                return "Freeze damage";
            case "block_drops":
                return "Blocks drop items";
            case "entity_drops":
                return "Entities drop items";
            case "mob_drops":
                return "Mobs drop loot";
            case "limited_crafting":
                return "Requires unlocked recipe";
            default:
                return formatGameruleName(gamerule);
        }
    }
}

