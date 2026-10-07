package com.gmpass.event;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.hologram.Hologram;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.data.HologramData;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.logging.Level;

public class EventNode {

    private final GMPassEvent plugin;
    private final String id;
    private final Location location;
    private final String headId;
    private final String particleName;
    private final int showRadius;
    private final double holoOffset;
    private final double headOffset;
    private final double bobbingAmplitude;
    private final String mythicSkill;
    private final int mythicInterval;
    
    private final List<String> commands;
    private final List<String> hologramLines;

    private boolean isSpawned = false;
    private ItemDisplay itemDisplay;
    private Interaction interaction;
    private BukkitTask particleTask;
    private BukkitTask bobbingTask;
    private Hologram hologram;

    public EventNode(GMPassEvent plugin, String id, Location location, String headId, String particleName, int showRadius, 
                     double holoOffset, double headOffset, double bobbingAmplitude, String mythicSkill, int mythicInterval, 
                     List<String> commands, List<String> hologramLines) {
        this.plugin = plugin;
        this.id = id;
        this.location = location.clone();
        this.headId = headId;
        this.particleName = particleName;
        this.showRadius = showRadius;
        this.holoOffset = holoOffset;
        this.headOffset = headOffset;
        this.bobbingAmplitude = bobbingAmplitude;
        this.mythicSkill = mythicSkill;
        this.mythicInterval = mythicInterval;
        this.commands = commands;
        this.hologramLines = hologramLines;
    }

    public void spawn() {
        if (isSpawned) return;
        if (!location.isWorldLoaded()) return;

        // 0. Dọn dẹp Zombie Entities (Thực thể rác) từ những lần server crash trước
        for (org.bukkit.entity.Entity e : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
            if (e.getPersistentDataContainer().has(plugin.getEventKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                e.remove();
            }
        }

        // 1. Tạo ItemStack từ Base64
        ItemStack headItem = getHeadFromBase64(headId);
        if (headItem == null) headItem = new ItemStack(Material.PLAYER_HEAD);

        // 2. Tạo ItemDisplay
        Location headLoc = location.clone().add(0, headOffset, 0);
        itemDisplay = (ItemDisplay) location.getWorld().spawnEntity(headLoc, EntityType.ITEM_DISPLAY);
        itemDisplay.setItemStack(headItem);
        // Thiết lập kích thước hiển thị
        Transformation trans = itemDisplay.getTransformation();
        trans.getScale().set(1.5f, 1.5f, 1.5f); // Đầu to ra một chút cho đẹp
        itemDisplay.setTransformation(trans);
        // Bật interpolation cho chuyển động mượt
        itemDisplay.setInterpolationDelay(0);
        itemDisplay.setInterpolationDuration(20); 
        
        // Đánh dấu ItemDisplay để dọn dẹp
        itemDisplay.getPersistentDataContainer().set(plugin.getEventKey(), org.bukkit.persistence.PersistentDataType.STRING, this.id);

        // 3. Tạo Interaction Entity để click
        Location interactLoc = location.clone().add(0, headOffset, 0);
        interaction = (Interaction) location.getWorld().spawnEntity(interactLoc, EntityType.INTERACTION);
        interaction.setInteractionWidth(1.2f);
        interaction.setInteractionHeight(1.5f);
        
        // Gắn metadata để nhận diện sự kiện
        interaction.getPersistentDataContainer().set(plugin.getEventKey(), org.bukkit.persistence.PersistentDataType.STRING, this.id);

        // 4. Tạo FancyHolograms
        spawnHologram();

        // 5. Chạy Particle và Bobbing
        startTasks();
        
        isSpawned = true;
    }

    private void spawnHologram() {
        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Location holoLoc = location.clone().add(0, holoOffset, 0); 
            
            // Xóa holo cũ nếu có (tránh trùng)
            manager.getHologram("gme_" + id).ifPresent(manager::removeHologram);

            TextHologramData holoData = new TextHologramData("gme_" + id, holoLoc);
            holoData.setText(hologramLines);
            // Thiết lập nền trong suốt (transparent)
            holoData.setBackground(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            
            this.hologram = manager.create(holoData);
            manager.addHologram(hologram);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Lỗi khi tạo Hologram FancyHolograms: " + e.getMessage(), e);
        }
    }

    private void startTasks() {
        // Task lơ lửng + xoáy vòng (Spin & Bobbing)
        bobbingTask = new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (itemDisplay == null || !itemDisplay.isValid()) {
                    this.cancel();
                    return;
                }
                if (!location.isWorldLoaded() || !location.getChunk().isLoaded()) {
                    return; // Bỏ qua nếu chunk bị unload để tránh lỗi
                }
                
                tick += 5;
                Transformation t = itemDisplay.getTransformation();
                
                // 1. Tính toán nhấp nhô (sine wave)
                float yOffset = (float) (Math.sin(tick * 0.1) * bobbingAmplitude);
                t.getTranslation().set(0, yOffset, 0);
                
                // 2. Tính toán xoay (Quay 180 độ theo trục Y mỗi chu kỳ)
                // tick càng tăng, góc xoay càng tăng
                t.getRightRotation().rotateY((float) Math.toRadians(15)); // Xoay 15 độ mỗi 5 tick
                
                itemDisplay.setInterpolationDelay(0);
                itemDisplay.setInterpolationDuration(5);
                itemDisplay.setTransformation(t);
            }
        }.runTaskTimer(plugin, 0L, 5L); // Cập nhật siêu mượt mỗi 5 tick (0.25s)

