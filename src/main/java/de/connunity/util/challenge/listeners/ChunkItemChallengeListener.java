package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles the chunk item challenge:
 * - When entering a new chunk, players receive a random item
 * - Each chunk gives the same item to a player on re-entry
 * - Different players get different items from the same chunk
 * - In manhunt mode, hunters only start receiving items 10 minutes after /start
 */
public class ChunkItemChallengeListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    // Track the last chunk each player was in
    private final Map<UUID, String> playerLastChunk = new HashMap<>();
    
    // Track which item each player gets in each chunk (player -> chunk -> item)
    private final Map<UUID, Map<String, Material>> playerChunkItems = new HashMap<>();
    
    // Track number of items given per player to prevent memory leaks
    private final Map<UUID, Integer> playerItemCount = new HashMap<>();
    private static final int MAX_CHUNKS_PER_PLAYER = 1000; // Limit to prevent memory issues
    
    // Random generator for items
    private final Random random = new Random();
    
    // Start time for manhunt mode delay
    private long manhuntStartTime = 0;
    private static final long HUNTER_DELAY = 10 * 60 * 1000; // 10 minutes in milliseconds
    
    // List of excluded items (will be configurable later)
    private final Set<Material> excludedItems = new HashSet<>();
    
    // Cache valid items to avoid recreating list every time
    private List<Material> validItemsCache = null;
    
    // OPTIMIZATION: Cache challenge/manhunt enabled status to avoid constant disk reads
    private boolean challengeEnabled = false;
    private boolean manhuntEnabled = false;
    private long lastCacheUpdate = 0;
    private static final long CACHE_REFRESH_INTERVAL = 5000; // 5 seconds
    
    // OPTIMIZATION: Cache player teams to avoid constant disk reads
    private final Map<UUID, String> teamCache = new HashMap<>();
    private final Map<UUID, Long> teamCacheTime = new HashMap<>();
    private static final long TEAM_CACHE_DURATION = 10000; // 10 seconds
    
    public ChunkItemChallengeListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        initializeExcludedItems();
        initializeValidItemsCache();
    }
    
    /**
     * Initialize the list of excluded items
     */
    private void initializeExcludedItems() {
        // Get excluded items from config, with defaults
        List<String> excludedList = plugin.getConfig().getStringList("challenge.chunk_items.excluded");
        
        if (excludedList.isEmpty()) {
            // Default excluded items - high-value, game-breaking, or technical items
            excludedItems.add(Material.AIR);
            excludedItems.add(Material.VOID_AIR);
            excludedItems.add(Material.CAVE_AIR);
            excludedItems.add(Material.BARRIER);
            excludedItems.add(Material.BEDROCK);
            excludedItems.add(Material.COMMAND_BLOCK);
            excludedItems.add(Material.CHAIN_COMMAND_BLOCK);
            excludedItems.add(Material.REPEATING_COMMAND_BLOCK);
            excludedItems.add(Material.COMMAND_BLOCK_MINECART);
            excludedItems.add(Material.STRUCTURE_BLOCK);
            excludedItems.add(Material.STRUCTURE_VOID);
            excludedItems.add(Material.JIGSAW);
            excludedItems.add(Material.SPAWNER);
            excludedItems.add(Material.DRAGON_EGG);
            excludedItems.add(Material.ELYTRA);
            excludedItems.add(Material.TOTEM_OF_UNDYING);
            excludedItems.add(Material.NETHER_STAR);
            excludedItems.add(Material.ENCHANTED_GOLDEN_APPLE);
            excludedItems.add(Material.NETHERITE_BLOCK);
            excludedItems.add(Material.ENDER_DRAGON_SPAWN_EGG);
            excludedItems.add(Material.ENDER_EYE);
            excludedItems.add(Material.END_PORTAL);
            excludedItems.add(Material.END_PORTAL_FRAME);
            excludedItems.add(Material.DEBUG_STICK);
            excludedItems.add(Material.KNOWLEDGE_BOOK);
            excludedItems.add(Material.ENCHANTED_BOOK);
            excludedItems.add(Material.WRITTEN_BOOK);
            excludedItems.add(Material.LIGHT);
        } else {
            // Load from config
            for (String itemName : excludedList) {
                try {
                    Material material = Material.valueOf(itemName.toUpperCase());
                    excludedItems.add(material);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid excluded item in config: " + itemName);
                }
            }
        }
    }
    
    /**
     * Initialize the cache of valid items (called once on startup)
     */
    private void initializeValidItemsCache() {
        validItemsCache = new ArrayList<>();
        
        // Build list once instead of on every chunk change
        for (Material material : Material.values()) {
            if (material.isItem() && !excludedItems.contains(material)) {
                validItemsCache.add(material);
            }
        }
        
        plugin.getLogger().info("Cached " + validItemsCache.size() + " valid chunk items");
    }
    
    /**
     * Reset all tracking data when challenge is reset
     */
    public void reset() {
        playerLastChunk.clear();
        playerChunkItems.clear();
        playerItemCount.clear();
        manhuntStartTime = 0;
        teamCache.clear();
        teamCacheTime.clear();
        lastCacheUpdate = 0;
    }
    
    /**
     * Start the challenge (called when /start is executed)
     */
    public void start() {
        // Refresh cache when challenge starts
        refreshChallengeCache();
        
        // Set start time for manhunt mode delay
        manhuntStartTime = System.currentTimeMillis();
    }
    
    /**
     * OPTIMIZATION: Refresh challenge enabled cache from disk (called periodically)
     */
    private void refreshChallengeCache() {
        Boolean chunkItems = plugin.getDataManager().getSavedChallenge("chunk_items");
        challengeEnabled = chunkItems != null && chunkItems;
        
        Boolean manhunt = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        manhuntEnabled = manhunt != null && manhunt;
        
        lastCacheUpdate = System.currentTimeMillis();
    }
    
    /**
     * OPTIMIZATION: Get cached team for player (avoid constant disk reads)
     */
    private String getCachedTeam(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        Long cacheTime = teamCacheTime.get(playerId);
        
        // Refresh cache if expired or missing
        if (cacheTime == null || (currentTime - cacheTime) > TEAM_CACHE_DURATION) {
            String team = plugin.getDataManager().getPlayerTeam(playerId);
            teamCache.put(playerId, team);
            teamCacheTime.put(playerId, currentTime);
            return team;
        }
        
        return teamCache.get(playerId);
    }
    
    /**
     * Stop the challenge (called when timer is stopped/reset)
     */
    public void stop() {
        reset();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // CRITICAL PERFORMANCE: Check if player moved to a different chunk (not just a new block)
        // This is a chunk-based challenge, so we only care about chunk changes
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return; // Player is still in the same chunk - skip all processing
        }
        
        // OPTIMIZATION: Refresh cache periodically (avoid constant disk reads)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate > CACHE_REFRESH_INTERVAL) {
            refreshChallengeCache();
        }
        
        // Check cached challenge enabled status (no disk I/O)
        if (!challengeEnabled) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if player is a hunter in manhunt mode and if delay hasn't passed
        if (!canPlayerReceiveItems(player)) {
            return;
        }
        
        // Get current chunk
        Chunk currentChunk = player.getLocation().getChunk();
        String chunkKey = getChunkKey(currentChunk);
        
        // Check if player moved to a new chunk
        UUID playerId = player.getUniqueId();
        String lastChunk = playerLastChunk.get(playerId);
        
        if (!chunkKey.equals(lastChunk)) {
            // Player entered a new chunk
            playerLastChunk.put(playerId, chunkKey);
            
            // Give item to player
            giveChunkItem(player, chunkKey);
        }
    }
    
    /**
     * Clean up player data when they quit to prevent memory leaks
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        
        // Remove player's data to free memory
        playerLastChunk.remove(playerId);
        playerChunkItems.remove(playerId);
        playerItemCount.remove(playerId);
    }
    
    /**
     * Check if player can receive items based on manhunt mode and delay
     * OPTIMIZED: Uses cached values instead of disk reads
     */
    private boolean canPlayerReceiveItems(Player player) {
        // Check cached manhunt mode status (no disk I/O)
        if (!manhuntEnabled) {
            // Not in manhunt mode, everyone can receive items
            return true;
        }
        
        // Get player's cached team (minimal disk I/O)
        String team = getCachedTeam(player.getUniqueId());
        
        // Runners always receive items
        if ("runner".equals(team)) {
            return true;
        }
        
        // Hunters need to wait 10 minutes
        if ("hunter".equals(team)) {
            if (manhuntStartTime == 0) {
                return false; // Challenge hasn't started yet
            }
            
            long elapsed = System.currentTimeMillis() - manhuntStartTime;
            if (elapsed < HUNTER_DELAY) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Give a random item to the player for this chunk
     * OPTIMIZED: Memory leak prevention and entity spawn reduction
     */
    private void giveChunkItem(Player player, String chunkKey) {
        UUID playerId = player.getUniqueId();
        
        // Get or create the player's chunk-item mapping
        Map<String, Material> chunkItems = playerChunkItems.computeIfAbsent(playerId, k -> new HashMap<>());
        
        // MEMORY LEAK PREVENTION: Check if player has too many stored chunks
        Integer itemCount = playerItemCount.getOrDefault(playerId, 0);
        if (itemCount >= MAX_CHUNKS_PER_PLAYER) {
            // Clear old entries to prevent memory leak
            chunkItems.clear();
            playerItemCount.put(playerId, 0);
            plugin.getLogger().info("Cleared chunk item cache for " + player.getName() + " (reached limit)");
        }
        
        // Check if player has already received an item for this chunk
        Material item = chunkItems.get(chunkKey);
        
        if (item == null) {
            // Generate a new random item for this chunk
            item = getRandomItem();
            chunkItems.put(chunkKey, item);
            playerItemCount.put(playerId, itemCount + 1);
        }
        
        // Give the item to the player
        ItemStack itemStack = new ItemStack(item, 1);
        
        // OPTIMIZATION: Check if item can be added (either empty slot or stackable)
        if (canAddItemToInventory(player, itemStack)) {
            // Add to inventory (fast operation)
            player.getInventory().addItem(itemStack);
            /* Commented out to reduce chat spam
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", getItemName(item));
            player.sendMessage(lang.getComponent("chunkitem.item-received", placeholders));
            */
        } else {
            // CRITICAL LAG FIX: Don't drop items when inventory full!
            // Instead, notify player
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("item", getItemName(item));
            player.sendMessage(lang.getComponent("chunkitem.inventory-full", placeholders));
        }
    }
    
    /**
     * Check if an item can be added to the player's inventory
     * This includes checking for empty slots AND stackable items
     */
    private boolean canAddItemToInventory(Player player, ItemStack itemToAdd) {
        // First check if there's an empty slot
        if (player.getInventory().firstEmpty() != -1) {
            return true;
        }
        
        // No empty slots - check if item can be stacked with existing items
        for (ItemStack inventoryItem : player.getInventory().getStorageContents()) {
            if (inventoryItem != null && inventoryItem.getType() == itemToAdd.getType()) {
                // Check if items are similar (same type, same meta)
                if (inventoryItem.isSimilar(itemToAdd)) {
                    // Check if there's room to stack
                    int maxStackSize = inventoryItem.getMaxStackSize();
                    if (inventoryItem.getAmount() < maxStackSize) {
                        return true; // Can stack with this item
                    }
                }
            }
        }
        
        return false; // No empty slots and can't stack
    }
    
    /**
     * Get a random item that is not excluded (using cached list for performance)
     */
    private Material getRandomItem() {
        if (validItemsCache == null || validItemsCache.isEmpty()) {
            // Fallback to dirt if cache is empty (should never happen)
            return Material.DIRT;
        }
        
        return validItemsCache.get(random.nextInt(validItemsCache.size()));
    }
    
    /**
     * Get a unique key for a chunk (optimized to reduce string allocation)
     */
    private String getChunkKey(Chunk chunk) {
        // Use StringBuilder to reduce string allocation garbage
        return new StringBuilder(32)
            .append(chunk.getWorld().getName())
            .append('_')
            .append(chunk.getX())
            .append('_')
            .append(chunk.getZ())
            .toString();
    }
    
    /**
     * Get a readable name for a material
     */
    private String getItemName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        
        // Capitalize each word
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1));
                }
            }
        }
        
        return result.toString();
    }
}
