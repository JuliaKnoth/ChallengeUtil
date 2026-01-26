package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.SettingsGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Command to open the settings GUI (waiting room only)
 */
public class SettingsCommand implements CommandExecutor {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public SettingsCommand(ChallengeUtil plugin) {
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
        
        // Check permission
        if (!player.hasPermission("challenge.host")) {
            player.sendMessage(lang.getComponent("commands.no-permission"));
            return true;
        }
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.sendMessage(lang.getComponent("commands.reset-in-progress"));
            player.sendMessage(lang.getComponent("commands.reset-in-progress-wait"));
            return true;
        }
        
        // Check if player is in waiting room
        String waitingRoomName = plugin.getConfig().getString("world.waiting-room", "waiting_room");
        if (!player.getWorld().getName().equals(waitingRoomName)) {
            player.sendMessage(lang.getComponent("settings.only-in-waiting-room"));
            return true;
        }
        
        // Open the settings GUI
        SettingsGUI gui = new SettingsGUI(plugin);
        gui.open(player);
        
        return true;
    }
}
