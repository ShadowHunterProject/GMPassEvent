package com.gmpass.event;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class EventManager {
    private final GMPassEvent plugin;
    private final Map<String, EventNode> activeEvents = new HashMap<>();
    private BukkitTask proximityTask;

    public EventManager(GMPassEvent plugin) {
        this.plugin = plugin;
    }

    public void loadEvents() {
        despawnAll();
        
        ConfigurationSection eventsSection = plugin.getConfig().getConfigurationSection("events");
        if (eventsSection == null) return;

        for (String key : eventsSection.getKeys(false)) {
            ConfigurationSection sec = eventsSection.getConfigurationSection(key);
            if (sec == null) continue;

            String locStr = sec.getString("location");
            Location loc = parseLocation(locStr);
            if (loc == null) {
                plugin.getLogger().log(Level.WARNING, "Toạ độ không hợp lệ cho sự kiện: " + key);
                continue;
            }

            String headId = sec.getString("head-base64", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2Y1MDQxZGI4OWFmYzhlMTNhZjEwMDFkOTJkZDY2YjNmMzliMDdjOWQwYWM4OTIxOTRjYTdhMmQ3MDVlMzI2ZiJ9fX0=");
            String particle = sec.getString("particle", "SQUID_INK");
            int showRadius = sec.getInt("show-radius", 10);
            
            double holoOffset = sec.getDouble("hologram-offset-y", 1.8);
            double headOffset = sec.getDouble("head-offset-y", 0.5);
            double bobbingAmplitude = sec.getDouble("bobbing-amplitude", 0.4);
            
            String mythicSkill = sec.getString("mythic-skill", "");
            int mythicInterval = sec.getInt("mythic-skill-interval", 40);
            
            List<String> commands = sec.getStringList("click-commands");
            List<String> holoLines = sec.getStringList("hologram-lines");

            EventNode node = new EventNode(plugin, key, loc, headId, particle, showRadius, holoOffset, headOffset, bobbingAmplitude, mythicSkill, mythicInterval, commands, holoLines);
            activeEvents.put(key, node);
        }
        
        plugin.getLogger().info("Đã tải " + activeEvents.size() + " điểm sự kiện.");
        
        // Khởi động vòng lặp kiểm tra khoảng cách
        startProximityTask();
    }

    private void startProximityTask() {
        if (proximityTask != null) proximityTask.cancel();
        
        // Cứ mỗi 1 giây (20 tick) check 1 lần xem có player ở gần không
        proximityTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (EventNode node : activeEvents.values()) {
                    Location loc = node.getLocation();
                    if (!loc.isWorldLoaded()) continue;
                    
                    boolean hasPlayerNearby = false;
                    for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, node.getShowRadius(), node.getShowRadius(), node.getShowRadius())) {
                        if (e instanceof org.bukkit.entity.Player) {
                            hasPlayerNearby = true;
                            break;
                        }
                    }
                    
                    if (hasPlayerNearby && !node.isSpawned()) {
                        node.spawn(); // Player tới gần -> Hiện ra
                    } else if (!hasPlayerNearby && node.isSpawned()) {
                        node.despawn(); // Không có ai -> Tắt đi
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // Bắt đầu sau 2 giây (đợi HDB tải)
    }

    public void despawnAll() {
        if (proximityTask != null) proximityTask.cancel();
        for (EventNode node : activeEvents.values()) {
            node.despawn();
        }
        activeEvents.clear();
    }

    public void removeEvent(String id) {
        activeEvents.remove(id);
    }

    public EventNode getEvent(String id) {
        return activeEvents.get(id);
    }

    private Location parseLocation(String str) {
        if (str == null) return null;
        String[] parts = str.split(",");
        if (parts.length != 4) return null;
        try {
            World w = plugin.getServer().getWorld(parts[0]);
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(w, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }
}
