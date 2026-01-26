package de.connunity.util.challenge.lang;

import de.connunity.util.challenge.ChallengeUtil;
import de.connunity.util.challenge.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages multi-language support for the plugin
 */
public class LanguageManager {
    
    private final ChallengeUtil plugin;
    private final Map<String, YamlConfiguration> languages = new HashMap<>();
    private String currentLanguage;
    
    public LanguageManager(ChallengeUtil plugin) {
        this.plugin = plugin;
        this.currentLanguage = plugin.getConfig().getString("language", "en");
        loadLanguages();
    }
    
    /**
     * Load all available language files
     */
    private void loadLanguages() {
        // Load German
        loadLanguage("de");
        // Load English
        loadLanguage("en");
        
        plugin.getLogger().info("Loaded " + languages.size() + " language(s). Current: " + currentLanguage);
    }
    
    /**
     * Load a specific language file
     */
    private void loadLanguage(String lang) {
        File langFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml");
        
        // Create lang directory if it doesn't exist
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Check if file needs to be updated with new keys
        boolean needsUpdate = false;
        if (langFile.exists()) {
            needsUpdate = checkForNewKeys(langFile, lang);
        }
        
        // Save default file from resources if it doesn't exist or needs update
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
            plugin.getLogger().info("Created new language file: lang/" + lang + ".yml");
        } else if (needsUpdate) {
            // Backup old file
            File backupFile = new File(plugin.getDataFolder(), "lang/" + lang + ".yml.bak");
            try {
                Files.copy(langFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up old language file to: lang/" + lang + ".yml.bak");
            } catch (Exception e) {
                plugin.getLogger().warning("Could not backup language file: " + e.getMessage());
            }
            
            // Force update the file with new version
            plugin.saveResource("lang/" + lang + ".yml", true);
            plugin.getLogger().info("Updated language file with new keys: lang/" + lang + ".yml");
        }
        
        // Load the language file
        YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Load defaults from resources
        InputStream defConfigStream = plugin.getResource("lang/" + lang + ".yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defConfigStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defConfig);
        }
        
        languages.put(lang, langConfig);
    }
    
    /**
     * Check if the bundled language file has new keys compared to the existing file
     */
    private boolean checkForNewKeys(File existingFile, String lang) {
        try {
            // Load existing file
            YamlConfiguration existingConfig = YamlConfiguration.loadConfiguration(existingFile);
            
            // Load bundled file from resources
            InputStream bundledStream = plugin.getResource("lang/" + lang + ".yml");
            if (bundledStream == null) {
                return false;
            }
            
            YamlConfiguration bundledConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(bundledStream, StandardCharsets.UTF_8));
            
            // Get all keys from both configs
            Set<String> existingKeys = existingConfig.getKeys(true);
            Set<String> bundledKeys = bundledConfig.getKeys(true);
            
            // Check if bundled config has keys that existing config doesn't have
            for (String key : bundledKeys) {
                if (!existingKeys.contains(key)) {
                    plugin.getLogger().info("Found new language key in bundled file: " + key);
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking for new language keys: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get a translated message
     */
    public String getMessage(String key) {
        YamlConfiguration langConfig = languages.get(currentLanguage);
        if (langConfig == null) {
            langConfig = languages.get("en"); // Fallback to English
        }
        
        String message = langConfig.getString(key);
        if (message == null) {
            // Try fallback language
            YamlConfiguration fallback = languages.get("en");
            if (fallback != null) {
                message = fallback.getString(key);
            }
            if (message == null) {
                return "Missing translation: " + key;
            }
        }
        
        return message;
    }
    
    /**
     * Get a translated message with placeholders replaced
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        return message;
    }
    
    /**
     * Get a translated message as a Component
     */
    public Component getComponent(String key) {
        return ColorUtil.parse(getMessage(key));
    }
    
    /**
     * Get a translated message as a Component with placeholders
     */
    public Component getComponent(String key, Map<String, String> placeholders) {
        return ColorUtil.parse(getMessage(key, placeholders));
    }
    
    /**
     * Get current language code
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Set current language
     */
    public void setLanguage(String lang) {
        if (languages.containsKey(lang)) {
            this.currentLanguage = lang;
            plugin.getConfig().set("language", lang);
            plugin.saveConfig();
            plugin.getLogger().info("Language changed to: " + lang);
        } else {
            plugin.getLogger().warning("Language '" + lang + "' not found!");
        }
    }
    
    /**
     * Reload all language files
     */
    public void reload() {
        languages.clear();
        this.currentLanguage = plugin.getConfig().getString("language", "en");
        loadLanguages();
    }
}