        // Task Particle quay vòng
        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (Exception e) {
            particle = Particle.HAPPY_VILLAGER; // Default
        }
        final Particle finalParticle = particle;
        
        // Task Particle / MythicMobs
        if (mythicSkill != null && !mythicSkill.trim().isEmpty()) {
            // Sử dụng MythicMobs
            particleTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (itemDisplay == null || !itemDisplay.isValid() || !location.isWorldLoaded()) {
                        return;
                    }
                    try {
                        io.lumine.mythic.bukkit.MythicBukkit.inst().getAPIHelper().castSkill(itemDisplay, mythicSkill);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Không thể cast MythicMobs skill: " + mythicSkill, e);
                    }
                }
            }.runTaskTimer(plugin, 0L, mythicInterval);
        } else {
            // Sử dụng vòng xoáy Particle mặc định (Helix / Trôn ốc kép)
            particleTask = new BukkitRunnable() {
                double angle = 0;
                double height = 0; // Thêm trục Y để bay lên

                @Override
                public void run() {
                    if (itemDisplay == null || !itemDisplay.isValid() || !location.isWorldLoaded()) return;
                    
                    angle += 0.2 * Math.PI; // Tốc độ xoay vòng
                    height += 0.06; // Tốc độ bay lên
                    
                    if (height > 2.0) { // Bay cao tối đa 2 block rồi quay lại dưới chân
                        height = 0;
                    }

                    // Tọa độ Hạt 1
                    double x1 = 0.8 * Math.cos(angle);
                    double z1 = 0.8 * Math.sin(angle);
                    Location pLoc1 = itemDisplay.getLocation().clone().add(x1, height, z1);
                    pLoc1.getWorld().spawnParticle(finalParticle, pLoc1, 1, 0, 0, 0, 0);
                    
                    // Tọa độ Hạt 2 (Đối xứng qua tâm, tạo thành ADN/Double Helix)
                    double x2 = 0.8 * Math.cos(angle + Math.PI);
                    double z2 = 0.8 * Math.sin(angle + Math.PI);
                    Location pLoc2 = itemDisplay.getLocation().clone().add(x2, height, z2);
                    pLoc2.getWorld().spawnParticle(finalParticle, pLoc2, 1, 0, 0, 0, 0);
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
    }

    private ItemStack getHeadFromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isEmpty()) return head;
        
        try {
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID());
            org.bukkit.profile.PlayerTextures textures = profile.getTextures();
            
            String decoded = new String(java.util.Base64.getDecoder().decode(base64));
            String urlString = decoded.substring(decoded.indexOf("\"url\":\"") + 7);
            urlString = urlString.substring(0, urlString.indexOf("\""));
            
            textures.setSkin(new java.net.URL(urlString));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Base64 không hợp lệ: " + base64);
        }
        return head;
    }

    public void despawn() {
        if (!isSpawned) return;
        
        if (bobbingTask != null) bobbingTask.cancel();
        if (particleTask != null) particleTask.cancel();
        
        if (itemDisplay != null && itemDisplay.isValid()) itemDisplay.remove();
        if (interaction != null && interaction.isValid()) interaction.remove();
        
        if (hologram != null) {
            try {
                FancyHologramsPlugin.get().getHologramManager().removeHologram(hologram);
            } catch (Exception ignored) {}
        }
        
        isSpawned = false;
    }

    public String getId() { return id; }
    public Location getLocation() { return location; }
    public int getShowRadius() { return showRadius; }
    public boolean isSpawned() { return isSpawned; }
    public List<String> getCommands() { return commands; }
}
