package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.time.Duration;

/**
 * Handles Ender Dragon death in Manhunt mode
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
        
        // Check if manhunt mode is enabled and timer is running
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        boolean isTimerRunning = plugin.getTimerManager().isRunning();
        
        if (manhuntEnabled == null || !manhuntEnabled || !isTimerRunning) {
            return;
        }
        
        // Runners win!
        announceWinner("RUNNER");
    }
    
    /**
     * Announce the winning team
     */
    private void announceWinner(String team) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop manhunt manager
        plugin.getManhuntManager().stop();
        
        Component title;
        Component subtitle;
        NamedTextColor color;
        
        if (team.equals("HUNTER")) {
            title = lang.getComponent("death.hunters-won-title");
            subtitle = lang.getComponent("death.hunters-won-subtitle");
            color = NamedTextColor.GOLD;
        } else {
            title = lang.getComponent("death.runners-won-title");
            subtitle = lang.getComponent("death.runners-won-subtitle");
            color = NamedTextColor.LIGHT_PURPLE;
        }
        
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
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(lang.getComponent("dragon.game-over"));
            player.sendMessage(Component.text(""));
            player.sendMessage(lang.getComponent("dragon.winner")
                    .append(Component.text("TEAM " + team, color, TextDecoration.BOLD)));
            player.sendMessage(Component.text("═══════════════════════════════════", color, TextDecoration.STRIKETHROUGH));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
}
