package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import de.connunity.util.challenge.timer.TimerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ResetCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final TimerManager timerManager;
    private final LanguageManager lang;
    
    public ResetCommand(ChallengeUtil plugin, TimerManager timerManager) {
        this.plugin = plugin;
        this.timerManager = timerManager;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("challenge.reset") && !sender.hasPermission("challenge.host")) {
            sender.sendMessage(lang.getComponent("commands.no-permission"));
            return true;
        }
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            sender.sendMessage(lang.getComponent("commands.reset-in-progress"));
            sender.sendMessage(lang.getComponent("commands.reset-in-progress-wait"));
            return true;
        }
        
        timerManager.reset();
        plugin.getManhuntManager().stop();
        plugin.getTeamRaceManager().stop();
        plugin.getChunkItemChallengeListener().stop();
        
        // Clear team data to allow players to rejoin teams
        plugin.getDataManager().clearTeams();
        
        sender.sendMessage(lang.getComponent("reset.timer-reset"));
        sender.sendMessage(lang.getComponent("reset.teams-cleared"));
        
        return true;
    }
}
