package de.connunity.util.challenge.gui;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Host Control GUI for quick access to host commands
 */
public class HostControlGUI {
    
    private final ChallengeUtil plugin;
    private final LanguageManager lang;
    private static final String TITLE = "Host Controls";
    
    public HostControlGUI(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }
    
    /**
     * Open the host control GUI for a player
     */
    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, Component.text(TITLE, NamedTextColor.GOLD, TextDecoration.BOLD));
        
        // Start button (slot 11)
        gui.setItem(11, createStartItem());
        
        // Settings button (slot 13)
        gui.setItem(13, createSettingsItem());
        
        // Full Reset button with WARNING (slot 15)
        gui.setItem(15, createFullResetItem());

        // Filler items
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Create the Start item
     */
    private ItemStack createStartItem() {
        ItemStack item = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.host.start.name").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.start.lore-click"));
        lore.add(lang.getComponent("gui.host.start.lore-begin"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.start.lore-command").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the Settings item
     */
    private ItemStack createSettingsItem() {
        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.host.settings.name").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.settings.lore-click"));
        lore.add(lang.getComponent("gui.host.settings.lore-menu"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.settings.lore-command").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Create the Full Reset item with WARNING
     */
    private ItemStack createFullResetItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.host.fullreset.name").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.fullreset.lore-warning").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        lore.add(lang.getComponent("gui.host.fullreset.lore-desc1"));
        lore.add(lang.getComponent("gui.host.fullreset.lore-desc2"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.fullreset.lore-bullet1"));
        lore.add(lang.getComponent("gui.host.fullreset.lore-bullet2"));
        lore.add(lang.getComponent("gui.host.fullreset.lore-bullet3"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.fullreset.lore-click"));
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.fullreset.lore-command").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
        
        meta.lore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    /**
     * Open confirmation GUI for full reset
     */
    public void openFullResetConfirmation(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, lang.getComponent("gui.host.confirm.title").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        
        // Confirm button (slot 11)
        ItemStack confirm = new ItemStack(Material.RED_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.displayName(lang.getComponent("gui.host.confirm.yes-name").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
        
        List<Component> confirmLore = new ArrayList<>();
        confirmLore.add(Component.text(""));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-desc"));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-bullet1"));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-bullet2"));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-bullet3"));
        confirmLore.add(Component.text(""));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-warning").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        confirmLore.add(Component.text(""));
        confirmLore.add(lang.getComponent("gui.host.confirm.yes-click"));
        
        confirmMeta.lore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        gui.setItem(11, confirm);
        
        // Cancel button (slot 15)
        ItemStack cancel = new ItemStack(Material.LIME_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(lang.getComponent("gui.host.confirm.no-name").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        
        List<Component> cancelLore = new ArrayList<>();
        cancelLore.add(Component.text(""));
        cancelLore.add(lang.getComponent("gui.host.confirm.no-desc1"));
        cancelLore.add(lang.getComponent("gui.host.confirm.no-desc2"));
        cancelLore.add(Component.text(""));
        
        cancelMeta.lore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        gui.setItem(15, cancel);
        
        // Warning signs as decoration
        ItemStack warning = new ItemStack(Material.BARRIER);
        ItemMeta warningMeta = warning.getItemMeta();
        warningMeta.displayName(lang.getComponent("gui.host.confirm.warning-label").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD));
        warning.setItemMeta(warningMeta);
        
        gui.setItem(0, warning);
        gui.setItem(8, warning);
        gui.setItem(18, warning);
        gui.setItem(26, warning);
        
        // Filler items
        ItemStack filler = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        
        for (int i = 0; i < 27; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
        
        player.openInventory(gui);
    }
    
    /**
     * Create the enchanted nether star item for host inventory
     */
    public static ItemStack createHostControlItem(ChallengeUtil plugin) {
        LanguageManager lang = plugin.getLanguageManager();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(lang.getComponent("gui.host.item.name").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(lang.getComponent("gui.host.item.lore-desc1"));
        lore.add(lang.getComponent("gui.host.item.lore-desc2"));
        lore.add(Component.text(""));
        
        meta.lore(lore);
        
        // Add enchantment glow
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        
        item.setItemMeta(meta);
        
        return item;
    }
}
