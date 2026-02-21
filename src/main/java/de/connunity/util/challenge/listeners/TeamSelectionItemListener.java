package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.TeamSelectionGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the team selection menu item (nether star)
 * - Gives item to all players in team race mode (slot 0)
 * - Prevents dropping and moving
 * - Opens team selection GUI on interaction
 */
public class TeamSelectionItemListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public TeamSelectionItemListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Handle right-click on the team selection menu item
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is holding the team selection menu item
        if (!isTeamMenuItem(item)) {
            return;
        }
        
        // Cancel the event to prevent any default behavior
        event.setCancelled(true);
        
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            player.sendMessage(lang.getComponent("teamrace.menu-item.mode-not-active"));
            return;
        }
        
        // Check if timer is running (teams locked)
        if (plugin.getTimerManager().isRunning()) {
            player.sendMessage(lang.getComponent("team.teamrace-locked"));
            player.sendMessage(lang.getComponent("team.teamrace-locked-hint"));
            return;
        }
        
        // Open the team selection GUI
        TeamSelectionGUI gui = new TeamSelectionGUI(plugin);
        gui.open(player);
    }
    
    /**
     * Prevent dropping the team selection menu item
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (isTeamMenuItem(item)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent moving the team selection menu item in inventory and keep it in slot 0
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        
        // Check if trying to move the team selection menu item
        if (isTeamMenuItem(currentItem) || isTeamMenuItem(cursorItem)) {
            event.setCancelled(true);
            
            // Ensure item is in slot 0
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ensureItemInSlot0(player);
            });
        }
    }
    
    /**
     * Check if an item is the team selection menu item (barrier or any wool with the correct name)
     */
    private boolean isTeamMenuItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Accept barrier (no team selected) or any wool (team selected)
        if (item.getType() != Material.BARRIER && !item.getType().name().endsWith("_WOOL")) {
            return false;
        }

        if (!item.hasItemMeta()) {
            return false;
        }

        String itemName = PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        String expectedName = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("teamrace.menu-item.name"));

        return itemName.equals(expectedName);
    }
    
    /**
     * Ensure the team selection menu item is in slot 0
     */
    private void ensureItemInSlot0(Player player) {
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }
        
        // Remove any team menu items from inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isTeamMenuItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Give the item in slot 0
        ItemStack menuItem = TeamSelectionGUI.createTeamMenuItem(plugin, player);
        player.getInventory().setItem(0, menuItem);
    }
    
    /**
     * Give the team selection menu item to a player in slot 0
     */
    public void giveTeamMenuItem(Player player) {
        // Check if player already has the item in slot 0
        ItemStack slot0Item = player.getInventory().getItem(0);
        if (isTeamMenuItem(slot0Item)) {
            return; // Player already has the item in the correct slot
        }
        
        // Remove any existing team menu items from inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isTeamMenuItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Give the item in slot 0 (first hotbar slot)
        ItemStack menuItem = TeamSelectionGUI.createTeamMenuItem(plugin, player);
        player.getInventory().setItem(0, menuItem);
    }
    
    /**
     * Refresh the team selection menu item for a player (e.g. after they join a team).
     * Always replaces the current item to reflect the latest team color.
     */
    public void updateTeamMenuItem(Player player) {
        // Remove any existing team menu items from inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isTeamMenuItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Give the updated item in slot 0
        ItemStack menuItem = TeamSelectionGUI.createTeamMenuItem(plugin, player);
        player.getInventory().setItem(0, menuItem);
    }
    
    /**
     * Remove the team selection menu item from a player
     */
    public void removeTeamMenuItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isTeamMenuItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }
}
