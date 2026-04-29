package com.pluginforge.advancedrtp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class CombatTracker implements Listener {
    private final AdvancedRtpPlugin plugin;
    private final Map<UUID, Long> combatTimes = new HashMap<>();

    public CombatTracker(AdvancedRtpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) return;
        long now = System.currentTimeMillis();
        combatTimes.put(((Player) event.getEntity()).getUniqueId(), now);
        combatTimes.put(((Player) event.getDamager()).getUniqueId(), now);
    }

    public boolean isInCombat(Player player) {
        long seconds = plugin.getConfig().getInt("combat-seconds", 10);
        Long last = combatTimes.get(player.getUniqueId());
        if (last == null) return false;
        boolean active = System.currentTimeMillis() - last < seconds * 1000L;
        if (!active) combatTimes.remove(player.getUniqueId());
        return active;
    }
}
