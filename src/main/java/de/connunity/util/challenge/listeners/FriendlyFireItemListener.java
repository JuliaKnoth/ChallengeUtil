package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.FoliaSchedulerUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Friendly Fire = Item Challenge:
 * - When a player damages a teammate, the damage is synced (both take the same damage)
 * - The damaged teammate receives OP items based on their health (lower health = better items)
 * - Items are selected from predefined loot pools with enchantments
 * - Armor durability is not affected by friendly fire damage
 */
public class FriendlyFireItemListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private final Random random = new Random();
    
    // Loot pools based on health percentage
    private final Map<String, List<LootItem>> lootPools = new HashMap<>();
    
    // Track players with friendly fire penalty (UUID -> penalty end time in milliseconds)
    private final Map<UUID, Long> penaltyPlayers = new HashMap<>();
    private static final long PENALTY_DURATION = 30 * 1000; // 1 minute in milliseconds
    
    // Track players in PvP with enemy teams (UUID -> PvP end time in milliseconds)
    private final Map<UUID, Long> pvpPlayers = new HashMap<>();
    private static final long PVP_PAUSE_DURATION = 5 * 1000; // 5 seconds in milliseconds
    private static final double FRIENDLY_FIRE_DAMAGE = 1.0; // Half a heart (0.5 hearts = 1.0 damage)
    
    public FriendlyFireItemListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        initializeLootPools();
    }
    
    /**
     * Initialize loot pools with OP items
     */
    private void initializeLootPools() {
        // HIGH HEALTH (80-100%): Basic useful items - Iron tier, basic enchantments
        List<LootItem> highHealthPool = new ArrayList<>();
        // Food
        highHealthPool.add(new LootItem(Material.COOKED_BEEF, 8, 16));
        highHealthPool.add(new LootItem(Material.GOLDEN_CARROT, 4, 8));
        // Basic resources
        highHealthPool.add(new LootItem(Material.IRON_INGOT, 4, 8));
        highHealthPool.add(new LootItem(Material.IRON_BLOCK, 1, 2));
        highHealthPool.add(new LootItem(Material.COPPER_BLOCK, 2, 4));
        highHealthPool.add(new LootItem(Material.LAPIS_BLOCK, 1, 3));
        highHealthPool.add(new LootItem(Material.ARROW, 16, 32));
        highHealthPool.add(new LootItem(Material.ENDER_PEARL, 1, 4));
        // Iron tools with low enchantments
        highHealthPool.add(new LootItem(Material.IRON_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_AXE, 1, 1)
            .addEnchantment("efficiency", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_SWORD, 1, 1)
            .addEnchantment("sharpness", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 1, 2));
        // Iron armor with low enchantments
        highHealthPool.add(new LootItem(Material.IRON_HELMET, 1, 1)
            .addEnchantment("protection", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_LEGGINGS, 1, 1)
            .addEnchantment("protection", 1, 2));
        highHealthPool.add(new LootItem(Material.IRON_BOOTS, 1, 1)
            .addEnchantment("protection", 1, 2));
        // Ranged weapons
        highHealthPool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 1, 2));
        highHealthPool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 1, 1));
        // Utility
        highHealthPool.add(new LootItem(Material.SHIELD, 1, 1));
        highHealthPool.add(new LootItem(Material.WATER_BUCKET, 1, 2));
        highHealthPool.add(new LootItem(Material.LAVA_BUCKET, 1, 1));
        highHealthPool.add(new LootItem(Material.GLOWSTONE, 4, 8));
        lootPools.put("high", highHealthPool);
        
        // MEDIUM HEALTH (50-79%): Better items - Diamond tier, medium enchantments
        List<LootItem> mediumHealthPool = new ArrayList<>();
        // Food & Resources
        mediumHealthPool.add(new LootItem(Material.GOLDEN_APPLE, 1, 2));
        mediumHealthPool.add(new LootItem(Material.DIAMOND, 2, 4));
        mediumHealthPool.add(new LootItem(Material.EMERALD, 2, 4));
        mediumHealthPool.add(new LootItem(Material.GOLD_BLOCK, 1, 2));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_BLOCK, 1, 1));
        mediumHealthPool.add(new LootItem(Material.EMERALD_BLOCK, 1, 1));
        // Diamond tools with medium enchantments
        mediumHealthPool.add(new LootItem(Material.DIAMOND_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 3, 4)
            .addEnchantment("unbreaking", 2, 3));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_AXE, 1, 1)
            .addEnchantment("efficiency", 3, 4)
            .addEnchantment("sharpness", 2, 3));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_SWORD, 1, 1)
            .addEnchantment("sharpness", 3, 4)
            .addEnchantment("looting", 1, 2));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 3, 4));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_HOE, 1, 1)
            .addEnchantment("efficiency", 3, 4));
        // Diamond armor with medium enchantments
        mediumHealthPool.add(new LootItem(Material.DIAMOND_HELMET, 1, 1)
            .addEnchantment("protection", 2, 3)
            .addEnchantment("unbreaking", 2, 3));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 2, 3)
            .addEnchantment("unbreaking", 2, 3));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_LEGGINGS, 1, 1)
            .addEnchantment("protection", 2, 3)
            .addEnchantment("unbreaking", 2, 3));
        mediumHealthPool.add(new LootItem(Material.DIAMOND_BOOTS, 1, 1)
            .addEnchantment("protection", 2, 3)
            .addEnchantment("feather_falling", 2, 3));
        // Ranged weapons
        mediumHealthPool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 3, 4)
            .addEnchantment("punch", 1, 2));
        mediumHealthPool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 2, 3)
            .addEnchantment("piercing", 1, 2));
        mediumHealthPool.add(new LootItem(Material.SPECTRAL_ARROW, 8, 16));
        // Utility
        mediumHealthPool.add(new LootItem(Material.ENCHANTING_TABLE, 1, 1));
        mediumHealthPool.add(new LootItem(Material.OBSIDIAN, 8, 16));
        mediumHealthPool.add(new LootItem(Material.END_CRYSTAL, 2, 4));
        mediumHealthPool.add(new LootItem(Material.FIREWORK_ROCKET, 16, 32));
        lootPools.put("medium", mediumHealthPool);
        
        // LOW HEALTH (25-49%): Strong items - Diamond/Netherite tier, high enchantments
        List<LootItem> lowHealthPool = new ArrayList<>();
        // Premium food & resources
        lowHealthPool.add(new LootItem(Material.GOLDEN_APPLE, 2, 4));
        lowHealthPool.add(new LootItem(Material.DIAMOND, 4, 8));
        lowHealthPool.add(new LootItem(Material.EMERALD, 4, 8));
        lowHealthPool.add(new LootItem(Material.NETHERITE_SCRAP, 1, 2));
        lowHealthPool.add(new LootItem(Material.DIAMOND_BLOCK, 1, 2));
        lowHealthPool.add(new LootItem(Material.EMERALD_BLOCK, 1, 2));
        lowHealthPool.add(new LootItem(Material.ENDER_PEARL, 1, 4));
        // Diamond tools with high enchantments
        lowHealthPool.add(new LootItem(Material.DIAMOND_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("fortune", 2, 3)
            .addEnchantment("unbreaking", 3, 3));
        lowHealthPool.add(new LootItem(Material.DIAMOND_AXE, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("sharpness", 4, 5)
            .addEnchantment("unbreaking", 3, 3));
        lowHealthPool.add(new LootItem(Material.DIAMOND_SWORD, 1, 1)
            .addEnchantment("sharpness", 4, 5)
            .addEnchantment("looting", 2, 3)
            .addEnchantment("fire_aspect", 1, 2));
        lowHealthPool.add(new LootItem(Material.DIAMOND_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 4, 5)
            .addEnchantment("unbreaking", 3, 3));
        // Diamond armor with high enchantments
        lowHealthPool.add(new LootItem(Material.DIAMOND_HELMET, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("respiration", 2, 3)
            .addEnchantment("unbreaking", 3, 3));
        lowHealthPool.add(new LootItem(Material.DIAMOND_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("unbreaking", 3, 3));
        lowHealthPool.add(new LootItem(Material.DIAMOND_LEGGINGS, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("unbreaking", 3, 3));
        lowHealthPool.add(new LootItem(Material.DIAMOND_BOOTS, 1, 1)
            .addEnchantment("protection", 3, 4)
            .addEnchantment("feather_falling", 3, 4)
            .addEnchantment("unbreaking", 3, 3));
        // Ranged weapons
        lowHealthPool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 4, 5)
            .addEnchantment("flame", 1, 1)
            .addEnchantment("punch", 2, 2));
        lowHealthPool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 3, 3)
            .addEnchantment("multishot", 1, 1));
        lowHealthPool.add(new LootItem(Material.TRIDENT, 1, 1)
            .addEnchantment("loyalty", 2, 3)
            .addEnchantment("impaling", 3, 4));
        // Utility
        lowHealthPool.add(new LootItem(Material.FIREWORK_ROCKET, 2, 32));
        lowHealthPool.add(new LootItem(Material.RESPAWN_ANCHOR, 1, 1));
        lowHealthPool.add(new LootItem(Material.OBSIDIAN, 2, 12));
        lootPools.put("low", lowHealthPool);
        
        // CRITICAL HEALTH (1-24%): Extremely OP items - Max enchantments, rare items
        List<LootItem> criticalHealthPool = new ArrayList<>();
        // Ultra rare food
        criticalHealthPool.add(new LootItem(Material.GOLDEN_APPLE, 3, 6));
        criticalHealthPool.add(new LootItem(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 100)); // 1/100 chance!
        // Premium resources
        criticalHealthPool.add(new LootItem(Material.NETHERITE_INGOT, 1, 2));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_BLOCK, 1, 1));
        criticalHealthPool.add(new LootItem(Material.DIAMOND_BLOCK, 1, 3));
        criticalHealthPool.add(new LootItem(Material.EMERALD_BLOCK, 2, 3));
        criticalHealthPool.add(new LootItem(Material.ENDER_PEARL, 2, 12));
        // Netherite tools with MAX enchantments
        criticalHealthPool.add(new LootItem(Material.NETHERITE_PICKAXE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("fortune", 3, 3)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_AXE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("sharpness", 5, 5)
            .addEnchantment("unbreaking", 3, 3));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_SWORD, 1, 1)
            .addEnchantment("sharpness", 5, 5)
            .addEnchantment("looting", 3, 3)
            .addEnchantment("fire_aspect", 2, 2)
            .addEnchantment("unbreaking", 3, 3));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_SHOVEL, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("unbreaking", 3, 3));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_HOE, 1, 1)
            .addEnchantment("efficiency", 5, 5)
            .addEnchantment("unbreaking", 3, 3));
        // Netherite armor with MAX enchantments
        criticalHealthPool.add(new LootItem(Material.NETHERITE_HELMET, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("respiration", 3, 3)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_CHESTPLATE, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_LEGGINGS, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        criticalHealthPool.add(new LootItem(Material.NETHERITE_BOOTS, 1, 1)
            .addEnchantment("protection", 4, 4)
            .addEnchantment("feather_falling", 4, 4)
            .addEnchantment("unbreaking", 3, 3)
            .addEnchantment("mending", 1, 1));
        // Ultimate ranged weapons
        criticalHealthPool.add(new LootItem(Material.BOW, 1, 1)
            .addEnchantment("power", 5, 5)
            .addEnchantment("infinity", 1, 1)
            .addEnchantment("flame", 1, 1)
            .addEnchantment("punch", 2, 2));
        criticalHealthPool.add(new LootItem(Material.CROSSBOW, 1, 1)
            .addEnchantment("quick_charge", 3, 3)
            .addEnchantment("multishot", 1, 1)
            .addEnchantment("piercing", 4, 4));
        criticalHealthPool.add(new LootItem(Material.TRIDENT, 1, 1)
            .addEnchantment("loyalty", 3, 3)
            .addEnchantment("impaling", 5, 5)
            .addEnchantment("channeling", 1, 1));
        // Ultra rare items (sehr selten!)
        criticalHealthPool.add(new LootItem(Material.ELYTRA, 1, 1, 250)); // 1/250 chance!
        criticalHealthPool.add(new LootItem(Material.FIREWORK_ROCKET, 16, 64));
        criticalHealthPool.add(new LootItem(Material.END_CRYSTAL, 4, 8));
        criticalHealthPool.add(new LootItem(Material.OBSIDIAN, 8, 32));
        lootPools.put("critical", criticalHealthPool);
    }
    
    /**
     * Handle friendly fire damage and enemy PvP tracking
     */
    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        // Check if friendly fire challenge is enabled
        Boolean challengeEnabled = plugin.getDataManager().getSavedChallenge("friendly_fire_item");
        if (challengeEnabled == null || !challengeEnabled) {
            return;
        }
        
        // Check if timer is running and not paused
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        // Check if both entities are players
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        
        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();
        
        // Get teams (null means no team assigned, treat as solo)
        String victimTeam = plugin.getDataManager().getPlayerTeam(victim.getUniqueId());
        String attackerTeam = plugin.getDataManager().getPlayerTeam(attacker.getUniqueId());
        
        // Check if this is friendly fire (teammates attacking each other)
        boolean isFriendlyFire = victimTeam != null && attackerTeam != null && victimTeam.equals(attackerTeam);
        
        if (isFriendlyFire) {
            // FRIENDLY FIRE - teammates damaging each other
            
            // Check if either player is in PvP pause (recently fought an enemy)
            long currentTime = System.currentTimeMillis();
            Long victimPvpEnd = pvpPlayers.get(victim.getUniqueId());
            Long attackerPvpEnd = pvpPlayers.get(attacker.getUniqueId());
            
            // Clean up expired PvP timers
            if (victimPvpEnd != null && currentTime >= victimPvpEnd) {
                pvpPlayers.remove(victim.getUniqueId());
                victimPvpEnd = null;
            }
            if (attackerPvpEnd != null && currentTime >= attackerPvpEnd) {
                pvpPlayers.remove(attacker.getUniqueId());
                attackerPvpEnd = null;
            }
            
            // Check if either player is still in PvP pause
            if ((victimPvpEnd != null && currentTime < victimPvpEnd) || 
                (attackerPvpEnd != null && currentTime < attackerPvpEnd)) {
                // PvP pause is active - COMPLETELY DISABLE friendly fire
                // Cancel the event so NO damage is dealt between teammates
                event.setCancelled(true);
                return;
            }
            
            // Require at least 1 damage (0.5 hearts) to trigger item rewards
            if (event.getDamage() < 1.0) {
                return; // No reward for tiny damage
            }
            
            // Cancel the event to prevent normal damage and armor durability loss
            event.setCancelled(true);
            
            // Manually apply FIXED half-heart damage to both players (schedule for next tick to avoid issues)
            FoliaSchedulerUtil.runTask(plugin, () -> {
                // Apply FIXED damage to victim (always half a heart)
                if (victim.isOnline() && !victim.isDead()) {
                    double newVictimHealth = Math.max(0.0, victim.getHealth() - FRIENDLY_FIRE_DAMAGE);
                    victim.setHealth(newVictimHealth);
                    
                    // Give item to victim based on their health AFTER damage
                    if (!victim.isDead()) {
                        giveHealthBasedItem(victim);
                    }
                }
                
                // Sync FIXED damage to attacker (always half a heart)
                if (attacker.isOnline() && !attacker.isDead()) {
                    double newAttackerHealth = Math.max(0.0, attacker.getHealth() - FRIENDLY_FIRE_DAMAGE);
                    attacker.setHealth(newAttackerHealth);
                }
            });
        } else if (victimTeam != null && attackerTeam != null && !victimTeam.equals(attackerTeam)) {
            // ENEMY PVP - players from different teams fighting
            // Mark both players as being in PvP for 5 seconds
            long pvpEndTime = System.currentTimeMillis() + PVP_PAUSE_DURATION;
            pvpPlayers.put(victim.getUniqueId(), pvpEndTime);
            pvpPlayers.put(attacker.getUniqueId(), pvpEndTime);
            
            // Normal damage applies, no friendly fire mechanics
            // Don't cancel the event - let normal PvP damage go through
        }
        // If one or both have no team, treat as normal combat - no friendly fire mechanics
    }
    
    /**
     * Prevent item drops when hunters kill each other in manhunt mode
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Check if friendly fire challenge is enabled
        Boolean challengeEnabled = plugin.getDataManager().getSavedChallenge("friendly_fire_item");
        if (challengeEnabled == null || !challengeEnabled) {
            return;
        }
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled == null || !manhuntEnabled) {
            return;
        }
        
        // Check if timer is running
        if (!plugin.getTimerManager().isRunning() || plugin.getTimerManager().isPaused()) {
            return;
        }
        
        Player victim = event.getPlayer();
        
        // Check if victim is a hunter
        String victimTeam = plugin.getDataManager().getPlayerTeam(victim.getUniqueId());
        if (!"hunter".equals(victimTeam)) {
            return; // Not a hunter, normal death mechanics
        }
        
        // Check if killed by another player
        Player killer = victim.getKiller();
        if (killer == null) {
            return; // Not killed by a player, normal death mechanics
        }
        
        // Check if killer is also a hunter (friendly fire)
        String killerTeam = plugin.getDataManager().getPlayerTeam(killer.getUniqueId());
        if (!"hunter".equals(killerTeam)) {
            return; // Killed by non-hunter, normal death mechanics
        }
        
        // This is friendly fire between hunters - apply penalty but don't clear items
        // Apply 1 minute penalty to both players (no friendly fire items)
        long penaltyEndTime = System.currentTimeMillis() + PENALTY_DURATION;
        penaltyPlayers.put(victim.getUniqueId(), penaltyEndTime);
        penaltyPlayers.put(killer.getUniqueId(), penaltyEndTime);
        
        // Send message to both players
        victim.sendMessage(lang.getComponent("friendlyfire.penalty-victim"));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", victim.getName());
        killer.sendMessage(lang.getComponent("friendlyfire.penalty-attacker", placeholders));
        killer.sendMessage(lang.getComponent("friendlyfire.penalty-victim"));
    }
    
    /**
     * Give an item to a player based on their current health
     */
    private void giveHealthBasedItem(Player player) {
        // Check if player has a penalty for hunter-on-hunter friendly fire
        Long penaltyEndTime = penaltyPlayers.get(player.getUniqueId());
        if (penaltyEndTime != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime < penaltyEndTime) {
                // Player is still under penalty
                long remainingSeconds = (penaltyEndTime - currentTime) / 1000;
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("seconds", String.valueOf(remainingSeconds));
                player.sendMessage(lang.getComponent("friendlyfire.penalty-time-remaining", placeholders));
                return;
            } else {
                // Penalty expired, remove it
                penaltyPlayers.remove(player.getUniqueId());
            }
        }
        
        double health = player.getHealth();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double healthPercent = (health / maxHealth) * 100.0;
        
        // Determine which loot pool to use
        String poolKey;
        String healthStatus;
        NamedTextColor color;
        
        if (healthPercent >= 65.0) {
            poolKey = "high";
            healthStatus = "Guter Zustand";
            color = NamedTextColor.GREEN;
        } else if (healthPercent >= 40.0) {
            poolKey = "medium";
            healthStatus = "Mittlerer Zustand";
            color = NamedTextColor.YELLOW;
        } else if (healthPercent >= 20.0) {
            poolKey = "low";
            healthStatus = "Kritisch";
            color = NamedTextColor.GOLD;
        } else {
            poolKey = "critical";
            healthStatus = "EXTREM KRITISCH";
            color = NamedTextColor.RED;
        }
        
        // Get random item from the appropriate loot pool
        List<LootItem> pool = lootPools.get(poolKey);
        if (pool == null || pool.isEmpty()) {
            return;
        }
        
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
        
        // Try to add to inventory
        int emptySlot = player.getInventory().firstEmpty();
        if (emptySlot == -1) {
            // Inventory full, drop at player's location
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        } else {
            // Add to inventory silently
            player.getInventory().addItem(item);
            
            // Play sound based on loot tier
            org.bukkit.Sound sound;
            float pitch;
            switch (poolKey) {
                case "critical":
                    sound = org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
                    pitch = 2.0f;
                    break;
                case "low":
                    sound = org.bukkit.Sound.ENTITY_PLAYER_LEVELUP;
                    pitch = 1.5f;
                    break;
                case "medium":
                    sound = org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                    pitch = 1.2f;
                    break;
                default:
                    sound = org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                    pitch = 1.0f;
            }
            player.playSound(player.getLocation(), sound, 1.0f, pitch);
        }
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
