package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.TeamSelectionGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles clicks in the Team Selection GUI
 */
public class TeamSelectionGUIListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public TeamSelectionGUIListener(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        InventoryView view = event.getView();
        
        // Check if it's the team selection GUI
        String title = PlainTextComponentSerializer.plainText().serialize(view.title());
        String guiTitle = PlainTextComponentSerializer.plainText().serialize(lang.getComponent("teamrace.gui.title"));
        
        if (!title.equals(guiTitle)) {
            return;
        }
        
        // Cancel all clicks in the GUI
        event.setCancelled(true);
        
        // Check if timer is running (teams locked)
        if (plugin.getTimerManager().isRunning()) {
            player.closeInventory();
            player.sendMessage(lang.getComponent("team.teamrace-locked"));
            player.sendMessage(lang.getComponent("team.teamrace-locked-hint"));
            return;
        }
        
        // Get clicked slot
        int slot = event.getRawSlot();
        
        // Only process clicks in the GUI inventory (not player inventory)
        if (slot < 0 || slot >= view.getTopInventory().getSize()) {
            return;
        }
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE) {
            return;
        }
        
        // Check if clicked item is a wool block (team item)
        if (!clickedItem.getType().name().endsWith("_WOOL")) {
            return;
        }
        
        // Get the team name from the clicked wool
        String teamName = getTeamNameFromWool(clickedItem.getType());
        if (teamName == null) {
            return;
        }
        
        // Check if team is valid
        List<String> availableTeams = plugin.getTeamRaceManager().getAvailableTeamNames();
        if (!availableTeams.contains(teamName)) {
            return;
        }
        
        // Check if player is already on this team
        String currentTeam = plugin.getDataManager().getPlayerTeam(player.getUniqueId());
        if (teamName.equals(currentTeam)) {
            player.sendMessage(lang.getComponent("teamrace.gui.already-in-team"));
            player.closeInventory();
            return;
        }
        
        // Join the team
        plugin.getDataManager().setPlayerTeam(player.getUniqueId(), teamName);
        
        // Send success message
        net.kyori.adventure.text.format.TextColor teamColor = plugin.getTeamRaceManager().getTeamTextColor(teamName);
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("team", teamName);
        
        player.sendMessage(Component.text(""));
        player.sendMessage(lang.getComponent("team.teamrace-joined", placeholders));
        player.sendMessage(lang.getComponent("team.teamrace-joined-goal"));
        player.sendMessage(lang.getComponent("team.manhunt-joined-runner-wait"));
        player.sendMessage(lang.getComponent("team.manhunt-joined-hunter-compass"));
        player.sendMessage(Component.text(""));
        
        // Close and reopen GUI to show updated selection
        player.closeInventory();
        
        // Reopen after a short delay to prevent issues
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Update the hotbar item to reflect the new team color
            plugin.getTeamSelectionItemListener().updateTeamMenuItem(player);
            TeamSelectionGUI gui = new TeamSelectionGUI(plugin);
            gui.open(player);
        }, 1L);
    }
    
    /**
     * Get team name from wool material
     */
    private String getTeamNameFromWool(Material woolMaterial) {
        switch (woolMaterial) {
            case RED_WOOL:
                return "Rot";
            case BLUE_WOOL:
                return "Blau";
            case GREEN_WOOL:
                return "Grün";
            case YELLOW_WOOL:
                return "Gelb";
            case PURPLE_WOOL:
                return "Lila";
            case CYAN_WOOL:
                return "Aqua";
            case WHITE_WOOL:
                return "Weiß";
            case ORANGE_WOOL:
                return "Orange";
            case PINK_WOOL:
                return "Pink";
            case GRAY_WOOL:
                return "Grau";
            default:
                return null;
        }
    }
}
