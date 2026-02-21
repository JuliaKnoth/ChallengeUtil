package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.PortalCreateEvent.CreateReason;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

/**
 * Handles Ender Dragon death in Manhunt mode and Custom End Fight
 */
public class EnderDragonDeathListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public EnderDragonDeathListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnderDragonDeath(EntityDeathEvent event) {
        // Check if the entity is an Ender Dragon
        if (event.getEntityType() != EntityType.ENDER_DRAGON) {
            return;
        }
        
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        boolean isTimerPaused = plugin.getTimerManager().isPaused();
        if (!isTimerRunning || isTimerPaused) {
            return;
        }
        
        // Check if custom end fight is enabled AND team race mode is enabled
        Boolean customEndFightEnabled = plugin.getDataManager().getSavedChallenge("custom_end_fight");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (customEndFightEnabled != null && customEndFightEnabled && 
            teamRaceEnabled != null && teamRaceEnabled) {
            handleCustomEndFight(event);
            return;
        }
        
        // Check if manhunt mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        if (manhuntEnabled != null && manhuntEnabled) {
            // Runners win!
            announceWinner("RUNNER");
            return;
        }
        
        // Check if connunity hunt mode is enabled
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled != null && connunityHuntEnabled) {
            // Streamers win!
            announceWinner("STREAMER");
            return;
        }
        
        // No specific mode enabled - everyone wins
        announceEveryoneWins();
    }
    
    /**
     * Handle the custom end fight when dragon is killed
     */
    private void handleCustomEndFight(EntityDeathEvent event) {
        Location dragonLocation = event.getEntity().getLocation();
        
        // Delay activation by 3 seconds to let the egg spawn first, then remove portals
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getCustomEndFightManager().activate();
                placeBedRockPlatform(dragonLocation.getWorld());
            }
        }.runTaskLater(plugin, 60L); // 3 seconds delay
        
        // Show "Chase the Egg" title to all players
        Component title = lang.getComponent("endfight.chase-egg-title");
        Component subtitle = lang.getComponent("endfight.chase-egg-subtitle");
        
        Title chaseTitle = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),  // Fade in
                Duration.ofSeconds(4),   // Stay
                Duration.ofSeconds(1)    // Fade out
            )
        );
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(chaseTitle);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        }
        
        // Add purple glow to dragon egg
        addDragonEggGlow(dragonLocation);
    }
    
    /**
     * Place a bedrock platform under the dragon egg spawn location to prevent it from falling
     */
    private void placeBedRockPlatform(World world) {
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        // The egg spawns at (0, 64, 0) on top of the exit portal
        // Place a 5x5 bedrock platform at y=61 to catch the egg if portal blocks are removed
        Location center = new Location(world, 0, 61, 0);
        
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                Block block = world.getBlockAt(center.getBlockX() + x, 61, center.getBlockZ() + z);
                if (block.getType() == Material.AIR || block.getType() == Material.END_PORTAL) {
                    block.setType(Material.AIR);
                }
            }
        }
    }
    
    /**
     * Add a purple glow effect to the dragon egg
     */
    private void addDragonEggGlow(Location dragonLocation) {
        new BukkitRunnable() {
            int attempts = 0;
            final int maxAttempts = 200; // Try for 10 seconds
            
            @Override
            public void run() {
                attempts++;
                
                // Search for dragon egg in the area (check portal area specifically)
                Location eggLocation = findDragonEgg(dragonLocation);
                
                if (eggLocation != null) {
                    plugin.logDebug("Found dragon egg at: " + eggLocation);
                    // Start continuous glow effect
                    startEggGlowEffect(eggLocation);
                    this.cancel();
                } else if (attempts >= maxAttempts) {
                    // Give up after max attempts
                    plugin.logWarning("Could not find dragon egg to add glow effect after " + attempts + " attempts!");
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }
    
    /**
     * Find the dragon egg in the area
     */
    private Location findDragonEgg(Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        
        // First check the exit portal area (0, 64, 0) where the egg always spawns
        Location portalCenter = new Location(world, 0, 64, 0);
        int portalRadius = 10;
        
        for (int x = -portalRadius; x <= portalRadius; x++) {
            for (int y = -5; y <= 10; y++) {
                for (int z = -portalRadius; z <= portalRadius; z++) {
                    Block block = world.getBlockAt(
                        portalCenter.getBlockX() + x,
                        portalCenter.getBlockY() + y,
                        portalCenter.getBlockZ() + z
                    );
                    
                    if (block.getType() == Material.DRAGON_EGG) {
                        return block.getLocation();
                    }
                }
            }
        }
        
        // Fallback: search around dragon death location
        int radius = 30;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(
                        center.getBlockX() + x,
                        center.getBlockY() + y,
                        center.getBlockZ() + z
                    );
                    
                    if (block.getType() == Material.DRAGON_EGG) {
                        return block.getLocation();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Start a continuous glow effect around the dragon egg
     */
    private void startEggGlowEffect(Location eggLocation) {
        // Particle effects removed due to compatibility issues
    }
    
    /**
     * Prevent portal from creating during custom end fight
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortalCreate(PortalCreateEvent event) {
        Boolean customEndFightEnabled = plugin.getDataManager().getSavedChallenge("custom_end_fight");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (customEndFightEnabled == null || !customEndFightEnabled || 
            teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }
        
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        boolean isTimerPaused = plugin.getTimerManager().isPaused();
        if (!isTimerRunning || isTimerPaused) {
            return;
        }
        
        // Check if this is in the End dimension
        if (event.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        // Cancel ALL portal creation in the End during custom end fight
        event.setCancelled(true);
        
        // Aggressively remove any portal blocks
        new BukkitRunnable() {
            @Override
            public void run() {
                for (org.bukkit.block.BlockState blockState : event.getBlocks()) {
                    Block block = blockState.getBlock();
                    if (block.getType() == Material.END_PORTAL || block.getType() == Material.END_GATEWAY) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }.runTaskLater(plugin, 1L);
    }
    
    /**
     * Prevent portal blocks from lighting during custom end fight
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPortalForm(BlockFromToEvent event) {
        Boolean customEndFightEnabled = plugin.getDataManager().getSavedChallenge("custom_end_fight");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (customEndFightEnabled == null || !customEndFightEnabled || 
            teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }
        
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        boolean isTimerPaused = plugin.getTimerManager().isPaused();
        if (!isTimerRunning || isTimerPaused) {
            return;
        }
        
        if (event.getBlock().getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }
        
        // Prevent portal blocks from forming
        Block block = event.getToBlock();
        if (block.getType() == Material.END_PORTAL || block.getType() == Material.END_GATEWAY) {
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
    }
    
    /**
     * Announce the winning team
     */
    private void announceWinner(String winningTeam) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop manhunt manager
        plugin.getManhuntManager().stop();
        
        // Stop connunity hunt manager
        plugin.getConnunityHuntManager().stop();
        
        Component subtitle;
        NamedTextColor winColor = NamedTextColor.GREEN;
        NamedTextColor loseColor = NamedTextColor.RED;
        
        if (winningTeam.equals("HUNTER")) {
            subtitle = lang.getComponent("death.hunters-eliminated-subtitle");
        } else if (winningTeam.equals("STREAMER")) {
            subtitle = lang.getComponent("death.dragon-slain-subtitle");
        } else {
            subtitle = lang.getComponent("death.dragon-slain-subtitle");
        }
        
        // Show personalized messages to each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            boolean isWinner = false;
            
            if (winningTeam.equals("HUNTER") && "Hunter".equals(playerTeam)) {
                isWinner = true;
            } else if (winningTeam.equals("RUNNER") && "Runner".equals(playerTeam)) {
                isWinner = true;
            } else if (winningTeam.equals("STREAMER") && "Streamer".equals(playerTeam)) {
                isWinner = true;
            }
            
            Component title = isWinner ? 
                lang.getComponent("death.you-win-title") : 
                lang.getComponent("death.you-lose-title");
            NamedTextColor color = isWinner ? winColor : loseColor;
            
            // Create title with timings
            Title gameTitle = Title.title(
                title,
                subtitle,
                Title.Times.times(
                    Duration.ofMillis(500),  // Fade in
                    Duration.ofSeconds(5),    // Stay
                    Duration.ofSeconds(2)     // Fade out
                )
            );
            
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            player.sendMessage(subtitle);
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    /**
     * Announce that everyone wins (no team mode enabled)
     */
    private void announceEveryoneWins() {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        Component title = lang.getComponent("dragon.everyone-wins-title");
        Component subtitle = lang.getComponent("dragon.everyone-wins-subtitle");
        
        // Create title with timings
        Title gameTitle = Title.title(
            title,
            subtitle,
            Title.Times.times(
                Duration.ofMillis(500),  // Fade in
                Duration.ofSeconds(5),    // Stay
                Duration.ofSeconds(2)     // Fade out
            )
        );
        
        // Show to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            player.sendMessage(lang.getComponent("dragon.dragon-defeated-message"));
            player.sendMessage(lang.getComponent("dragon.everyone-wins-message"));
            player.sendMessage(Component.text("═══════════════════════════════════", NamedTextColor.GOLD, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
}
