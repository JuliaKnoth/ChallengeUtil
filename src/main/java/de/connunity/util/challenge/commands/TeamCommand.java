package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Unified command for players to join teams in both Manhunt and Team Race modes
 */
public class TeamCommand implements CommandExecutor, TabCompleter {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public TeamCommand(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.getComponent("commands.only-players"));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.sendMessage(lang.getComponent("commands.reset-in-progress"));
            player.sendMessage(lang.getComponent("commands.reset-in-progress-wait"));
            return true;
        }
        
        // Check which mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        Boolean connunityHuntEnabled = plugin.getDataManager().getSavedChallenge("connunity_hunt_mode");
        
        // If Connunity Hunt is enabled, teams are auto-assigned
        if (connunityHuntEnabled != null && connunityHuntEnabled) {
            player.sendMessage(Component.text("ⓘ Connunity Hunt: Teams are automatically assigned!", net.kyori.adventure.text.format.NamedTextColor.AQUA));
            player.sendMessage(Component.text("  Streamer team: vup.creator or challenge.creator permission", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            player.sendMessage(Component.text("  Viewer team: everyone else", net.kyori.adventure.text.format.NamedTextColor.BLUE));
            
            // Show player their current team
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            if (team != null) {
                net.kyori.adventure.text.format.TextColor teamColor = plugin.getConnunityHuntManager().getTeamColor(team);
                player.sendMessage(Component.text(""));
                player.sendMessage(Component.text("Your team: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                    .append(Component.text(team, teamColor, TextDecoration.BOLD)));
            }
            
            return true;
        }
        
        if ((manhuntEnabled == null || !manhuntEnabled) && (teamRaceEnabled == null || !teamRaceEnabled)) {
            player.sendMessage(lang.getComponent("team.no-mode-active"));
            player.sendMessage(lang.getComponent("team.no-mode-active-hint"));
            return true;
        }
        
        // If Team Race is enabled, use Team Race logic
        if (teamRaceEnabled != null && teamRaceEnabled) {
            return handleTeamRaceMode(player, args);
        }
        
        // Otherwise use Manhunt logic
        return handleManhuntMode(player, args);
    }
    
    /**
     * Handle team selection for Manhunt mode
     */
    private boolean handleManhuntMode(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(lang.getComponent("team.manhunt-usage"));
            return true;
        }
        
        String teamName = args[0].toLowerCase();
        
        if (!teamName.equals("runner") && !teamName.equals("hunter") && !teamName.equals("spectator")) {
            player.sendMessage(lang.getComponent("team.manhunt-invalid"));
            return true;
        }
        
        // Check if the game has started (timer is running)
        boolean timerRunning = plugin.getTimerManager().isRunning();
        if (timerRunning) {
            player.sendMessage(lang.getComponent("team.manhunt-locked"));
            player.sendMessage(lang.getComponent("team.manhunt-locked-hint"));
            return true;
        }
        
        // Save team to data
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), teamName);
        
        if (teamName.equals("runner")) {
            player.sendMessage(lang.getComponent("team.manhunt-joined-runner"));
            player.sendMessage(lang.getComponent("team.manhunt-joined-runner-goal"));
            
            if (!timerRunning) {
                player.sendMessage(lang.getComponent("team.manhunt-joined-runner-wait"));
            }
        } else if (teamName.equals("hunter")) {
            player.sendMessage(lang.getComponent("team.manhunt-joined-hunter"));
            player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-goal"));
            
            if (!timerRunning) {
                player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-wait"));
                player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-compass"));
            } else {
                // Give compass to hunter if the game is already running
                plugin.getManhuntManager().giveCompassToHunter(player);
                player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-blind"));
                player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-compass-received"));
            }
        } else if (teamName.equals("spectator")) {
            player.sendMessage(lang.getComponent("team.manhunt-joined-spectator"));
            player.sendMessage(lang.getComponent("team.manhunt-joined-spectator-info"));
            
            // Set player to spectator mode
            player.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
        
        return true;
    }
    
    /**
     * Handle team selection for Team Race mode
     */
    private boolean handleTeamRaceMode(Player player, String[] args) {
        if (args.length == 0) {
            // Show available teams
            showAvailableTeams(player);
            return true;
        }
        
        String teamName = args[0];
        
        // Normalize team name (capitalize first letter)
        teamName = teamName.substring(0, 1).toUpperCase() + teamName.substring(1).toLowerCase();
        
        // Check if team name is valid
        List<String> availableTeams = plugin.getTeamRaceManager().getAvailableTeamNames();
        if (!availableTeams.contains(teamName)) {
            player.sendMessage(lang.getComponent("team.teamrace-invalid"));
            showAvailableTeams(player);
            return true;
        }
        
        // Check if the game has started (timer is running)
        boolean timerRunning = plugin.getTimerManager().isRunning();
        if (timerRunning) {
            player.sendMessage(lang.getComponent("team.teamrace-locked"));
            player.sendMessage(lang.getComponent("team.teamrace-locked-hint"));
            return true;
        }
        
        // Save team to data
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), teamName);
        
        net.kyori.adventure.text.format.TextColor teamColor = plugin.getTeamRaceManager().getTeamTextColor(teamName);
        
        Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("team", teamName);
        player.sendMessage(lang.getComponent("team.teamrace-joined", placeholders));
        player.sendMessage(lang.getComponent("team.teamrace-joined-goal"));
        
        if (!timerRunning) {
            player.sendMessage(lang.getComponent("team.manhunt-joined-runner-wait"));
            player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-compass"));
        } else {
            // Give compass to player if the game is already running
            plugin.getTeamRaceManager().giveCompassToPlayer(player, teamName);
            player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-compass-received"));
        }
        
        return true;
    }
    
    /**
     * Show available teams to the player
     */
    private void showAvailableTeams(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(lang.getComponent("team.teamrace-available-teams-header"));
        player.sendMessage(Component.text(""));
        
        List<String> availableTeams = plugin.getTeamRaceManager().getAvailableTeamNames();
        
        for (String teamName : availableTeams) {
            net.kyori.adventure.text.format.TextColor teamColor = plugin.getTeamRaceManager().getTeamTextColor(teamName);
            int memberCount = plugin.getDataManager().getPlayersInTeam(teamName).size();
            
            Map<String, String> placeholders = new java.util.HashMap<>();
            placeholders.put("team", teamName);
            placeholders.put("count", String.valueOf(memberCount));
            
            Component teamLine = Component.text("  • ").color(teamColor)
                    .append(Component.text("Team " + teamName).color(teamColor).decorate(TextDecoration.BOLD));
            
            if (memberCount > 0) {
                teamLine = teamLine.append(lang.getComponent("team.teamrace-team-members", placeholders));
            }
            
            player.sendMessage(teamLine);
        }
        
        player.sendMessage(Component.text(""));
        player.sendMessage(lang.getComponent("team.teamrace-join-hint"));
        player.sendMessage(lang.getComponent("team.teamrace-join-example"));
        player.sendMessage(Component.text(""));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return new ArrayList<>();
        }
        
        // Check which mode is enabled
        Boolean manhuntEnabled = plugin.getDataManager().getSavedChallenge("manhunt_mode");
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        
        // Team Race mode - show team names
        if (teamRaceEnabled != null && teamRaceEnabled) {
            List<String> teams = plugin.getTeamRaceManager().getAvailableTeamNames();
            List<String> completions = new ArrayList<>();
            
            String partial = args[0].toLowerCase();
            for (String team : teams) {
                if (team.toLowerCase().startsWith(partial)) {
                    completions.add(team);
                }
            }
            
            return completions;
        }
        
        // Manhunt mode - show runner/hunter/spectator
        if (manhuntEnabled != null && manhuntEnabled) {
            return Arrays.asList("runner", "hunter", "spectator");
        }
        
        return new ArrayList<>();
    }
}
