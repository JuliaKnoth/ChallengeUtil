package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command for hosts to become spectators in Connunity Hunt mode
 */
public class SpectatorCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public SpectatorCommand(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getComponent("commands.only-players"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if player has challenge.host permission
        if (!player.hasPermission("challenge.host")) {
            player.sendMessage(lang.getComponent("spectator.no-permission"));
            return true;
        }
        
        // Check if Connunity Hunt mode is enabled
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        if (connunityHuntEnabled == null || !connunityHuntEnabled) {
            player.sendMessage(lang.getComponent("spectator.not-connunity-hunt"));
            return true;
        }
        
        // Set player team to "spectator"
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), "spectator");
        
        // Set player to spectator gamemode
        player.setGameMode(GameMode.SPECTATOR);
        
        // Send confirmation message
        player.sendMessage(lang.getComponent("spectator.success"));
        player.sendMessage(lang.getComponent("spectator.info"));
        
        return true;
    }
}
