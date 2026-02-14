package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import de.connunity.util.challenge.timer.TimerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class PauseCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final TimerManager timerManager;
    private final LanguageManager lang;
    
    public PauseCommand(ChallengeUtil plugin, TimerManager timerManager) {
        this.plugin = plugin;
        this.timerManager = timerManager;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("challenge.pause") && !sender.hasPermission("challenge.host")) {
            sender.sendMessage(lang.getComponent("commands.no-permission"));
            return true;
        }
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            sender.sendMessage(lang.getComponent("commands.reset-in-progress"));
            sender.sendMessage(lang.getComponent("commands.reset-in-progress-wait"));
            return true;
        }
        
        if (!timerManager.isRunning()) {
            sender.sendMessage(lang.getComponent("pause.not-running"));
            return true;
        }
        
        if (timerManager.isPaused()) {
            sender.sendMessage(lang.getComponent("pause.timer-already-paused"));
            return true;
        }
        
        timerManager.pause();
        
        // Pause timed item challenge as well
        plugin.getTimedRandomItemListener().pause();
        
        sender.sendMessage(lang.getComponent("pause.timer-paused"));
        
        return true;
    }
}
