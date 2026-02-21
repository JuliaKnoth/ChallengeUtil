package de.connunity.util.challenge.gui;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for team selection in Team Race mode
 * Displays teams as colored wool blocks with enchantment glint on selected team
 */
public class TeamSelectionGUI {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    // Map German team names to their wool colors
    private static final Map<String, Material> TEAM_WOOL_MATERIALS = new LinkedHashMap<>();
    
    static {
        TEAM_WOOL_MATERIALS.put("Rot", Material.RED_WOOL);
        TEAM_WOOL_MATERIALS.put("Blau", Material.BLUE_WOOL);
        TEAM_WOOL_MATERIALS.put("Grün", Material.GREEN_WOOL);
        TEAM_WOOL_MATERIALS.put("Gelb", Material.YELLOW_WOOL);
        TEAM_WOOL_MATERIALS.put("Lila", Material.PURPLE_WOOL);
        TEAM_WOOL_MATERIALS.put("Aqua", Material.CYAN_WOOL);
        TEAM_WOOL_MATERIALS.put("Weiß", Material.WHITE_WOOL);
        TEAM_WOOL_MATERIALS.put("Orange", Material.ORANGE_WOOL);
        TEAM_WOOL_MATERIALS.put("Pink", Material.PINK_WOOL);
    }
    
    public TeamSelectionGUI(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Open the team selection GUI for a player
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 9, lang.getComponent("teamrace.gui.title"));
        
        String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        List<String> availableTeams = plugin.getTeamRaceManager().getAvailableTeamNames();
        
        int slotIndex = 0;
        for (String teamName : availableTeams) {
            if (slotIndex >= 9) break;
            boolean isPlayerTeam = teamName.equals(playerTeam);
            gui.setItem(slotIndex, createTeamItem(teamName, isPlayerTeam));
            slotIndex++;
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Create a team item (wool block) with optional enchantment glint
     */
    private ItemStack createTeamItem(String teamName, boolean isSelected) {
        Material woolMaterial = TEAM_WOOL_MATERIALS.getOrDefault(teamName, Material.WHITE_WOOL);
        ItemStack item = new ItemStack(woolMaterial);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Get team color from TeamRaceManager
            net.kyori.adventure.text.format.TextColor teamColor = 
                    plugin.getTeamRaceManager().getTeamTextColor(teamName);
            
            // Set display name
            Component displayName = Component.text("Team " + teamName, teamColor, TextDecoration.BOLD);
            meta.displayName(displayName);
            
            // Add lore
            List<Component> lore = new ArrayList<>();
            
            // Show member count
            int memberCount = plugin.getDataManager().getPlayersInTeam(teamName).size();
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("count", String.valueOf(memberCount));
            lore.add(lang.getComponent("teamrace.gui.members", placeholders));
            
            lore.add(Component.text(""));
            
            if (isSelected) {
                // Show selected indicator
                lore.add(lang.getComponent("teamrace.gui.current-team"));
            } else {
                // Show click to join
                lore.add(lang.getComponent("teamrace.gui.click-to-join"));
            }
            
            meta.lore(lore);
            
            // Add enchantment glint if this is the player's current team
            if (isSelected) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * Create the team selection menu item.
     * Shows a barrier block if the player has no team, or the team's wool color if they do.
     */
    public static ItemStack createTeamMenuItem(ChallengeUtil plugin, Player player) {
        LanguageManager lang = plugin.getLanguageManager();

        // Determine material based on player's current team
        String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        Material material;
        if (playerTeam != null && !playerTeam.isEmpty() && TEAM_WOOL_MATERIALS.containsKey(playerTeam)) {
            material = TEAM_WOOL_MATERIALS.get(playerTeam);
        } else {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(lang.getComponent("teamrace.menu-item.name"));

            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("teamrace.menu-item.lore1"));
            lore.add(lang.getComponent("teamrace.menu-item.lore2"));
            lore.add(Component.text(""));
            lore.add(lang.getComponent("teamrace.menu-item.hint"));

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }
}
