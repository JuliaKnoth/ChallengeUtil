package de.connunity.util.challenge.teamrace;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.endfight.CustomEndFightManager;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Manages team race mode mechanics: multiple teams racing to kill the Ender Dragon
 * Each team has a compass tracking the nearest player from the closest enemy team
 */
public class TeamRaceManager {

    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private BukkitRunnable compassUpdateTask;
    private static final long COMPASS_UPDATE_INTERVAL = 20L; // 1 second
    
    // Track which team each player is currently tracking (defaults to nearest team)
    private final Map<UUID, String> playerTrackedTeam = new HashMap<>();
    
    // Track end portal location once someone enters the End
    private Location endPortalLocation = null;

    // German color names mapped to Minecraft colors
    private static final Map<String, TeamColor> TEAM_COLORS = new LinkedHashMap<>();
    
    static {
        TEAM_COLORS.put("Rot", new TeamColor(NamedTextColor.RED, "<red>"));
        TEAM_COLORS.put("Blau", new TeamColor(NamedTextColor.BLUE, "<blue>"));
        TEAM_COLORS.put("Grün", new TeamColor(NamedTextColor.GREEN, "<green>"));
        TEAM_COLORS.put("Gelb", new TeamColor(NamedTextColor.YELLOW, "<yellow>"));
        TEAM_COLORS.put("Lila", new TeamColor(NamedTextColor.LIGHT_PURPLE, "<light_purple>"));
        TEAM_COLORS.put("Aqua", new TeamColor(NamedTextColor.AQUA, "<aqua>"));
        TEAM_COLORS.put("Weiß", new TeamColor(NamedTextColor.WHITE, "<white>"));
        TEAM_COLORS.put("Orange", new TeamColor(NamedTextColor.GOLD, "<gold>"));
        TEAM_COLORS.put("Pink", new TeamColor(TextColor.color(255, 105, 180), "<light_purple>"));
        TEAM_COLORS.put("Grau", new TeamColor(NamedTextColor.GRAY, "<gray>"));
    }

    private static class TeamColor {
        final TextColor textColor;
        final String colorCode;
        
        TeamColor(TextColor textColor, String colorCode) {
            this.textColor = textColor;
            this.colorCode = colorCode;
        }
    }

    public TeamRaceManager(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Start team race mechanics when timer starts
     */
    public void start() {
        // Check if team race mode is enabled
        Boolean teamRaceEnabled = plugin.getDataManager().getSavedChallenge("team_race_mode");
        if (teamRaceEnabled == null || !teamRaceEnabled) {
            return;
        }

        // Remove team selection menu items from all players (teams are now locked)
        removeTeamMenuItems();
        
        // Give all players compasses
        giveTeamCompasses();
        
        // Update all player suffixes with kill counts
        updateAllPlayerSuffixes();
        
        // Start compass tracking
        startCompassUpdateTask();
    }

    /**
     * Stop all team race tasks
     */
    public void stop() {
        if (compassUpdateTask != null) {
            compassUpdateTask.cancel();
            compassUpdateTask = null;
        }
        playerTrackedTeam.clear();
        endPortalLocation = null;
        clearAllPlayerSuffixes();
    }

    /**
     * Give all team players tracking compasses
     */
    private void giveTeamCompasses() {
        List<String> teamNames = getActiveTeamNames();
        
        for (String teamName : teamNames) {
            Set<UUID> teamMembers = plugin.getDataManager().getPlayersInTeam(teamName);
            for (UUID memberId : teamMembers) {
                Player player = Bukkit.getPlayer(memberId);
                if (player != null && player.isOnline()) {
                    giveCompassToPlayer(player, teamName);
                }
            }
        }
    }

    /**
     * Give a compass to a specific player
     */
    public void giveCompassToPlayer(Player player, String teamName) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        if (meta != null) {
            TeamColor teamColor = getTeamColor(teamName);
            
            meta.displayName(lang.getComponent("teamrace.compass-name"));

            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("teamrace.compass-points-to"));
            lore.add(Component.text(""));
            lore.add(lang.getComponent("teamrace.compass-your-team")
                    .append(Component.text("Team " + teamName, teamColor.textColor, TextDecoration.BOLD)));

            meta.lore(lore);
            compass.setItemMeta(meta);
        }

