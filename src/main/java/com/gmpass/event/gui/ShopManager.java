package com.gmpass.event.gui;

import com.gmpass.event.GMPassEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopManager implements Listener {

    private final GMPassEvent plugin;
    private final Map<String, ShopData> shops = new HashMap<>();

    public ShopManager(GMPassEvent plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadConfig();
    }

    public void loadConfig() {
        for (ShopData shop : shops.values()) {
            shop.cancelTasks();
        }
        shops.clear();

        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
            plugin.saveResource("gui/shop.yml", false);
        }

        File[] files = guiFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".yml")) {
                    String shopId = file.getName().replace(".yml", "");
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    ShopData shopData = new ShopData(plugin, shopId, config);
                    shops.put(shopId.toLowerCase(), shopData);
                }
            }
        }
    }

    public void openShop(Player player, String shopId) {
        ShopData shop = shops.get(shopId.toLowerCase());
        if (shop != null) {
            shop.open(player);
        } else {
            player.sendMessage(plugin.getMessageManager().getWithPrefix("shop-not-found"));
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String cmdStr = event.getMessage().substring(1).toLowerCase(); // bo dau /
        String[] parts = cmdStr.split(" ");
        String baseCmd = parts[0];

        for (Map.Entry<String, ShopData> entry : shops.entrySet()) {
            List<String> aliases = entry.getValue().getAliases();
            if (aliases != null && aliases.contains(baseCmd)) {
                event.setCancelled(true);
                entry.getValue().open(event.getPlayer());
                return;
            }
        }
    }
}
