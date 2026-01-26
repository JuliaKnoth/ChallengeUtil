package de.connunity.util.challenge.commands;

import de.connunity.util.challenge.ChallengeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for players to join a team in Team Race mode
 */
public class RaceTeamCommand implements CommandExecutor, TabCompleter {
    
    private final ChallengeUtil plugin;
    
    public RaceTeamCommand(ChallengeUtil plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Nur Spieler können diesen Befehl verwenden!", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.sendMessage(Component.text("✗ A world reset is currently in progress!", NamedTextColor.RED));
            player.sendMessage(Component.text("  Please wait until the reset is complete.", NamedTextColor.GRAY));
            return true;
        }
        
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            player.sendMessage(Component.text("✗ Team Race Modus ist nicht aktiviert!", NamedTextColor.RED));
            player.sendMessage(Component.text("  Aktiviere ihn in /settings > Herausforderungen", NamedTextColor.GRAY));
            return true;
        }
        
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
            player.sendMessage(Component.text("✗ Ungültiges Team! Verfügbare Teams:", NamedTextColor.RED));
            showAvailableTeams(player);
            return true;
        }
        
        // Check if the game has started (timer is running)
        boolean timerRunning = plugin.getTimerManager().isRunning();
        if (timerRunning) {
            player.sendMessage(Component.text("✗ Du kannst die Teams nicht wechseln, sobald das Spiel gestartet wurde!", NamedTextColor.RED));
            player.sendMessage(Component.text("  Teams sind gesperrt, bis das Spiel zurückgesetzt wird.", NamedTextColor.GRAY));
            return true;
        }
        
        // Save team to data
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), teamName);
        
        net.kyori.adventure.text.format.TextColor teamColor = plugin.getTeamRaceManager().getTeamTextColor(teamName);
        
        player.sendMessage(Component.text("✓ Du bist ", NamedTextColor.GREEN)
                .append(Component.text("Team " + teamName, teamColor, TextDecoration.BOLD))
                .append(Component.text(" beigetreten!", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("  Dein Ziel: Besiege den Enderdrachen!", NamedTextColor.GRAY));
        
        if (!timerRunning) {
            player.sendMessage(Component.text("  Warte auf den Start der Challenge mit /start", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  Du erhältst einen Tracking-Kompass, wenn das Spiel startet", NamedTextColor.AQUA));
        } else {
            // Give compass to player if the game is already running
            plugin.getTeamRaceManager().giveCompassToPlayer(player, teamName);
            player.sendMessage(Component.text("  Du hast einen Tracking-Kompass erhalten!", NamedTextColor.AQUA));
        }
        
        return true;
    }
    
    /**
     * Show available teams to the player
     */
    private void showAvailableTeams(Player player) {
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("═══ Verfügbare Teams ═══", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(""));
        
        List<String> availableTeams = plugin.getTeamRaceManager().getAvailableTeamNames();
        
        for (String teamName : availableTeams) {
            net.kyori.adventure.text.format.TextColor teamColor = plugin.getTeamRaceManager().getTeamTextColor(teamName);
            int memberCount = plugin.getDataManager().getPlayersInTeam(teamName).size();
            
            Component teamLine = Component.text("  • ", NamedTextColor.GRAY)
                    .append(Component.text("Team " + teamName, teamColor, TextDecoration.BOLD));
            
            if (memberCount > 0) {
                teamLine = teamLine.append(Component.text(" (" + memberCount + " Spieler)", NamedTextColor.GRAY));
            }
            
            player.sendMessage(teamLine);
        }
        
        player.sendMessage(Component.text(""));
        player.sendMessage(Component.text("Nutze ", NamedTextColor.YELLOW)
                .append(Component.text("/raceteam <Teamname>", NamedTextColor.GOLD))
                .append(Component.text(" zum Beitreten", NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Beispiel: ", NamedTextColor.GRAY)
                .append(Component.text("/raceteam Rot", NamedTextColor.WHITE)));
        player.sendMessage(Component.text(""));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
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
        return new ArrayList<>();
    }
}
