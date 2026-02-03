package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Block Break Randomizer Challenge
 * - Every block broken drops a random item instead of normal drops
 * - Same randomization for ALL players (seed-based)
 * - Different each match (seed resets with /fullreset)
 * - No OP loot - only regular obtainable items
 */
public class BlockBreakRandomizerListener implements Listener {
    
    private final ChallengeUtil plugin;
    
    // Seed for this match - same for all players, changes each match
    private long matchSeed;
    
    // Cache of valid items (excluding OP/technical items)
    private List<Material> validItems = null;
    
    // Blocks that should drop nothing when broken (air, fire, etc.)
    private final Set<Material> noDropBlocks = new HashSet<>();
    
    // Map to store what each block type drops (deterministic based on seed)
    private final Map<Material, Material> blockDropMapping = new HashMap<>();
    
    public BlockBreakRandomizerListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        initializeMatchSeed();
        initializeNoDropBlocks();
        initializeValidItems();
    }
    
    /**
     * Initialize the match seed - called on plugin startup and reset
     */
    private void initializeMatchSeed() {
        // Use current world seed + timestamp for uniqueness each match
        // This will be the same for all players in this match
        matchSeed = System.currentTimeMillis();
    }
    
    /**
     * Reset the randomizer for a new match
     * Called by ChallengeUtil when /fullreset happens
     */
    public void resetForNewMatch() {
        matchSeed = System.currentTimeMillis();
        blockDropMapping.clear(); // Clear cached mappings
    }
    
    /**
     * Initialize blocks that should never drop items
     */
    private void initializeNoDropBlocks() {
        noDropBlocks.add(Material.AIR);
        noDropBlocks.add(Material.CAVE_AIR);
        noDropBlocks.add(Material.VOID_AIR);
        noDropBlocks.add(Material.FIRE);
        noDropBlocks.add(Material.SOUL_FIRE);
        noDropBlocks.add(Material.WATER);
        noDropBlocks.add(Material.LAVA);
    }
    
    /**
     * Initialize list of valid items (excluding OP/technical items)
     */
    private void initializeValidItems() {
        validItems = new ArrayList<>();
        
        // Get excluded items from config
        List<String> excludedList = plugin.getConfig().getStringList("challenge.block_break_randomizer.excluded");
        Set<Material> excluded = new HashSet<>();
        
        // Add user-defined excluded items
        for (String itemName : excludedList) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                excluded.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in block_break_randomizer.excluded: " + itemName);
            }
        }
        
        // Default exclusions (OP items, technical blocks, etc.)
        excluded.add(Material.AIR);
        excluded.add(Material.CAVE_AIR);
        excluded.add(Material.VOID_AIR);
        excluded.add(Material.BARRIER);
        excluded.add(Material.BEDROCK);
        excluded.add(Material.COMMAND_BLOCK);
        excluded.add(Material.CHAIN_COMMAND_BLOCK);
        excluded.add(Material.REPEATING_COMMAND_BLOCK);
        excluded.add(Material.COMMAND_BLOCK_MINECART);
        excluded.add(Material.STRUCTURE_BLOCK);
        excluded.add(Material.STRUCTURE_VOID);
        excluded.add(Material.JIGSAW);
        excluded.add(Material.SPAWNER);
        excluded.add(Material.END_PORTAL);
        excluded.add(Material.END_PORTAL_FRAME);
        excluded.add(Material.END_GATEWAY);
        excluded.add(Material.NETHER_PORTAL);
        excluded.add(Material.BUDDING_AMETHYST);
        excluded.add(Material.REINFORCED_DEEPSLATE);
        excluded.add(Material.ENCHANTED_GOLDEN_APPLE); // No OP loot
        excluded.add(Material.NETHERITE_BLOCK); // Too OP
        excluded.add(Material.ANCIENT_DEBRIS); // Too valuable
        excluded.add(Material.ELYTRA); // Too rare/valuable
        excluded.add(Material.TOTEM_OF_UNDYING); // Too OP
        excluded.add(Material.DRAGON_EGG); // Too rare
        excluded.add(Material.DRAGON_HEAD); // Too rare
        excluded.add(Material.NETHER_STAR); // Too valuable
        excluded.add(Material.BEACON); // Too valuable
        
        // Add all valid items
        for (Material material : Material.values()) {
            if (material.isItem() && !excluded.contains(material)) {
                validItems.add(material);
            }
        }
        
        plugin.getLogger().info("Block Break Randomizer: Initialized " + validItems.size() + " valid items");
    }
    
    /**
     * Get the randomized drop for a block type
     * Same block type always drops the same item (based on match seed)
     */
    private Material getRandomizedDrop(Material blockType) {
        // Check cache first
        if (blockDropMapping.containsKey(blockType)) {
            return blockDropMapping.get(blockType);
        }
        
        // Generate deterministic random item based on block type and match seed
        Random random = new Random(matchSeed + blockType.ordinal());
        Material randomItem = validItems.get(random.nextInt(validItems.size()));
        
        // Cache the result
        blockDropMapping.put(blockType, randomItem);
        
        return randomItem;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDropItem(BlockDropItemEvent event) {
        // Check if challenge is enabled
        Boolean randomizerEnabled = plugin.getDataManager().getSavedChallenge("block_break_randomizer");
        if (randomizerEnabled == null || !randomizerEnabled) {
            return; // Challenge not enabled
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning()) {
            return; // Challenge not active
        }
        
        Player player = event.getPlayer();
        Block block = event.getBlockState().getBlock();
        Material blockType = event.getBlockState().getType();
        
        // Skip creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        // Skip blocks that should drop nothing
        if (noDropBlocks.contains(blockType)) {
            return;
        }
        
        // Get randomized drop for this block type
        Material randomDrop = getRandomizedDrop(blockType);
        
        // Clear all existing drops
        event.getItems().clear();
        
        // Spawn the randomized item at the block location
        Item droppedItem = block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(randomDrop, 1));
        
        // Add it to the event's item list so it's tracked properly
        event.getItems().add(droppedItem);
    }
}
