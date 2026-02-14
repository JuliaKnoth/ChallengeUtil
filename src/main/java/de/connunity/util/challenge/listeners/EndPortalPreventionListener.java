package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Prevents END_PORTAL and END_GATEWAY blocks from forming in the End dimension
 * when custom end fight is active
 */
public class EndPortalPreventionListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final CustomEndFightManager endFightManager;
    
    public EndPortalPreventionListener(ChallengeUtil plugin, CustomEndFightManager endFightManager) {
        this.plugin = plugin;
        this.endFightManager = endFightManager;
    }
    
    /**
     * Prevent any END_PORTAL or END_GATEWAY blocks from being placed
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!endFightManager.isActive()) {
            return;
        }
        
        if (event.getBlock().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        Material type = event.getBlock().getType();
        if (type == Material.END_PORTAL || type == Material.END_GATEWAY) {
            event.setCancelled(true);
            event.getBlock().setType(Material.AIR);
        }
    }
    
    /**
     * Prevent END_PORTAL or END_GATEWAY blocks from forming naturally
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockForm(BlockFormEvent event) {
        if (!endFightManager.isActive()) {
            return;
        }
        
        if (event.getBlock().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        Material newType = event.getNewState().getType();
        if (newType == Material.END_PORTAL || newType == Material.END_GATEWAY) {
            event.setCancelled(true);
            // Schedule a task to ensure the block is AIR
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (event.getBlock().getType() == Material.END_PORTAL || 
                        event.getBlock().getType() == Material.END_GATEWAY) {
                        event.getBlock().setType(Material.AIR);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }
    
    /**
     * Prevent END_PORTAL or END_GATEWAY blocks from spreading/flowing
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!endFightManager.isActive()) {
            return;
        }
        
        if (event.getBlock().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        Material fromType = event.getBlock().getType();
        Material toType = event.getToBlock().getType();
        
        if (fromType == Material.END_PORTAL || fromType == Material.END_GATEWAY ||
            toType == Material.END_PORTAL || toType == Material.END_GATEWAY) {
            event.setCancelled(true);
            event.getToBlock().setType(Material.AIR);
        }
    }
}
