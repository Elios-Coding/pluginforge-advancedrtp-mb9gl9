package com.pluginforge.advancedrtp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Optional opt-in activation / reporting client.
 *
 * The plugin NEVER talks to Firebase directly. It only POSTs a small JSON
 * payload to the user-configured backend endpoint. That backend is responsible
 * for authenticating to Firebase with its own service account.
 *
 * No private keys, service accounts, or admin credentials are stored or used
 * by the plugin.
 */
public class Reporter {
    private final AdvancedRtpPlugin plugin;
    private final AtomicReference<String> externalIp = new AtomicReference<>(null);
    private BukkitTask task;

    public Reporter(AdvancedRtpPlugin plugin) {
        this.plugin = plugin;
        // Discover external IP once asynchronously so /rtp activate can show it.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::refreshExternalIp);
    }

    public String getCachedExternalIp() {
        String ip = externalIp.get();
        return ip == null ? "(discovering...)" : ip;
    }

    public void startScheduler() {
        if (!plugin.getConfig().getBoolean("activation.enabled", false)) {
            plugin.getLogger().info("Activation disabled (opt-in). No reporting will occur.");
            return;
        }
        if (!plugin.getConfig().getBoolean("activation.allowReporting", false)) {
            plugin.getLogger().info("Activation enabled but reporting is disabled.");
            return;
        }
        String endpoint = plugin.getConfig().getString("activation.endpoint", "");
        if (endpoint == null || endpoint.isEmpty()) {
            plugin.getLogger().warning("Activation enabled but no endpoint configured. Skipping reports.");
            return;
        }
        int minutes = Math.max(1, plugin.getConfig().getInt("activation.reportIntervalMinutes", 30));
        long ticks = minutes * 60L * 20L;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendNow, 20L * 30L, ticks);
        plugin.getLogger().info("AdvancedRTP reporting enabled. Sending anonymous usage data every " + minutes + "m to " + endpoint);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void sendNow() {
        if (!plugin.getConfig().getBoolean("activation.enabled", false)) return;
        if (!plugin.getConfig().getBoolean("activation.allowReporting", false)) return;
        final String endpoint = plugin.getConfig().getString("activation.endpoint", "");
        if (endpoint == null || endpoint.isEmpty()) return;
        final String serverId = plugin.getConfig().getString("activation.serverId", "");
        final boolean reportIp = plugin.getConfig().getBoolean("activation.reportIP", false);
        final int playersOnline = Bukkit.getOnlinePlayers().size();
        final String serverVersion = Bukkit.getBukkitVersion();
        final String serverSoftware = Bukkit.getName() + " " + Bukkit.getVersion();
        final String pluginVersion = plugin.getDescription().getVersion();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (reportIp && externalIp.get() == null) refreshExternalIp();
            String ip = reportIp ? externalIp.get() : null;
            StringBuilder json = new StringBuilder();
            json.append('{');
            json.append("\"serverId\":\"").append(escape(serverId)).append("\",");
            json.append("\"plugin\":\"AdvancedRTP\",");
            json.append("\"pluginVersion\":\"").append(escape(pluginVersion)).append("\",");
            json.append("\"serverVersion\":\"").append(escape(serverVersion)).append("\",");
            json.append("\"serverSoftware\":\"").append(escape(serverSoftware)).append("\",");
            json.append("\"playersOnline\":").append(playersOnline).append(',');
            json.append("\"lastSeen\":").append(System.currentTimeMillis());
            if (reportIp && ip != null) {
                json.append(',').append("\"ipAddress\":\"").append(escape(ip)).append("\"");
            }
            json.append('}');
            try {
                plugin.getLogger().info("Sending anonymous usage data to configured endpoint...");
                HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "AdvancedRTP/" + pluginVersion);
                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                int code = conn.getResponseCode();
                plugin.getLogger().info("Report endpoint responded with HTTP " + code);
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Report failed: " + e.getMessage());
            }
        });
    }

    private void refreshExternalIp() {
        String[] services = new String[] {
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "https://icanhazip.com"
        };
        for (String url : services) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", "AdvancedRTP-IPDiscovery");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line = r.readLine();
                        if (line != null) {
                            String ip = line.trim();
                            if (!ip.isEmpty()) {
                                externalIp.set(ip);
                                conn.disconnect();
                                return;
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) { }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
