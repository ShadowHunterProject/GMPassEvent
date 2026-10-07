package com.gmpass.event;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {
    private final GMPassEvent plugin;
    private final Map<String, String> messages = new HashMap<>();

    public MessageManager(GMPassEvent plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        File file = new File(plugin.getDataFolder(), "message.yml");
        if (!file.exists()) {
            plugin.saveResource("message.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        messages.clear();

        for (String key : config.getKeys(false)) {
            messages.put(key, ChatColor.translateAlternateColorCodes('&', config.getString(key)));
        }
    }

    public String get(String key) {
        return messages.getOrDefault(key, "&cMissing message: " + key);
    }

    public String getWithPrefix(String key) {
        return get("prefix") + get(key);
    }
}
