package de.connunity.util.challenge.listeners;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.gui.HostControlGUI;
import de.connunity.util.challenge.gui.SettingsGUI;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

/**
 * Handles clicks in the Host Control GUI
 */
public class HostControlGUIListener implements Listener {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    
    public HostControlGUIListener(ChallengeUtil plugin) {
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
        
        // Check if it's the host control GUI or confirmation GUI
        String title = PlainTextComponentSerializer.plainText().serialize(view.title());
        
        // Get translated titles to compare
        String hostControlTitle = "Host Controls"; // Static title from HostControlGUI
        String confirmTitle = PlainTextComponentSerializer.plainText().serialize(
            lang.getComponent("gui.host.confirm.title"));
        
        if (!title.equals(hostControlTitle) && !title.equals(confirmTitle)) {
            return;
        }
        
        // Cancel all clicks in the GUI
        event.setCancelled(true);
        
        // Check if a full reset is in progress
        if (plugin.isResetInProgress()) {
            player.closeInventory();
            player.sendMessage(lang.getComponent("host.reset-in-progress"));
            player.sendMessage(lang.getComponent("host.reset-wait"));
            return;
        }
        
        // Get clicked slot
        int slot = event.getRawSlot();
        
        // Only process clicks in the GUI inventory (not player inventory)
        if (slot < 0 || slot >= view.getTopInventory().getSize()) {
            return;
        }
        
        // Handle confirmation GUI
        if (title.equals(confirmTitle)) {
            handleConfirmationClick(player, slot);
            return;
        }
        
        // Handle main host control GUI clicks
        switch (slot) {
            case 11: // Start button
                player.closeInventory();
                player.performCommand("start");
                break;
                
            case 13: // Settings button
                player.closeInventory();
                SettingsGUI settingsGUI = new SettingsGUI(plugin);
                settingsGUI.open(player);
                break;
                
            case 15: // Full Reset button - open confirmation
                HostControlGUI hostGUI = new HostControlGUI(plugin);
                hostGUI.openFullResetConfirmation(player);
                break;
        }
    }
    
    /**
     * Handle clicks in the full reset confirmation GUI
     */
    private void handleConfirmationClick(Player player, int slot) {
        if (slot == 11) {
            // Confirm - execute full reset
            player.closeInventory();
            player.sendMessage(lang.getComponent("host.executing-reset"));
            player.performCommand("fullreset");
            
        } else if (slot == 15) {
            // Cancel - go back to host controls
            player.sendMessage(lang.getComponent("host.reset-cancelled"));
            HostControlGUI hostGUI = new HostControlGUI(plugin);
            hostGUI.open(player);
        }
    }
}
