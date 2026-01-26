package de.connunity.util.challenge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
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
                        // Get the latest version (first in the array)
                        JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                        
                        // Validate version_number exists
                        if (!latestVersionObj.has("version_number")) {
                            plugin.getLogger().warning("Invalid Modrinth API response: missing version_number");
                            return false;
                        }
                        
                        latestVersion = latestVersionObj.get("version_number").getAsString();
                        
                        // Get download URL from files array with validation
                        if (latestVersionObj.has("files")) {
                            JsonArray files = latestVersionObj.getAsJsonArray("files");
                            if (files.size() > 0) {
                                JsonObject primaryFile = files.get(0).getAsJsonObject();
                                if (primaryFile.has("url")) {
                                    downloadUrl = primaryFile.get("url").getAsString();
                                }
                            }
                        }
                        
                        // Fallback download URL if not found in API response
                        if (downloadUrl == null || downloadUrl.isEmpty()) {
                            downloadUrl = "https://modrinth.com/plugin/" + modrinthProjectId;
                        }
                        
                        // Compare versions (supporting prefixes like Alpha-, Beta-, etc.)
                        return !areVersionsEqual(currentVersion, latestVersion);
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
                        String prefix = plugin.getLanguageManager().getMessage("version.prefix");
                        String updateMessage = plugin.getLanguageManager().getMessage("version.update-available")
                                .replace("{current}", currentVersion)
                                .replace("{latest}", latestVersion);
                        String downloadMessage = plugin.getLanguageManager().getMessage("version.download-url")
                                .replace("{url}", downloadUrl != null ? downloadUrl : "https://modrinth.com/plugin/" + modrinthProjectId);
                        
                        // Log to console
                        plugin.getLogger().info("=====================================");
                        plugin.getLogger().info(updateMessage);
                        plugin.getLogger().info(downloadMessage);
                        plugin.getLogger().info("=====================================");
                        
                        // Notify online ops (with null check for stability)
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (player != null && player.isOnline() && (player.isOp() || player.hasPermission("challenge.host"))) {
                                player.sendMessage(prefix + updateMessage);
                                player.sendMessage(prefix + downloadMessage);
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
        return latestVersion != null && !areVersionsEqual(currentVersion, latestVersion);
    }
    
    /**
     * Compares two version strings, supporting prefixes like "Alpha-", "Beta-", etc.
     * Examples:
     *   areVersionsEqual("Alpha-1.0.0", "Alpha-1.0.0") -> true
     *   areVersionsEqual("Alpha-1.0.0", "Alpha-1.0.1") -> false
     *   areVersionsEqual("1.0.0", "1.0.0") -> true
     *   areVersionsEqual("Beta-1.0.0", "Alpha-1.0.0") -> false (different prefixes)
     * 
     * @param version1 First version string
     * @param version2 Second version string
     * @return true if versions are equal, false otherwise
     */
    private boolean areVersionsEqual(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return version1 == version2;
        }
        
        // Extract prefix and version number
        String[] parts1 = splitVersionPrefix(version1);
        String[] parts2 = splitVersionPrefix(version2);
        
        String prefix1 = parts1[0];
        String prefix2 = parts2[0];
        String versionNum1 = parts1[1];
        String versionNum2 = parts2[1];
        
        // Prefixes must match
        if (!prefix1.equals(prefix2)) {
            return false;
        }
        
        // Version numbers must match
        return versionNum1.equals(versionNum2);
    }
    
    /**
     * Splits a version string into prefix and version number parts
     * Examples:
     *   "Alpha-1.0.0" -> ["Alpha-", "1.0.0"]
     *   "Beta-1.0.0" -> ["Beta-", "1.0.0"]
     *   "1.0.0" -> ["", "1.0.0"]
     * 
     * @param version The version string to split
     * @return Array with [prefix, versionNumber]
     */
    private String[] splitVersionPrefix(String version) {
        // Match any prefix before a version number pattern (e.g., "Alpha-", "Beta-", "RC-")
        // Version number pattern: starts with a digit
        int versionStart = -1;
        for (int i = 0; i < version.length(); i++) {
            if (Character.isDigit(version.charAt(i))) {
                versionStart = i;
                break;
            }
        }
        
        if (versionStart == -1) {
            // No digit found, treat entire string as version
            return new String[]{"", version};
        } else if (versionStart == 0) {
            // No prefix
            return new String[]{"", version};
        } else {
            // Has prefix
            String prefix = version.substring(0, versionStart);
            String versionNum = version.substring(versionStart);
            return new String[]{prefix, versionNum};
        }
    }
}
