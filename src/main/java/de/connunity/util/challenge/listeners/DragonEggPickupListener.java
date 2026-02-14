package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;

/**
 * Handles dragon egg pickup for the custom end fight
 */
public class DragonEggPickupListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private final CustomEndFightManager endFightManager;
    
    public DragonEggPickupListener(ChallengeUtil plugin, CustomEndFightManager endFightManager) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.endFightManager = endFightManager;
    }
    
    /**
     * Handle when a player breaks the dragon egg block
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonEggBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) {
            return;
        }
        
        // Check if custom end fight is active
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if egg was already collected
        if (endFightManager.isEggCollected()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Cancel the normal break event
        event.setCancelled(true);
        
        // Remove the egg block
        event.getBlock().setType(Material.AIR);
        
        // Give egg to player's inventory
        player.getInventory().addItem(new ItemStack(Material.DRAGON_EGG, 1));
        
        // Trigger the egg collection
        endFightManager.onEggCollected(player);
    }
    
    /**
     * Handle when a player picks up the dragon egg
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonEggPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        
        // Check if it's a dragon egg
        if (itemStack.getType() != Material.DRAGON_EGG) {
            return;
        }
        
        // Check if custom end fight is active
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if egg was already collected
        if (endFightManager.isEggCollected()) {
            return;
        }
        
        // Prevent normal pickup, we'll handle this ourselves
        event.setCancelled(true);
        
        // Remove the item from world
        item.remove();
        
        // Give egg to player's inventory
        player.getInventory().addItem(new ItemStack(Material.DRAGON_EGG, 1));
        
        // Reset fall distance to prevent flying kick when jumping
        player.setFallDistance(0);
        
        // Trigger the egg collection
        endFightManager.onEggCollected(player);
    }
    
    /**
     * Handle when a player interacts with the dragon egg
     * - Prevent teleportation by canceling right-click
     * - Allow picking up by punching (left-click)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDragonEggInteract(PlayerInteractEvent event) {
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if player clicked on dragon egg
        if (event.getClickedBlock() == null) {
            return;
        }
        
        if (event.getClickedBlock().getType() != Material.DRAGON_EGG) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // If egg hasn't been collected yet
        if (!endFightManager.isEggCollected()) {
            // Cancel right-click to prevent teleportation
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                return;
            }
            
            // Handle left-click (punch) to pick up the egg
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                
                // Remove the egg block
                event.getClickedBlock().setType(Material.AIR);
                
                // Give egg to player's inventory
                player.getInventory().addItem(new ItemStack(Material.DRAGON_EGG, 1));
                
                // Reset fall distance to prevent flying kick when jumping
                player.setFallDistance(0);
                
                // Trigger the egg collection
                endFightManager.onEggCollected(player);
            }
        } else {
            // Egg already collected - prevent any interaction with remaining egg blocks
            event.setCancelled(true);
        }
    }
    
    /**
     * Prevent players from dropping the dragon egg during the custom end fight
     * The egg can only be transferred by killing the egg holder
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonEggDrop(PlayerDropItemEvent event) {
        // Check if custom end fight is active
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if the dropped item is a dragon egg
        if (event.getItemDrop().getItemStack().getType() != Material.DRAGON_EGG) {
            return;
        }
        
        // Cancel the drop
        event.setCancelled(true);
        
        // Send a message to the player
        Player player = event.getPlayer();
        player.sendMessage(lang.getComponent("endfight.cannot-drop-egg")
            .color(NamedTextColor.RED));
    }
    
    /**
     * Prevent players from placing the dragon egg during the custom end fight
     * The egg must stay in the holder's inventory
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonEggPlace(BlockPlaceEvent event) {
        // Check if custom end fight is active
        if (!endFightManager.isActive()) {
            return;
        }
        
        // Check if the placed block is a dragon egg
        if (event.getBlock().getType() != Material.DRAGON_EGG) {
            return;
        }
        
        // Cancel the placement
        event.setCancelled(true);
        
        // Send a message to the player
        Player player = event.getPlayer();
        player.sendMessage(lang.getComponent("endfight.cannot-place-egg")
            .color(NamedTextColor.RED));
    }
}
