package de.connunity.util.challenge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class VersionChecker {
    
    private final ChallengeUtil plugin;
    private final String currentVersion;
    private final String modrinthProjectId;
    private String latestVersion = null;
    private String downloadUrl = null;
    
    /**
     * Creates a new version checker
     * @param plugin The ChallengeUtil plugin instance
     * @param modrinthProjectId Your Modrinth project ID (slug or ID)
     */
    public VersionChecker(ChallengeUtil plugin, String modrinthProjectId) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
        this.modrinthProjectId = modrinthProjectId;
    }
    
    /**
     * Checks for a new version asynchronously
     * @return CompletableFuture that completes when the check is done
     */
    public CompletableFuture<Boolean> checkForUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + modrinthProjectId + "/version";
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "ChallengeUtil/" + currentVersion);
                connection.setConnectTimeout(10000);  // 10 seconds for 24/7 stability
                connection.setReadTimeout(10000);     // 10 seconds for 24/7 stability
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse JSON response with extra validation
                    String responseStr = response.toString();
                    if (responseStr.isEmpty()) {
                        plugin.getLogger().warning("Empty response from Modrinth API");
                        return false;
                    }
                    
                    JsonArray versions = JsonParser.parseString(responseStr).getAsJsonArray();
                    if (versions.size() > 0) {
                        // Determine if current version is a pre-release
                        boolean currentIsPreRelease = isPreRelease(currentVersion);
                        
                        // Find the appropriate latest version
                        JsonObject latestVersionObj = null;
                        
                        if (currentIsPreRelease) {
                            // For pre-release versions (Alpha, Beta, etc.), look for ANY newer version
                            // This includes both newer pre-releases and stable releases
                            for (int i = 0; i < versions.size(); i++) {
                                JsonObject versionObj = versions.get(i).getAsJsonObject();
                                if (!versionObj.has("version_number")) continue;
                                
                                String versionNumber = versionObj.get("version_number").getAsString();
                                
                                // Check if this version is newer than current version
                                if (isNewerVersion(versionNumber, currentVersion)) {
                                    latestVersionObj = versionObj;
                                    break;
                                }
                            }
                        } else {
                            // For stable versions, only look for stable releases
                            for (int i = 0; i < versions.size(); i++) {
                                JsonObject versionObj = versions.get(i).getAsJsonObject();
                                if (!versionObj.has("version_number")) continue;
                                
                                String versionNumber = versionObj.get("version_number").getAsString();
                                
                                // Only consider stable releases
                                if (!isPreRelease(versionNumber)) {
                                    latestVersionObj = versionObj;
                                    break;
                                }
                            }
                        }
                        
                        // Validate we found a version
                        if (latestVersionObj == null || !latestVersionObj.has("version_number")) {
                            return false; // No suitable update found
                        }
                        
                        latestVersion = latestVersionObj.get("version_number").getAsString();
                        
                        // Get version page URL instead of direct download
                        if (latestVersionObj.has("id")) {
                            String versionId = latestVersionObj.get("id").getAsString();
                            downloadUrl = "https://modrinth.com/plugin/" + modrinthProjectId + "/version/" + versionId;
                        }
                        
                        // Fallback to project page if version ID not found
                        if (downloadUrl == null || downloadUrl.isEmpty()) {
                            downloadUrl = "https://modrinth.com/plugin/" + modrinthProjectId;
                        }
                        
                        // Compare versions
                        return isNewerVersion(latestVersion, currentVersion);
                    }
                } else if (responseCode == 429) {
                    // Rate limited - not critical for 24/7 server
                    plugin.getLogger().info("Version check rate limited by Modrinth API. Will try again later.");
                } else if (responseCode >= 500) {
                    // Server error - not critical for 24/7 server
                    plugin.getLogger().info("Modrinth API temporarily unavailable (HTTP " + responseCode + ")");
                } else {
                    plugin.getLogger().warning("Failed to check for updates. Response code: " + responseCode);
                }
                
                connection.disconnect();
            } catch (java.net.SocketTimeoutException e) {
                // Timeout is not critical for 24/7 server - just log at info level
                plugin.getLogger().info("Version check timed out - this is normal and doesn't affect server operation");
            } catch (java.net.UnknownHostException e) {
                // Network issue - not critical for 24/7 server
                plugin.getLogger().info("Could not reach Modrinth API - check internet connection");
            } catch (Exception e) {
                // General error - log but don't spam console
                plugin.getLogger().log(Level.WARNING, "Could not check for updates: " + e.getMessage());
            }
            
            return false;
        });
    }
    
    /**
     * Notifies console and ops about available updates
     */
    public void notifyIfUpdateAvailable() {
        checkForUpdate().thenAccept(updateAvailable -> {
            if (updateAvailable && latestVersion != null) {
                // Run on main thread with null check for language manager
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        String downloadUrlFinal = downloadUrl != null ? downloadUrl : "https://modrinth.com/plugin/" + modrinthProjectId;
                        
                        // Log to console (plain text)
                        plugin.getLogger().info("=====================================");
                        plugin.getLogger().info("⚠ Neue Version verfügbar! Aktuell: " + currentVersion + " → Neueste: " + latestVersion);
                        plugin.getLogger().info("Download: " + downloadUrlFinal);
                        plugin.getLogger().info("=====================================");
                        
                        // Use language manager with placeholders
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("current", currentVersion);
                        placeholders.put("latest", latestVersion);
                        placeholders.put("url", downloadUrlFinal);
                        
                        // Get components from language file
                        Component prefix = plugin.getLanguageManager().getComponent("version.prefix");
                        Component updateMessage = plugin.getLanguageManager().getComponent("version.update-available");
                        Component versionInfo = plugin.getLanguageManager().getComponent("version.version-info", placeholders);
                        Component versionLatest = plugin.getLanguageManager().getComponent("version.version-latest", placeholders);
                        Component downloadLink = plugin.getLanguageManager().getComponent("version.download-link", placeholders);
                        
                        // Notify online ops with rich components
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player != null && player.isOnline() && (player.isOp() || player.hasPermission("challenge.host"))) {
                                player.sendMessage(prefix.append(updateMessage));
                                player.sendMessage(versionInfo);
                                player.sendMessage(versionLatest);
                                player.sendMessage(downloadLink);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Error notifying about update: " + e.getMessage());
                    }
                });
            }
        }).exceptionally(throwable -> {
            // Catch any exceptions from the async task to prevent thread issues on 24/7 server
            plugin.getLogger().log(Level.WARNING, "Error during version check: " + throwable.getMessage());
            return null;
        });
    }
    
    /**
     * Gets the current version of the plugin
     */
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    /**
     * Gets the latest available version (null if not checked yet)
     */
    public String getLatestVersion() {
        return latestVersion;
    }
    
    /**
     * Gets the download URL for the latest version (null if not checked yet)
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    /**
     * Checks if an update is available (synchronous)
     * Note: latestVersion must be populated by checkForUpdate() first
     */
    public boolean isUpdateAvailable() {
        return latestVersion != null && isNewerVersion(latestVersion, currentVersion);
    }
    
    /**
     * Checks if a version string represents a pre-release (Alpha, Beta, RC, etc.)
     * Examples:
     *   isPreRelease("Alpha-1.0.0") -> true
     *   isPreRelease("Beta-1.0.0") -> true
     *   isPreRelease("1.0.0") -> false
     * 
     * @param version The version string to check
     * @return true if it's a pre-release, false otherwise
     */
    private boolean isPreRelease(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        
        // Check if the version starts with a letter (indicating a prefix like Alpha-, Beta-, etc.)
        return !Character.isDigit(version.charAt(0));
    }
    
    /**
     * Extracts the base version number without any pre-release prefix
     * Examples:
     *   getBaseVersion("Alpha-1.3.80") -> "1.3.80"
     *   getBaseVersion("Beta-2.0.1") -> "2.0.1"
     *   getBaseVersion("1.4.0") -> "1.4.0"
     * 
     * @param version The version string
     * @return The base version number
     */
    private String getBaseVersion(String version) {
        if (version == null || version.isEmpty()) {
            return version;
        }
        
        // Find where the version number starts (first digit)
        for (int i = 0; i < version.length(); i++) {
            if (Character.isDigit(version.charAt(i))) {
                return version.substring(i);
            }
        }
        
        return version;
    }
    
    /**
     * Compares two version strings to determine if version1 is newer than version2
     * Uses semantic versioning comparison (major.minor.patch)
     * Examples:
     *   isNewerVersion("1.4.0", "Alpha-1.3.80") -> true (1.4 > 1.3)
     *   isNewerVersion("1.3.80", "1.3.63") -> true (80 > 63)
     *   isNewerVersion("1.3.47", "1.3.63") -> false (47 < 63)
     *   isNewerVersion("2.0.0", "1.9.9") -> true (2 > 1)
     * 
     * @param version1 First version string (potentially newer)
     * @param version2 Second version string (current)
     * @return true if version1 is newer than version2
     */
    private boolean isNewerVersion(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return false;
        }
        
        // Extract base version numbers (remove any pre-release prefix)
        String v1 = getBaseVersion(version1);
        String v2 = getBaseVersion(version2);
        
        // Split into parts
        String[] parts1 = v1.split("[.\\-]");
        String[] parts2 = v2.split("[.\\-]");
        
        // Compare each part
        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            int num1 = 0;
            int num2 = 0;
            
            try {
                if (i < parts1.length) {
                    num1 = Integer.parseInt(parts1[i]);
                }
                if (i < parts2.length) {
                    num2 = Integer.parseInt(parts2[i]);
                }
            } catch (NumberFormatException e) {
                // If parsing fails, treat as 0
                continue;
            }
            
            if (num1 > num2) {
                return true; // version1 is newer
            } else if (num1 < num2) {
                return false; // version2 is newer
            }
            // If equal, continue to next part
        }
        
        // Versions are equal
        return false;
    }
}
