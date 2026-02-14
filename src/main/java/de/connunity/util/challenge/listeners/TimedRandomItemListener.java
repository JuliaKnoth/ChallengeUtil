package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.FoliaSchedulerUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Timed Random Item Challenge:
 * - Every 30 seconds, all online players receive a random item
 * - Items get progressively better over time (based on elapsed game time)
 * - Reuses excluded items from ChunkItemChallenge
 * - Uses OP loot pool from FriendlyFireItemListener with progressive tiers
 * - Better items drop later on in the challenge
 */
public class TimedRandomItemListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private final Random random = new Random();
    
    // Task that runs every second to check timer
    private Object itemTask = null;
    
    // Time tracking for progressive loot
    private long challengeStartTime = 0;
    private long totalElapsedTime = 0; // Total elapsed time excluding paused time
    private long pauseStartTime = 0; // When the challenge was paused
    private boolean isPaused = false;
    
    // Track last item give time to avoid duplicates
    private long lastItemGiveSecond = -1;
    
    // Loot pools based on elapsed time (progressive difficulty)
    private final Map<String, List<LootItem>> lootPools = new HashMap<>();
    
    // List of excluded items (reused from ChunkItemChallenge)
    private final Set<Material> excludedItems = new HashSet<>();
    
    public TimedRandomItemListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        initializeExcludedItems();
        initializeLootPools();
    }
    
    /**
     * Initialize excluded items (reused from ChunkItemChallenge)
     */
    private void initializeExcludedItems() {
        // Get excluded items from config, with defaults
        List<String> excludedList = plugin.getConfig().getStringList("challenge.chunk_items.excluded");
        
        if (excludedList.isEmpty()) {
            // Default excluded items - technical/game-breaking items only
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
            excludedItems.add(Material.DEBUG_STICK);
            excludedItems.add(Material.KNOWLEDGE_BOOK);
            excludedItems.add(Material.LIGHT);
            excludedItems.add(Material.END_PORTAL);
            excludedItems.add(Material.END_PORTAL_FRAME);
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
     * Initialize progressive loot pools
     * Time-based pools: early game (0-15 min), mid game (15-45 min), late game (45-90 min), end game (90+ min)
     */
    private void initializeLootPools() {
        // EARLY GAME (0-15 minutes): Very basic items, basic food, stone/copper tier only, NO iron or better
        List<LootItem> earlyGamePool = new ArrayList<>();
        // Basic food
        earlyGamePool.add(new LootItem(Material.BREAD, 2, 6));
        earlyGamePool.add(new LootItem(Material.COOKED_BEEF, 2, 4));
        earlyGamePool.add(new LootItem(Material.COOKED_CHICKEN, 2, 4));
        earlyGamePool.add(new LootItem(Material.BAKED_POTATO, 2, 4));
        earlyGamePool.add(new LootItem(Material.APPLE, 1, 3));
        // Basic resources
        earlyGamePool.add(new LootItem(Material.COAL, 4, 8));
        earlyGamePool.add(new LootItem(Material.COPPER_INGOT, 3, 8));
        earlyGamePool.add(new LootItem(Material.COBBLESTONE, 16, 32));
        earlyGamePool.add(new LootItem(Material.OAK_LOG, 8, 16));
        earlyGamePool.add(new LootItem(Material.STICK, 8, 16));
        earlyGamePool.add(new LootItem(Material.ARROW, 4, 8));
        earlyGamePool.add(new LootItem(Material.LEATHER, 2, 4));
        earlyGamePool.add(new LootItem(Material.FLINT, 2, 6));
        // Stone tools (no enchants)
        earlyGamePool.add(new LootItem(Material.STONE_PICKAXE, 1, 1));
        earlyGamePool.add(new LootItem(Material.STONE_AXE, 1, 1));
        earlyGamePool.add(new LootItem(Material.STONE_SWORD, 1, 1));
        earlyGamePool.add(new LootItem(Material.STONE_SHOVEL, 1, 1));
        // Wooden tools
        earlyGamePool.add(new LootItem(Material.WOODEN_PICKAXE, 1, 1));
        earlyGamePool.add(new LootItem(Material.WOODEN_AXE, 1, 1));
        earlyGamePool.add(new LootItem(Material.WOODEN_SWORD, 1, 1));
        // Basic utility
        earlyGamePool.add(new LootItem(Material.TORCH, 8, 16));
        earlyGamePool.add(new LootItem(Material.CRAFTING_TABLE, 1, 1));
        earlyGamePool.add(new LootItem(Material.CHEST, 1, 2));
        earlyGamePool.add(new LootItem(Material.BUCKET, 1, 1));
        lootPools.put("early", earlyGamePool);
        
        // MID GAME (15-45 minutes): Iron tier, better food, gold resources, ender pearls available
        List<LootItem> midGamePool = new ArrayList<>();
        // Better food
        midGamePool.add(new LootItem(Material.GOLDEN_CARROT, 4, 8));
        midGamePool.add(new LootItem(Material.COOKED_BEEF, 8, 16));
        midGamePool.add(new LootItem(Material.GOLDEN_APPLE, 1, 2));
        // Better resources
        midGamePool.add(new LootItem(Material.IRON_INGOT, 4, 8));
        midGamePool.add(new LootItem(Material.GOLD_INGOT, 4, 8));
        midGamePool.add(new LootItem(Material.IRON_BLOCK, 1, 2));
        midGamePool.add(new LootItem(Material.GOLD_BLOCK, 1, 2));
        midGamePool.add(new LootItem(Material.LAPIS_LAZULI, 8, 16));
        midGamePool.add(new LootItem(Material.REDSTONE, 8, 16));
        midGamePool.add(new LootItem(Material.ENDER_PEARL, 1, 4)); // Now available at 15 min
        // Iron tools with better enchantments
        midGamePool.add(new LootItem(Material.IRON_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 2, 3)
            .addEnchantment("unbreaking", 1, 2));
        midGamePool.add(new LootItem(Material.IRON_AXE, 1, 1)
            .addEnchantment("efficiency", 2, 3)
            .addEnchantment("sharpness", 2, 3));
        midGamePool.add(new LootItem(Material.IRON_SWORD, 1, 1)
            .addEnchantment("sharpness", 2, 3)
            .addEnchantment("looting", 1, 2));
        midGamePool.add(new LootItem(Material.IRON_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 2, 3));
        // Iron armor with better enchantments
        midGamePool.add(new LootItem(Material.IRON_HELMET, 1, 1)
            .addEnchantment("protection", 1, 2)
            .addEnchantment("unbreaking", 1, 2));
        midGamePool.add(new LootItem(Material.IRON_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 1, 2)
            .addEnchantment("unbreaking", 1, 2));
        midGamePool.add(new LootItem(Material.IRON_LEGGINGS, 1, 1)
            .addEnchantment("protection", 1, 2)
            .addEnchantment("unbreaking", 1, 2));
        midGamePool.add(new LootItem(Material.IRON_BOOTS, 1, 1)
            .addEnchantment("protection", 1, 2)
            .addEnchantment("feather_falling", 1, 2));
        // Ranged weapons
        midGamePool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 1, 3)
            .addEnchantment("punch", 1, 1));
        midGamePool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 1, 2));
        // Utility
        midGamePool.add(new LootItem(Material.ENCHANTING_TABLE, 1, 1));
        midGamePool.add(new LootItem(Material.OBSIDIAN, 2, 4));
        midGamePool.add(new LootItem(Material.ANVIL, 1, 1));
        midGamePool.add(new LootItem(Material.EXPERIENCE_BOTTLE, 4, 8));
        lootPools.put("mid", midGamePool);
        
        // LATE GAME (45-90 minutes): Diamond tier, blaze rods, high enchantments
        List<LootItem> lateGamePool = new ArrayList<>();
        // Premium food
        lateGamePool.add(new LootItem(Material.GOLDEN_APPLE, 2, 4));
        lateGamePool.add(new LootItem(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 200)); // Rare!
        // Diamond resources
        lateGamePool.add(new LootItem(Material.DIAMOND, 3, 6));
        lateGamePool.add(new LootItem(Material.EMERALD, 3, 6));
        lateGamePool.add(new LootItem(Material.DIAMOND_BLOCK, 1, 2));
        lateGamePool.add(new LootItem(Material.EMERALD_BLOCK, 1, 2));
        lateGamePool.add(new LootItem(Material.ENDER_PEARL, 2, 6));
        lateGamePool.add(new LootItem(Material.BLAZE_ROD, 2, 6)); // Now available at 45 min
        lateGamePool.add(new LootItem(Material.BLAZE_POWDER, 4, 8));
        // Diamond tools with high enchantments
        lateGamePool.add(new LootItem(Material.DIAMOND_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("fortune", 2, 3)
            .addEnchantment("unbreaking", 2, 3));
        lateGamePool.add(new LootItem(Material.DIAMOND_AXE, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("sharpness", 4, 5)
            .addEnchantment("unbreaking", 2, 3));
        lateGamePool.add(new LootItem(Material.DIAMOND_SWORD, 1, 1)
            .addEnchantment("sharpness", 4, 5)
            .addEnchantment("looting", 2, 3)
            .addEnchantment("fire_aspect", 1, 2));
        lateGamePool.add(new LootItem(Material.DIAMOND_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("unbreaking", 2, 3));
        // Diamond armor with high enchantments
        lateGamePool.add(new LootItem(Material.DIAMOND_HELMET, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("respiration", 2, 3)
            .addEnchantment("unbreaking", 2, 3));
        lateGamePool.add(new LootItem(Material.DIAMOND_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("unbreaking", 2, 3));
        lateGamePool.add(new LootItem(Material.DIAMOND_LEGGINGS, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("unbreaking", 2, 3));
        lateGamePool.add(new LootItem(Material.DIAMOND_BOOTS, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("feather_falling", 3, 4)
            .addEnchantment("unbreaking", 2, 3));
        // Ranged weapons
        lateGamePool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 4, 5)
            .addEnchantment("flame", 1, 1)
            .addEnchantment("punch", 2, 2));
        lateGamePool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 2, 3)
            .addEnchantment("multishot", 1, 1));
        lateGamePool.add(new LootItem(Material.TRIDENT, 1, 1)
            .addEnchantment("loyalty", 2, 3)
            .addEnchantment("impaling", 3, 4));
        // Utility
        lateGamePool.add(new LootItem(Material.FIREWORK_ROCKET, 16, 32));
        lateGamePool.add(new LootItem(Material.RESPAWN_ANCHOR, 1, 1));
        lateGamePool.add(new LootItem(Material.END_CRYSTAL, 2, 4));
        lateGamePool.add(new LootItem(Material.OBSIDIAN, 8, 16));
        lootPools.put("late", lateGamePool);
        
        // END GAME (90+ minutes): Extremely OP items - Netherite with MAX enchantments, ultra-rare items
        List<LootItem> endGamePool = new ArrayList<>();
        // Ultra rare food
        endGamePool.add(new LootItem(Material.GOLDEN_APPLE, 3, 6));
        endGamePool.add(new LootItem(Material.ENCHANTED_GOLDEN_APPLE, 1, 2));
        // Premium resources - Netherite available now!
        endGamePool.add(new LootItem(Material.NETHERITE_INGOT, 1, 3));
        endGamePool.add(new LootItem(Material.NETHERITE_SCRAP, 2, 4));
        endGamePool.add(new LootItem(Material.NETHERITE_BLOCK, 1, 1, 50)); // Rare!
        endGamePool.add(new LootItem(Material.DIAMOND_BLOCK, 2, 4));
        endGamePool.add(new LootItem(Material.EMERALD_BLOCK, 2, 4));
        endGamePool.add(new LootItem(Material.ENDER_PEARL, 4, 12));
        endGamePool.add(new LootItem(Material.NETHER_STAR, 1, 1, 500)); // Very rare!
        // Netherite tools with MAX enchantments
        endGamePool.add(new LootItem(Material.NETHERITE_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("fortune", 3, 3)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_AXE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("sharpness", 5, 5)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_SWORD, 1, 1)
            .addEnchantment("sharpness", 5, 5)
            .addEnchantment("looting", 3, 3)
            .addEnchantment("fire_aspect", 2, 2)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_HOE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("unbreaking", 3, 3));
        // Netherite armor with MAX enchantments
        endGamePool.add(new LootItem(Material.NETHERITE_HELMET, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("respiration", 3, 3)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_LEGGINGS, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        endGamePool.add(new LootItem(Material.NETHERITE_BOOTS, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("feather_falling", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        // Ultimate ranged weapons
        endGamePool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 5, 5)
            .addEnchantment("infinity", 1, 1)
            .addEnchantment("flame", 1, 1)
            .addEnchantment("punch", 2, 2)
            .addEnchantment("unbreaking", 3, 3));
        endGamePool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 3, 3)
            .addEnchantment("multishot", 1, 1)
            .addEnchantment("piercing", 4, 4)
            .addEnchantment("unbreaking", 3, 3));
        endGamePool.add(new LootItem(Material.TRIDENT, 1, 1)
            .addEnchantment("loyalty", 3, 3)
            .addEnchantment("impaling", 5, 5)
            .addEnchantment("channeling", 1, 1)
            .addEnchantment("unbreaking", 3, 3));
        // Ultra rare items
        endGamePool.add(new LootItem(Material.ELYTRA, 1, 1, 300)); // Very rare!
        endGamePool.add(new LootItem(Material.TOTEM_OF_UNDYING, 1, 1, 200)); // Very rare!
        endGamePool.add(new LootItem(Material.FIREWORK_ROCKET, 32, 64));
        endGamePool.add(new LootItem(Material.END_CRYSTAL, 4, 8));
        endGamePool.add(new LootItem(Material.OBSIDIAN, 16, 32));
        endGamePool.add(new LootItem(Material.ANCIENT_DEBRIS, 1, 2));
        lootPools.put("end", endGamePool);
    }
    
    /**
     * Start the challenge (called when /start is executed)
     */
    public void start() {
        challengeStartTime = System.currentTimeMillis();
        totalElapsedTime = 0;
        isPaused = false;
        pauseStartTime = 0;
        lastItemGiveSecond = -1;
        
        // Cancel any existing task
        if (itemTask != null) {
            FoliaSchedulerUtil.cancelTask(itemTask);
        }
        
        // Start task that checks every second (20 ticks) to see if timer is at :00 or :30
        itemTask = FoliaSchedulerUtil.runTaskTimer(plugin, () -> {
            checkAndGiveItems();
        }, 20L, 20L); // Check every second (20 ticks)
        
        plugin.getLogger().info("Timed Random Item Challenge started!");
    }
    
    /**
     * Pause the challenge (called when /pause is executed)
     */
    public void pause() {
        if (isPaused || itemTask == null) {
            return; // Already paused or not running
        }
        
        isPaused = true;
        pauseStartTime = System.currentTimeMillis();
        
        // Update total elapsed time before pausing
        if (challengeStartTime > 0) {
            totalElapsedTime += (pauseStartTime - challengeStartTime);
        }
        
        plugin.getLogger().info("Timed Random Item Challenge paused! Elapsed time: " + (totalElapsedTime / 1000) + "s");
    }
    
    /**
     * Resume the challenge (called when /pause is executed to unpause)
     */
    public void resume() {
        if (!isPaused || itemTask == null) {
            return; // Not paused or not running
        }
        
        isPaused = false;
        
        // Reset the start time to now (excluding paused duration)
        challengeStartTime = System.currentTimeMillis();
        pauseStartTime = 0;
        
        plugin.getLogger().info("Timed Random Item Challenge resumed! Total elapsed time: " + (totalElapsedTime / 1000) + "s");
    }
    
    /**
     * Stop the challenge (called when /reset is executed)
     */
    public void stop() {
        if (itemTask != null) {
            FoliaSchedulerUtil.cancelTask(itemTask);
            itemTask = null;
        }
        challengeStartTime = 0;
        totalElapsedTime = 0;
        isPaused = false;
        lastItemGiveSecond = -1;
        plugin.getLogger().info("Timed Random Item Challenge stopped!");
    }
    
    /**
     * Reset all challenge data
     */
    public void reset() {
        stop();
    }
    
    /**
     * Check timer and give items if at :00 or :30
     */
    private void checkAndGiveItems() {
        // Check if challenge is enabled
        Boolean challengeEnabled = plugin.getDataManager().getSavedChallenge("timed_random_item");
        if (challengeEnabled == null || !challengeEnabled) {
            return;
        }
        
        // Check if timer is running and not paused
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Get current timer seconds
        long totalSeconds = plugin.getTimerManager().getTotalSeconds();
        long currentSecond = totalSeconds % 60;
        
        // Check if we're at :00 or :30 and haven't already given items this second
        if ((currentSecond == 0 || currentSecond == 30) && lastItemGiveSecond != totalSeconds) {
            lastItemGiveSecond = totalSeconds;
            giveItemsToAllPlayers();
        }
    }
    
    /**
     * Give random items to all online players
     */
    private void giveItemsToAllPlayers() {
        // Check if challenge is enabled
        Boolean challengeEnabled = plugin.getDataManager().getSavedChallenge("timed_random_item");
        if (challengeEnabled == null || !challengeEnabled) {
            return;
        }
        
        // Check if timer is running and not paused
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Get elapsed time in minutes
        long elapsedMillis = System.currentTimeMillis() - challengeStartTime;
        long elapsedMinutes = elapsedMillis / (60 * 1000);
        
        // Determine which loot pool(s) to use with smooth transitions
        // Transition windows: 25-35 min (early->mid), 40-50 min (mid->late), 85-95 min (late->end)
        List<LootItem> pool;
        String poolKey;
        
        if (elapsedMinutes < 10) {
            // Pure early game (0-10 minutes)
            poolKey = "early";
            pool = lootPools.get(poolKey);
        } else if (elapsedMinutes < 20) {
            // Transition from early to mid (10-20 minutes)
            // Blend factor: 0.0 at 10 min (100% early) -> 1.0 at 20 min (100% mid)
            double blendFactor = (elapsedMinutes - 10) / 10.0;
            if (random.nextDouble() < blendFactor) {
                poolKey = "mid";
                pool = lootPools.get("mid");
            } else {
                poolKey = "early";
                pool = lootPools.get("early");
            }
        } else if (elapsedMinutes < 40) {
            // Pure mid game (20-40 minutes)
            poolKey = "mid";
            pool = lootPools.get(poolKey);
        } else if (elapsedMinutes < 50) {
            // Transition from mid to late (40-50 minutes)
            // Blend factor: 0.0 at 40 min (100% mid) -> 1.0 at 50 min (100% late)
            double blendFactor = (elapsedMinutes - 40) / 10.0;
            if (random.nextDouble() < blendFactor) {
                poolKey = "late";
                pool = lootPools.get("late");
            } else {
                poolKey = "mid";
                pool = lootPools.get("mid");
            }
        } else if (elapsedMinutes < 85) {
            // Pure late game (50-85 minutes)
            poolKey = "late";
            pool = lootPools.get(poolKey);
        } else if (elapsedMinutes < 95) {
            // Transition from late to end (85-95 minutes)
            // Blend factor: 0.0 at 85 min (100% late) -> 1.0 at 95 min (100% end)
            double blendFactor = (elapsedMinutes - 85) / 10.0;
            if (random.nextDouble() < blendFactor) {
                poolKey = "end";
                pool = lootPools.get("end");
            } else {
                poolKey = "late";
                pool = lootPools.get("late");
            }
        } else {
            // Pure end game (95+ minutes)
            poolKey = "end";
            pool = lootPools.get(poolKey);
        }
        
        if (pool == null || pool.isEmpty()) {
            return;
        }
        
        // Give an item to each online player
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOnline() && !player.isDead()) {
                giveRandomItem(player, pool, poolKey);
            }
        }
    }
    
    /**
     * Give a random item from the specified pool to a player
     */
    private void giveRandomItem(Player player, List<LootItem> pool, String poolKey) {
        // Try to get an item that should drop (respect drop chances)
        LootItem lootItem = null;
        int maxAttempts = 10; // Try up to 10 times to find an item that should drop
        for (int i = 0; i < maxAttempts; i++) {
            LootItem candidate = pool.get(random.nextInt(pool.size()));
            if (candidate.shouldDrop(random)) {
                lootItem = candidate;
                break;
            }
        }
        
        // If no item passed the drop chance check, fall back to a guaranteed item
        if (lootItem == null) {
            lootItem = pool.get(random.nextInt(pool.size()));
        }
        
        ItemStack item = lootItem.createItemStack(random);
        
        // Try to add to inventory (handles stacking automatically)
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        
        // Drop any items that couldn't fit
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        
        // Send chat message with item name and amount
        String itemName = getItemName(item.getType());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", String.valueOf(item.getAmount()));
        placeholders.put("item", itemName);
        player.sendMessage(lang.getComponent("timed-random-item.received", placeholders));
    }
    
    /**
     * Clean up when a player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nothing to clean up per-player in this challenge
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
    
    /**
     * Inner class to represent a loot item with enchantments
     */
    private static class LootItem {
        private final Material material;
        private final int minAmount;
        private final int maxAmount;
        private final List<EnchantmentData> enchantments = new ArrayList<>();
        private final int dropChance; // 1 = always drop, 100 = 1/100 chance, 250 = 1/250 chance
        
        public LootItem(Material material, int minAmount, int maxAmount) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.dropChance = 1; // Default: always drop
        }
        
        public LootItem(Material material, int minAmount, int maxAmount, int dropChance) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.dropChance = dropChance;
        }
        
        public LootItem addEnchantment(String enchantmentKey, int minLevel, int maxLevel) {
            enchantments.add(new EnchantmentData(enchantmentKey, minLevel, maxLevel));
            return this;
        }
        
        public boolean shouldDrop(Random random) {
            return random.nextInt(dropChance) == 0;
        }
        
        public ItemStack createItemStack(Random random) {
            int amount = minAmount == maxAmount ? minAmount : 
                         minAmount + random.nextInt(maxAmount - minAmount + 1);
            ItemStack item = new ItemStack(material, amount);
            
            // Apply enchantments (each enchantment has individual 50% chance)
            if (!enchantments.isEmpty() && item.getItemMeta() != null) {
                ItemMeta meta = item.getItemMeta();
                for (EnchantmentData enchData : enchantments) {
                    // Each enchantment has 50% chance to be applied
                    if (random.nextBoolean()) {
                        @SuppressWarnings("deprecation")
                        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchData.enchantmentKey));
                        if (enchantment != null) {
                            int level = enchData.minLevel == enchData.maxLevel ? enchData.minLevel :
                                       enchData.minLevel + random.nextInt(enchData.maxLevel - enchData.minLevel + 1);
                            meta.addEnchant(enchantment, level, true);
                        }
                    }
                }
                item.setItemMeta(meta);
            }
            
            return item;
        }
    }
    
    /**
     * Inner class to store enchantment data
     */
    private static class EnchantmentData {
        private final String enchantmentKey;
        private final int minLevel;
        private final int maxLevel;
        
        public EnchantmentData(String enchantmentKey, int minLevel, int maxLevel) {
            this.enchantmentKey = enchantmentKey;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
        }
    }
}