        // Check if player already has a compass
        boolean hasCompass = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.COMPASS) {
                hasCompass = true;
                break;
            }
        }

        if (!hasCompass) {
            player.getInventory().addItem(compass);
        }
    }

    /**
     * Start task that updates compass direction to point to nearest enemy team member
     * or to the end portal once someone has entered the End
     */
    private void startCompassUpdateTask() {
        compassUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<String> teamNames = getActiveTeamNames();

                for (String teamName : teamNames) {
                    Set<UUID> teamMembers = plugin.getDataManager().getPlayersInTeam(teamName);
                    
                    for (UUID memberId : teamMembers) {
                        Player player = Bukkit.getPlayer(memberId);
                        if (player == null || !player.isOnline()) {
                            continue;
                        }

                        // --- Custom End Fight: egg holder tracking in The End ---
                        CustomEndFightManager endFight = plugin.getCustomEndFightManager();
                        boolean endFightActive = endFight != null && endFight.isActive() && endFight.isEggCollected();
                        Player currentEggHolder = endFightActive ? endFight.getEggHolder() : null;

                        // Skip updating compass for the egg holder (they hold the egg, not a compass)
                        if (currentEggHolder != null && memberId.equals(currentEggHolder.getUniqueId())) {
                            continue;
                        }

                        // Players in The End should track the egg holder
                        if (endFightActive && currentEggHolder != null && currentEggHolder.isOnline()
                                && player.getWorld().getEnvironment() == World.Environment.THE_END) {
                            player.setCompassTarget(currentEggHolder.getLocation());
                            updateCompassDisplayForEggHolder(player, teamName, currentEggHolder);
                            continue;
                        }

                        // If someone has entered the End, point compass to end portal (Overworld behaviour)
                        if (endPortalLocation != null) {
                            player.setCompassTarget(endPortalLocation);
                            updateCompassDisplayForEndPortal(player, teamName);
                            continue;
                        }

                        // Get the team this player is tracking (or find nearest)
                        String trackedTeam = playerTrackedTeam.get(memberId);
                        String nearestTeam = findNearestEnemyTeam(player, teamName, teamNames);
                        
                        // If no tracked team set, or tracked team has no players, use nearest
                        if (trackedTeam == null || trackedTeam.equals(teamName) || 
                            plugin.getDataManager().getPlayersInTeam(trackedTeam).isEmpty()) {
                            trackedTeam = nearestTeam;
                            playerTrackedTeam.put(memberId, trackedTeam);
                        }
                        
                        // Find nearest member of the tracked team
                        Player nearestEnemy = findNearestMemberOfTeam(player, trackedTeam);
                        
                        if (nearestEnemy != null) {
                            // Update compass to point to tracked enemy
                            Location targetLoc = nearestEnemy.getLocation();
                            player.setCompassTarget(targetLoc);
                            
                            // Update compass display
                            boolean isClosest = trackedTeam != null && trackedTeam.equals(nearestTeam);
                            updateCompassDisplay(player, teamName, trackedTeam, isClosest);
                        }
                    }
                }
            }
        };

        // Run every second
        compassUpdateTask.runTaskTimer(plugin, 0L, COMPASS_UPDATE_INTERVAL);
    }

    /**
     * Find the nearest enemy team (not the player's own team)
     * Returns the team name of the nearest enemy team
     */
    private String findNearestEnemyTeam(Player player, String playerTeam, List<String> allTeams) {
        String nearestTeam = null;
        double nearestDistance = Double.MAX_VALUE;

        for (String enemyTeam : allTeams) {
            if (enemyTeam.equals(playerTeam)) {
                continue; // Skip own team
            }

            Set<UUID> enemyMembers = plugin.getDataManager().getPlayersInTeam(enemyTeam);
            
            // Find the closest member of this team
            for (UUID enemyId : enemyMembers) {
                Player enemy = Bukkit.getPlayer(enemyId);
                if (enemy == null || !enemy.isOnline()) {
                    continue;
                }

                // Only track enemies in same world
                if (player.getWorld().equals(enemy.getWorld())) {
                    double distance = player.getLocation().distanceSquared(enemy.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestTeam = enemyTeam;
                    }
                }
            }
        }

        return nearestTeam;
    }

    /**
     * Find the nearest member of a specific team
     */
    private Player findNearestMemberOfTeam(Player player, String targetTeam) {
        if (targetTeam == null) {
            return null;
        }
        
        Player nearestEnemy = null;
        double nearestDistance = Double.MAX_VALUE;
        
        Set<UUID> enemyMembers = plugin.getDataManager().getPlayersInTeam(targetTeam);
        
        for (UUID enemyId : enemyMembers) {
            Player enemy = Bukkit.getPlayer(enemyId);
            if (enemy == null || !enemy.isOnline()) {
                continue;
            }

            // Only track enemies in same world
            if (player.getWorld().equals(enemy.getWorld())) {
                double distance = player.getLocation().distanceSquared(enemy.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEnemy = enemy;
                }
            }
        }

        return nearestEnemy;
    }

    /**
     * Update compass display with target team color and closest indicator
     */
    private void updateCompassDisplay(Player player, String playerTeam, String targetTeam, boolean isClosest) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    TeamColor targetColor = getTeamColor(targetTeam);
                    TeamColor playerColor = getTeamColor(playerTeam);
                    
                    // Set compass name to team name in team color (without "tracking" text)
                    Component compassName = Component.text("Team " + targetTeam, targetColor.textColor, TextDecoration.BOLD);
                    if (isClosest) {
                        // Add danger symbol if this is the closest team
                        compassName = Component.text("⚠ ", NamedTextColor.RED, TextDecoration.BOLD)
                                .append(compassName);
                    }
                    meta.displayName(compassName);

                    List<Component> lore = new ArrayList<>();
                    lore.add(lang.getComponent("teamrace.compass-points-label")
                            .append(Component.text("Team " + targetTeam, targetColor.textColor)));
                    if (isClosest) {
                        lore.add(Component.text("⚠ Closest Team", NamedTextColor.RED, TextDecoration.BOLD));
                    }
                    lore.add(Component.text(""));
                    lore.add(lang.getComponent("teamrace.compass-your-team")
                            .append(Component.text("Team " + playerTeam, playerColor.textColor, TextDecoration.BOLD)));
                    lore.add(Component.text(""));
                    lore.add(lang.getComponent("teamrace.compass-switch-hint"));

                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Get team color for a team name
     */
    public TeamColor getTeamColor(String teamName) {
        return TEAM_COLORS.getOrDefault(teamName, TEAM_COLORS.get("Weiß"));
    }

    /**
     * Get text color for a team name
     */
    public TextColor getTeamTextColor(String teamName) {
        return getTeamColor(teamName).textColor;
    }

    /**
     * Get color code for a team name
     */
    public String getTeamColorCode(String teamName) {
        return getTeamColor(teamName).colorCode;
    }

    /**
     * Get list of available team names (up to 10)
     */
    public List<String> getAvailableTeamNames() {
        return new ArrayList<>(TEAM_COLORS.keySet());
    }

    /**
     * Get list of currently active teams (teams with at least one player)
     */
    public List<String> getActiveTeamNames() {
        List<String> activeTeams = new ArrayList<>();
        
        for (String teamName : TEAM_COLORS.keySet()) {
            Set<UUID> members = plugin.getDataManager().getPlayersInTeam(teamName);
            if (!members.isEmpty()) {
                activeTeams.add(teamName);
            }
        }
        
        return activeTeams;
    }

    /**
     * Get a player's team name
     */
    private String getPlayerTeam(Player player) {
        return plugin.getDataManager().getPlayerTeam(player.getUniqueId());
    }

    /**
     * Check if a team has won (killed the Ender Dragon)
     */
    public boolean checkTeamWin(String winningTeam) {
        // Stop the timer
        plugin.getTimerManager().stop();
        
        // Stop team race manager
        stop();
        
        // Announce winner
        announceWinner(winningTeam);
        
        return true;
    }

    /**
     * Announce the winning team
     */
    private void announceWinner(String winningTeam) {
        Component subtitle = lang.getComponent("teamrace.dragon-defeated-subtitle");
        NamedTextColor winColor = NamedTextColor.GREEN;
        NamedTextColor loseColor = NamedTextColor.RED;
        
        // Show personalized messages to each player
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            boolean isWinner = winningTeam.equalsIgnoreCase(playerTeam);
            
            Component title = isWinner ? 
                lang.getComponent("teamrace.you-win-title") : 
                lang.getComponent("teamrace.you-lose-title");
            NamedTextColor color = isWinner ? winColor : loseColor;
            
            Component message;
            if (isWinner) {
                message = lang.getComponent("teamrace.your-team-won");
            } else {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("team", winningTeam.toUpperCase());
                message = lang.getComponent("teamrace.enemy-team-won", placeholders);
            }
            
            // Create title with timings
            net.kyori.adventure.title.Title gameTitle = net.kyori.adventure.title.Title.title(
                title,
                subtitle,
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(500),  // Fade in
                    java.time.Duration.ofSeconds(5),    // Stay
                    java.time.Duration.ofSeconds(2)     // Fade out
                )
            );
            
            player.showTitle(gameTitle);
            player.sendMessage(Component.text(""));
            player.sendMessage(lang.getComponent("teamrace.game-over-divider"));
            player.sendMessage(lang.getComponent("teamrace.game-over-title"));
            player.sendMessage(Component.text(""));
            player.sendMessage(message);
            player.sendMessage(lang.getComponent("teamrace.game-over-divider"));
            player.sendMessage(Component.text(""));
            
            // Play victory sound
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    /**
     * Get the number of active teams
     */
    public int getActiveTeamCount() {
        return getActiveTeamNames().size();
    }

    /**
     * Validate that team race mode has valid team setup (2-10 teams)
     */
    public boolean validateTeamSetup() {
        int teamCount = getActiveTeamCount();
        return teamCount >= 2 && teamCount <= 10;
    }
    
    /**
     * Switch a player's tracked team to the next available enemy team
     */
    public void switchTrackedTeam(Player player) {
        String playerTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (playerTeam == null) {
            return;
        }
        
        List<String> enemyTeams = getEnemyTeams(playerTeam);
        if (enemyTeams.isEmpty()) {
            return;
        }
        
        // Get current tracked team
        String currentTracked = playerTrackedTeam.get(player.getUniqueId());
        
        // Find next team in the list
        int currentIndex = enemyTeams.indexOf(currentTracked);
        int nextIndex = (currentIndex + 1) % enemyTeams.size();
        String nextTeam = enemyTeams.get(nextIndex);
        
        // Update tracked team
        playerTrackedTeam.put(player.getUniqueId(), nextTeam);
    }
    
    /**
     * Get list of enemy teams (excluding player's team)
     */
    private List<String> getEnemyTeams(String playerTeam) {
        List<String> enemyTeams = new ArrayList<>();
        List<String> allTeams = getActiveTeamNames();
        
        for (String team : allTeams) {
            if (!team.equals(playerTeam)) {
                enemyTeams.add(team);
            }
        }
        
        return enemyTeams;
    }
    
    /**
     * Update player's suffix with kill count
     */
    public void updatePlayerSuffix(Player player) {
        int kills = plugin.getDataManager().getTeamRaceKills(player.getUniqueId());
        
        if (kills > 0) {
            // Set suffix to show kill count
            Component suffix = Component.text(" " + kills, NamedTextColor.GOLD);
            player.playerListName(player.displayName().append(suffix));
        } else {
            // Reset to default (no suffix)
            player.playerListName(player.displayName());
        }
    }
    
    /**
     * Update all players' suffixes with their kill counts
     */
    public void updateAllPlayerSuffixes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String team = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
            if (team != null && !team.isEmpty()) {
                updatePlayerSuffix(player);
            }
        }
    }
    
    /**
     * Clear all player suffixes
     */
    public void clearAllPlayerSuffixes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playerListName(player.displayName());
        }
    }
    
    /**
     * Set the end portal location when a player enters the End
     * This makes all compasses point to this location
     */
    public void setEndPortalLocation(Location location) {
        if (endPortalLocation == null) {
            endPortalLocation = location;
            
            // Notify all team race players
            List<String> teamNames = getActiveTeamNames();
            for (String teamName : teamNames) {
                Set<UUID> teamMembers = plugin.getDataManager().getPlayersInTeam(teamName);
                for (UUID memberId : teamMembers) {
                    Player player = Bukkit.getPlayer(memberId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(lang.getComponent("teamrace.compass-now-points-to-portal"));
                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 1.0f);
                    }
                }
            }
        }
    }
    
    /**
     * Update compass display to show it's pointing to the egg holder in The End
     */
    private void updateCompassDisplayForEggHolder(Player player, String playerTeam, Player eggHolder) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    TeamColor playerColor = getTeamColor(playerTeam);
                    String holderTeamName = plugin.getDataManager().getPlayerTeam(eggHolder.getUniqueId());
                    TeamColor holderColor = holderTeamName != null ? getTeamColor(holderTeamName) : getTeamColor("Weiß");

                    Component compassName = Component.text("☽ ", NamedTextColor.WHITE, TextDecoration.BOLD)
                            .append(Component.text(eggHolder.getName(), holderColor.textColor, TextDecoration.BOLD))
                            .append(Component.text(" (Egg)", NamedTextColor.YELLOW, TextDecoration.BOLD));
                    meta.displayName(compassName);

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Points to: ", NamedTextColor.GRAY)
                            .append(Component.text(eggHolder.getName(), NamedTextColor.GOLD))
                            .append(Component.text(" — Egg Holder", NamedTextColor.DARK_PURPLE)));
                    lore.add(Component.text(""));
                    lore.add(lang.getComponent("teamrace.compass-your-team")
                            .append(Component.text("Team " + playerTeam, playerColor.textColor, TextDecoration.BOLD)));

                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Update compass display to show it's pointing to the end portal
     */
    private void updateCompassDisplayForEndPortal(Player player, String playerTeam) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.COMPASS) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    TeamColor playerColor = getTeamColor(playerTeam);
                    
                    // Set compass name to show end portal
                    Component compassName = Component.text("◆ ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("End Portal", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                            .append(Component.text(" ◆", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                    meta.displayName(compassName);

                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Points to: ", NamedTextColor.GRAY)
                            .append(Component.text("Activated End Portal", NamedTextColor.LIGHT_PURPLE)));
                    lore.add(Component.text(""));
                    lore.add(lang.getComponent("teamrace.compass-your-team")
                            .append(Component.text("Team " + playerTeam, playerColor.textColor, TextDecoration.BOLD)));

                    meta.lore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
    }
    
    /**
     * Give team selection menu items to all online players
     */
    public void giveTeamMenuItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getTeamSelectionItemListener().giveTeamMenuItem(player);
        }
    }
    
    /**
     * Remove team selection menu items from all online players
     */
    public void removeTeamMenuItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getTeamSelectionItemListener().removeTeamMenuItem(player);
        }
    }
}
