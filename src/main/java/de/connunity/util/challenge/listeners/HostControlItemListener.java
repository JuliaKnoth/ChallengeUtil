package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.HostControlGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the enchanted nether star host control item
 */
public class HostControlItemListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public HostControlItemListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Give the host control item to players with challenge.host permission on join
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player has host permission
        if (!player.hasPermission("challenge.host")) {
            return;
        }
        
        plugin.getLogger().info("Player " + player.getName() + " has host permission, scheduling item give...");
        
        // Delay to ensure inventory is fully loaded and all other join logic has completed
        // This runs after PlayerJoinListener (which runs at 5L), so we use 20L to be safe
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            giveHostControlItem(player);
        }, 20L);
    }
    
    /**
     * Handle right-click on the host control item
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        // Check if player is holding the host control item
        if (!isHostControlItem(item)) {
            return;
        }
        
        // Cancel the event to prevent any default behavior
        event.setCancelled(true);
        
        // Debug logging
        plugin.getLogger().info("Host control item clicked by " + player.getName());
        
        // Check permission
        if (!player.hasPermission("challenge.host")) {
            player.sendMessage(lang.getComponent("host.no-permission-item"));
            plugin.getLogger().warning(player.getName() + " tried to use host control without permission!");
            return;
        }
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.sendMessage(Component.text("✗ A world reset is currently in progress!", NamedTextColor.RED));
            player.sendMessage(Component.text("  Please wait until the reset is complete.", NamedTextColor.GRAY));
            return;
        }
        
        // Open the host control GUI
        plugin.getLogger().info("Opening host control GUI for " + player.getName());
        HostControlGUI gui = new HostControlGUI(plugin);
        gui.open(player);
    }
    
    /**
     * Give the host control item to a player in slot 8 (last hotbar slot)
     */
    private void giveHostControlItem(Player player) {
        // Check if player is in waiting room
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        if (!player.getWorld().getName().equals(waitingRoomName)) {
            plugin.getLogger().info("Not giving host item to " + player.getName() + " - not in waiting room (current world: " + player.getWorld().getName() + ", expected: " + waitingRoomName + ")");
            return; // Only give item in waiting room
        }
        
        plugin.getLogger().info("Giving host control item to " + player.getName() + " in world " + player.getWorld().getName());
        
        // Check if player already has the item in slot 8
        ItemStack slot8Item = player.getInventory().getItem(8);
        if (isHostControlItem(slot8Item)) {
            plugin.getLogger().info(player.getName() + " already has the host control item in slot 8");
            return; // Player already has the item in the correct slot
        }
        
        // Remove any existing host control items from inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isHostControlItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Give the item in slot 8 (last hotbar slot)
        ItemStack hostItem = HostControlGUI.createHostControlItem(plugin);
        player.getInventory().setItem(8, hostItem);
        plugin.getLogger().info("Successfully gave host control item to " + player.getName());
    }
    
    /**
     * Prevent dropping the host control item
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (isHostControlItem(item)) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent moving the host control item in inventory and keep it in slot 8
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        ItemStack currentItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        
        // Check if trying to move the host control item
        if (isHostControlItem(currentItem) || isHostControlItem(cursorItem)) {
            event.setCancelled(true);
            
            // Ensure item is in slot 8
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ensureItemInSlot8(player);
            });
        }
    }
    
    /**
     * Remove host control item when player leaves waiting room
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        String fromWorldName = event.getFrom().getName();
        String toWorldName = player.getWorld().getName();
        
        // If player is leaving the waiting room, remove the host control item
        if (fromWorldName.equals(waitingRoomName) && !toWorldName.equals(waitingRoomName)) {
            removeHostControlItem(player);
        }
        
        // If player is entering the waiting room and has host permission, give them the item
        if (toWorldName.equals(waitingRoomName) && player.hasPermission("challenge.host")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                giveHostControlItem(player);
            }, 10L);
        }
    }
    
    /**
     * Ensure the host control item is in slot 8 and nowhere else
     */
    private void ensureItemInSlot8(Player player) {
        // Only enforce in waiting room
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        if (!player.getWorld().getName().equals(waitingRoomName)) {
            return;
        }
        
        if (!player.hasPermission("challenge.host")) {
            return;
        }
        
        // Remove from all slots except slot 8
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (i == 8) continue;
            
            if (isHostControlItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // Ensure it's in slot 8
        if (!isHostControlItem(player.getInventory().getItem(8))) {
            ItemStack hostItem = HostControlGUI.createHostControlItem(plugin);
            player.getInventory().setItem(8, hostItem);
        }
    }
    
    /**
     * Remove all host control items from player's inventory
     */
    private void removeHostControlItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isHostControlItem(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
            }
        }
    }
    
    /**
     * Check if an item is the host control item
     */
    private boolean isHostControlItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR) {
            return false;
        }
        
        if (!item.hasItemMeta()) {
            return false;
        }
        
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) {
            return false;
        }
        
        String name = PlainTextComponentSerializer.plainText().serialize(displayName);
        return name.equals("Host Controls");
    }
}
