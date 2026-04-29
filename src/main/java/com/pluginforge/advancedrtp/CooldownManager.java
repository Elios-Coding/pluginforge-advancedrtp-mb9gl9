package com.pluginforge.advancedrtp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public class CooldownManager {
    private final AdvancedRtpPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public CooldownManager(AdvancedRtpPlugin plugin) {
        this.plugin = plugin;
    }

    public void markUsed(Player player) {
        lastUse.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public long getRemainingSeconds(Player player) {
        long cooldown = plugin.getConfig().getInt("cooldown-seconds", 300);
        Long used = lastUse.get(player.getUniqueId());
        if (used == null) return 0L;
        long elapsed = (System.currentTimeMillis() - used) / 1000L;
        long remaining = cooldown - elapsed;
        return Math.max(0L, remaining);
    }
}
