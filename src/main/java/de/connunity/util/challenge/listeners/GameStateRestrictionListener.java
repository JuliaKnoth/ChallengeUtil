package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Restricts player actions when the game is paused or hasn't started yet
 * This ensures that players can't make progress or interfere with the game state
 * when the timer is not actively running
 */
public class GameStateRestrictionListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public GameStateRestrictionListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check if game actions should be restricted (not running or paused)
     */
    private boolean shouldRestrictActions() {
        return !plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused();
    }
    
    /**
     * Check if player should be exempt from restrictions (creative/spectator mode)
     */
    private boolean isPlayerExempt(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }
    
    /**
     * Prevent block breaking when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent block placing when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent item pickup when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent item dropping when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent item use when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        // Allow opening GUIs and certain interactions even when paused
        // Only restrict actual item usage and block interactions
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || 
            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK ||
            event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            
            if (shouldRestrictActions()) {
                // Allow GUI items (like settings, host control) to work even when paused
                if (event.getItem() != null && event.getItem().hasItemMeta()) {
                    // Check if item has a display name (using legacy method for compatibility)
                    if (event.getItem().getItemMeta().hasDisplayName()) {
                        // Use toString() to handle both legacy and modern API
                        String displayName = String.valueOf(event.getItem().getItemMeta().displayName());
                        // Allow host control and settings items
                        if (displayName.contains("Settings") || displayName.contains("Host Control") || 
                            displayName.contains("Einstellungen") || displayName.contains("Host-Steuerung")) {
                            return; // Allow these items to function
                        }
                    }
                }
                
                // Block all other interactions
                event.setCancelled(true);
                if (plugin.getTimerManager().isPaused()) {
                    player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
                }
            }
        }
    }
    
    /**
     * Prevent bucket use when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketUse(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent mob/entity damage when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        // Cancel damage when game is paused or not started
        if (shouldRestrictActions()) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent crafting when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent eating when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent attacking entities when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getDamager();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent fishing when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setCancelled(true);
            if (plugin.getTimerManager().isPaused()) {
                player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
            }
        }
    }
    
    /**
     * Prevent exp gain when game is paused or not started
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExpGain(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        if (shouldRestrictActions()) {
            event.setAmount(0);
        }
    }
    
    /**
     * Prevent opening containers when game is paused or not started
     * (except for the waiting room world where inventory management should be allowed)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        
        if (isPlayerExempt(player)) {
            return;
        }
        
        // Allow inventory access in waiting room
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        if (player.getWorld().getName().equals(waitingRoomName)) {
            return;
        }
        
        // Check if it's a container (chest, barrel, etc.) and not player's own inventory
        if (event.getInventory().getHolder() != null && event.getInventory().getHolder() != player) {
            if (shouldRestrictActions()) {
                event.setCancelled(true);
                if (plugin.getTimerManager().isPaused()) {
                    player.sendActionBar(Component.text("Game is paused!", NamedTextColor.RED));
                }
            }
        }
    }
}
