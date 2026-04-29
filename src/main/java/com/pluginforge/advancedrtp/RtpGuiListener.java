package com.pluginforge.advancedrtp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RtpGuiListener implements Listener {
    private static final String TITLE = "Select RTP Distance";
    private final AdvancedRtpPlugin plugin;

    public RtpGuiListener(AdvancedRtpPlugin plugin) {
        this.plugin = plugin;
    }

    public static void openMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 9, TITLE);
        inventory.setItem(1, createItem("100 - 2500", Material.GRASS_BLOCK));
        inventory.setItem(3, createItem("2500 - 10000", Material.COMPASS));
        inventory.setItem(5, createItem("10000 - 25000", Material.ENDER_PEARL));
        inventory.setItem(7, createItem("25000 - 100000", Material.ELYTRA));
        player.openInventory(inventory);
    }

    private static ItemStack createItem(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) return;
        String name = item.getItemMeta().getDisplayName();
        int min;
        int max;
        if (name.equals("100 - 2500")) {
            min = 100;
            max = 2500;
        } else if (name.equals("2500 - 10000")) {
            min = 2500;
            max = 10000;
        } else if (name.equals("10000 - 25000")) {
            min = 10000;
            max = 25000;
        } else if (name.equals("25000 - 100000")) {
            min = 25000;
            max = 100000;
        } else {
            return;
        }
        plugin.setSelectedRange(min, max);
        player.closeInventory();
        player.sendMessage("RTP range set to " + min + " - " + max + " blocks.");
    }
}
