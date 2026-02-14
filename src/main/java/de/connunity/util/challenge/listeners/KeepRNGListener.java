package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keep RNG Challenge - Randomly keep 50% of inventory on death
 * The other 50% is dropped at the death location
 * Items remain in their original slots (no shuffling)
 */
public class KeepRNGListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public KeepRNGListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Check if the challenge is enabled
        Boolean keepRNGEnabled = plugin.getDataManager().getSavedChallenge("keep_rng");
        if (keepRNGEnabled == null || !keepRNGEnabled) {
            return; // Challenge not enabled
        }
        
        // Check if the timer is running (challenge is active)
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return; // Challenge not active or paused
        }
        
        Player player = event.getPlayer();
        
        // Track all slot indices that have items
        List<Integer> occupiedSlots = new ArrayList<>();
        
        // Check main inventory (0-35: hotbar + main inventory)
        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                occupiedSlots.add(i);
            }
        }
        
        // Check armor slots (100-103: boots, leggings, chestplate, helmet)
        for (int i = 0; i < 4; i++) {
            ItemStack armorPiece = player.getInventory().getArmorContents()[i];
            if (armorPiece != null && !armorPiece.getType().isAir()) {
                occupiedSlots.add(100 + i);
            }
        }
        
        // Check offhand (40)
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            occupiedSlots.add(40);
        }
        
        // If no items, nothing to do
        if (occupiedSlots.isEmpty()) {
            return;
        }
        
        // Get Keep RNG percentage setting (0-100, default 50)
        Integer keepPercentage = plugin.getDataManager().getSavedChallengeSetting("keep_rng", "percentage");
        if (keepPercentage == null) {
            keepPercentage = 50; // Default to 50%
        }
        
        // If percentage is 0, challenge is effectively disabled (keep nothing)
        if (keepPercentage == 0) {
            return; // Keep nothing, drop everything (default behavior)
        }
        
        // Calculate how many items to keep (percentage rounded down)
        int totalItems = occupiedSlots.size();
        int itemsToKeep = (totalItems * keepPercentage) / 100;
        
        // Randomly shuffle the slot indices to decide which ones to keep
        Collections.shuffle(occupiedSlots);
        
        // The first half are slots to keep, the second half are slots to drop
        List<Integer> slotsToKeep = new ArrayList<>(occupiedSlots.subList(0, itemsToKeep));
        List<Integer> slotsToDrop = new ArrayList<>(occupiedSlots.subList(itemsToKeep, totalItems));
        
        // Clear the default drops (we'll add our own)
        event.getDrops().clear();
        
        // Process each slot
        for (int slot : slotsToDrop) {
            ItemStack itemToDrop = getItemFromSlot(player, slot);
            if (itemToDrop != null) {
                event.getDrops().add(itemToDrop.clone());
                setItemInSlot(player, slot, null); // Remove from inventory
            }
        }
        
        // Keep the items in slots that were selected to keep (they stay in place)
        // No action needed - they're already there!
    }
    
    /**
     * Get item from a specific slot (including armor and offhand)
     */
    private ItemStack getItemFromSlot(Player player, int slot) {
        if (slot < 36) {
            // Main inventory
            return player.getInventory().getItem(slot);
        } else if (slot == 40) {
            // Offhand
            return player.getInventory().getItemInOffHand();
        } else if (slot >= 100 && slot <= 103) {
            // Armor slots
            return player.getInventory().getArmorContents()[slot - 100];
        }
        return null;
    }
    
    /**
     * Set item in a specific slot (including armor and offhand)
     */
    private void setItemInSlot(Player player, int slot, ItemStack item) {
        if (slot < 36) {
            // Main inventory
            player.getInventory().setItem(slot, item);
        } else if (slot == 40) {
            // Offhand
            player.getInventory().setItemInOffHand(item);
        } else if (slot >= 100 && slot <= 103) {
            // Armor slots
            ItemStack[] armor = player.getInventory().getArmorContents();
            armor[slot - 100] = item;
            player.getInventory().setArmorContents(armor);
        }
    }
}
