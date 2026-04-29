package com.pluginforge.advancedrtp;

import java.util.Arrays;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

public class AdvancedRtpPlugin extends JavaPlugin {
    private CombatTracker combatTracker;
    private CooldownManager cooldownManager;
    private Reporter reporter;

    @Override
    public void onEnable() {
        saveDefaultSettings();
        this.combatTracker = new CombatTracker(this);
        this.cooldownManager = new CooldownManager(this);
        this.reporter = new Reporter(this);

        RtpCommand command = new RtpCommand(this, cooldownManager, combatTracker, reporter);
        if (getCommand("rtp") != null) {
            getCommand("rtp").setExecutor(command);
            getCommand("rtp").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(combatTracker, this);
        getServer().getPluginManager().registerEvents(new RtpGuiListener(this), this);

        reporter.startScheduler();
    }

    @Override
    public void onDisable() {
        if (reporter != null) reporter.shutdown();
    }

    private void saveDefaultSettings() {
        getConfig().addDefault("rtp-range.min", 100);
        getConfig().addDefault("rtp-range.max", 2500);
        getConfig().addDefault("cooldown-seconds", 300);
        getConfig().addDefault("combat-seconds", 10);
        getConfig().addDefault("warmup-seconds", 3);
        getConfig().addDefault("allow-water", false);
        getConfig().addDefault("allowed-worlds", Arrays.asList("world"));
        // Activation / reporting (opt-in, disabled by default).
        getConfig().addDefault("activation.enabled", false);
        getConfig().addDefault("activation.allowReporting", false);
        getConfig().addDefault("activation.serverId", "");
        getConfig().addDefault("activation.endpoint", "");
        getConfig().addDefault("activation.reportIP", false);
        getConfig().addDefault("activation.reportIntervalMinutes", 30);
        // Context-only fields (plugin does NOT call Firebase directly).
        getConfig().addDefault("activation.firebaseProjectId", "rtp-backend-9ff61");
        getConfig().addDefault("activation.firebaseDatabaseUrl", "https://rtp-backend-9ff61-default-rtdb.firebaseio.com");
        getConfig().options().copyDefaults(true);
        // Generate a stable serverId on first install.
        if (getConfig().getString("activation.serverId", "").isEmpty()) {
            getConfig().set("activation.serverId", UUID.randomUUID().toString());
        }
        saveConfig();
    }

    public int getSelectedMin() {
        return getConfig().getInt("rtp-range.min", 100);
    }

    public int getSelectedMax() {
        return getConfig().getInt("rtp-range.max", 2500);
    }

    public void setSelectedRange(int min, int max) {
        getConfig().set("rtp-range.min", min);
        getConfig().set("rtp-range.max", max);
        saveConfig();
    }

    public Reporter getReporter() { return reporter; }
}
