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
    
    // Containers that should keep their normal drops (preserve contents)
    private final Set<Material> containerBlocks = new HashSet<>();
    
    // Map to store what each block type drops (deterministic based on seed)
    private final Map<Material, Material> blockDropMapping = new HashMap<>();

    // Cache challenge flags for high-frequency events
    private boolean randomizerEnabled = false;
    private boolean connunityHuntEnabled = false;
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 5000; // 5 seconds

    // Cache player team lookup for Connunity Hunt mode
    private final Map<UUID, String> teamCache = new HashMap<>();
    private final Map<UUID, Long> teamCacheTime = new HashMap<>();
    private static final long TEAM_CACHE_DURATION = 10000; // 10 seconds
    
    public BlockBreakRandomizerListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        initializeMatchSeed();
        initializeNoDropBlocks();
        initializeContainerBlocks();
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
        teamCache.clear();
        teamCacheTime.clear();
        lastCacheUpdate = 0;
    }

    private void refreshChallengeCache() {
        Boolean randomizer = plugin.getDataManager().getSavedChallenge("block_break_randomizer");
        randomizerEnabled = randomizer != null && randomizer;

        Boolean connunity = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        connunityHuntEnabled = connunity != null && connunity;

        lastCacheUpdate = System.currentTimeMillis();
    }

    private String getCachedTeam(UUID playerId) {
        long now = System.currentTimeMillis();
        Long cacheTime = teamCacheTime.get(playerId);

        if (cacheTime == null || (now - cacheTime) > TEAM_CACHE_DURATION) {
            String team = plugin.getDataManager().getPlayerTeam(playerId);
            teamCache.put(playerId, team);
            teamCacheTime.put(playerId, now);
            return team;
        }

        return teamCache.get(playerId);
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
     * Initialize container blocks that should keep their normal drops
     */
    private void initializeContainerBlocks() {
        // All chest types
        containerBlocks.add(Material.CHEST);
        containerBlocks.add(Material.TRAPPED_CHEST);
        containerBlocks.add(Material.ENDER_CHEST);
        
        // Shulker boxes
        containerBlocks.add(Material.SHULKER_BOX);
        containerBlocks.add(Material.WHITE_SHULKER_BOX);
        containerBlocks.add(Material.ORANGE_SHULKER_BOX);
        containerBlocks.add(Material.MAGENTA_SHULKER_BOX);
        containerBlocks.add(Material.LIGHT_BLUE_SHULKER_BOX);
        containerBlocks.add(Material.YELLOW_SHULKER_BOX);
        containerBlocks.add(Material.LIME_SHULKER_BOX);
        containerBlocks.add(Material.PINK_SHULKER_BOX);
        containerBlocks.add(Material.GRAY_SHULKER_BOX);
        containerBlocks.add(Material.LIGHT_GRAY_SHULKER_BOX);
        containerBlocks.add(Material.CYAN_SHULKER_BOX);
        containerBlocks.add(Material.PURPLE_SHULKER_BOX);
        containerBlocks.add(Material.BLUE_SHULKER_BOX);
        containerBlocks.add(Material.BROWN_SHULKER_BOX);
        containerBlocks.add(Material.GREEN_SHULKER_BOX);
        containerBlocks.add(Material.RED_SHULKER_BOX);
        containerBlocks.add(Material.BLACK_SHULKER_BOX);
        
        // Other storage blocks
        containerBlocks.add(Material.BARREL);
        containerBlocks.add(Material.FURNACE);
        containerBlocks.add(Material.BLAST_FURNACE);
        containerBlocks.add(Material.SMOKER);
        containerBlocks.add(Material.DISPENSER);
        containerBlocks.add(Material.DROPPER);
        containerBlocks.add(Material.HOPPER);
        containerBlocks.add(Material.BREWING_STAND);
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
                plugin.logWarning("Invalid material in block_break_randomizer.excluded: " + itemName);
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
        
        plugin.logDebug("Block Break Randomizer: Initialized " + validItems.size() + " valid items");
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
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            refreshChallengeCache();
        }

        if (!randomizerEnabled) {
            return; // Challenge not enabled
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return; // Challenge not active or paused
        }
        
        Player player = event.getPlayer();
        Block block = event.getBlockState().getBlock();
        Material blockType = event.getBlockState().getType();
        
        // In Connunity Hunt mode, only apply randomizer to Streamers
        if (connunityHuntEnabled) {
            String team = getCachedTeam(player.getUniqueId());
            if (!"Streamer".equals(team)) {
                return; // Viewers get normal block drops
            }
        }
        
        // Skip creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        
        // Skip blocks that should drop nothing
        if (noDropBlocks.contains(blockType)) {
            return;
        }
        
        // Skip container blocks - let them drop their contents normally
        if (containerBlocks.contains(blockType)) {
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
