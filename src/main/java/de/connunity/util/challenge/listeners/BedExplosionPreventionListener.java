package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Prevents bed explosions in the Nether and End dimensions.
 * When a player clicks on a bed in these dimensions, the bed is removed instead of exploding.
 */
public class BedExplosionPreventionListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    public BedExplosionPreventionListener(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBedInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        // Check if the clicked block is a bed
        Material blockType = clickedBlock.getType();
        if (!blockType.name().contains("BED")) {
            return;
        }
        
        Player player = event.getPlayer();
        World world = player.getWorld();
        
        // Check if player is in the Nether or End
        if (world.getEnvironment() != World.Environment.NETHER && 
            world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        // Cancel the event to prevent explosion
        event.setCancelled(true);
        
        // Get the bed's other half if it exists
        Block otherHalf = null;
        if (clickedBlock.getBlockData() instanceof Bed) {
            Bed bedData = (Bed) clickedBlock.getBlockData();
            Bed.Part part = bedData.getPart();
            org.bukkit.block.BlockFace facing = bedData.getFacing();
            
            if (part == Bed.Part.HEAD) {
                otherHalf = clickedBlock.getRelative(facing.getOppositeFace());
            } else {
                otherHalf = clickedBlock.getRelative(facing);
            }
        }
        
        // Remove both halves of the bed
        clickedBlock.breakNaturally();
        if (otherHalf != null && otherHalf.getType().name().contains("BED")) {
            otherHalf.breakNaturally();
        }
        
        plugin.logDebug("Prevented bed explosion for " + player.getName() + " in " + world.getName());
    }
}
