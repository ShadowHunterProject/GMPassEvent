package com.gmpass.event.gui;

import com.gmpass.event.GMPassEvent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.net.URL;

import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import vn.gmcore.gui.ConfigGUI;
import vn.gmcore.gui.GUIItem;

public class ShopData {
    private final GMPassEvent plugin;
    private final String shopId;
    private final FileConfiguration config;

    private ConfigGUI shopGui;
    private BukkitTask broadcastTask;
    private BukkitTask updateTitleTask;
    private long expireTimeMs = -1;
    private String title;
    private List<String> aliases = new ArrayList<>();
    private final java.util.Set<UUID> viewers = new java.util.HashSet<>();
    
    private static class ItemState {
        int price;
        int maxStock;
        int currentStock;
        boolean hasStock;
    }
    
    private final java.util.Map<Character, ItemState> itemStates = new java.util.HashMap<>();
    private final java.util.Map<Character, ConfigurationSection> dynamicTimeItems = new java.util.HashMap<>();
    private final java.util.Map<Character, List<Integer>> dynamicTimeSlots = new java.util.HashMap<>();
    private final java.util.Map<Character, ItemStack> dynamicItemStacks = new java.util.HashMap<>();

    public ShopData(GMPassEvent plugin, String shopId, FileConfiguration config) {
        this.plugin = plugin;
        this.shopId = shopId;
        this.config = config;
        init();
    }

    private void init() {
        String expireStr = config.getString("expire-time");
        if (expireStr != null && !expireStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                expireTimeMs = sdf.parse(expireStr).getTime();
            } catch (Exception e) {
                plugin.getLogger().warning("Không thể đọc định dạng expire-time ở " + shopId + "!");
                expireTimeMs = -1;
            }
        }

        aliases = config.getStringList("aliases");
        
        buildGUI();
        setupBroadcast();
        startTitleUpdater();
    }

    private boolean isExpired() {
        return expireTimeMs > 0 && System.currentTimeMillis() > expireTimeMs;
    }

    private void buildGUI() {
        if (!Bukkit.getPluginManager().isPluginEnabled("GMCore")) return;

        title = config.getString("title", "&8Cửa Hàng");
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                title = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, title);
            } catch (Exception e) {}
        }
        List<String> layout = config.getStringList("layout");
        java.util.Map<Character, List<Integer>> charToSlots = new java.util.HashMap<>();
        for (int i = 0; i < layout.size(); i++) {
            String row = layout.get(i);
            for (int j = 0; j < row.length() && j < 9; j++) {
                char c = row.charAt(j);
                charToSlots.computeIfAbsent(c, k -> new ArrayList<>()).add(i * 9 + j);
            }
        }

        shopGui = new ConfigGUI();
        shopGui.setTitle(getTitleWithTime());
        shopGui.setLayout(layout);

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String keyStr : itemsSection.getKeys(false)) {
                if (keyStr.length() != 1) continue;
                char key = keyStr.charAt(0);
                ConfigurationSection itemCfg = itemsSection.getConfigurationSection(keyStr);
                
                ItemState state = new ItemState();
                int price = 0;
                if (itemCfg.isString("price")) {
                    String priceStr = itemCfg.getString("price");
                    if (priceStr.contains("-")) {
                        String[] parts = priceStr.split("-");
                        try {
                            int min = Integer.parseInt(parts[0].trim());
                            int max = Integer.parseInt(parts[1].trim());
                            // Random nghiêng về số cao (dùng hàm phân phối 1 - r^3)
                            double r = 1.0 - Math.pow(Math.random(), 3.0);
                            price = min + (int) Math.round(r * (max - min));
                        } catch (Exception e) { price = 0; }
                    } else {
                        try { price = Integer.parseInt(priceStr.trim()); } catch (Exception e) {}
                    }
                } else {
                    price = itemCfg.getInt("price", 0);
                }

                int stock = itemCfg.getInt("stock", -1);
                state.price = price;
                state.maxStock = stock;
                state.currentStock = stock;
                state.hasStock = stock != -1;
                itemStates.put(key, state);
                
                boolean isDynamic = false;
                if (itemCfg.contains("name")) {
                    String name = itemCfg.getString("name");
                    if (name.contains("%time%") || name.contains("%price%") || name.contains("%stock%")) isDynamic = true;
                }
                if (itemCfg.contains("lore")) {
                    for (String l : itemCfg.getStringList("lore")) {
                        if (l.contains("%time%") || l.contains("%price%") || l.contains("%stock%")) isDynamic = true;
                    }
                }
                if (state.hasStock) isDynamic = true;
                
                if (isDynamic) {
                    dynamicTimeItems.put(key, itemCfg);
                    dynamicTimeSlots.put(key, charToSlots.getOrDefault(key, new ArrayList<>()));
                }
                
                ItemStack item = buildItem(itemCfg, true, state);
                if (isDynamic) {
                    dynamicItemStacks.put(key, item);
                }

                List<String> commands = itemCfg.getStringList("commands");

                GUIItem guiItem = new GUIItem(item, event -> {
                    event.setCancelled(true);
                    if (!(event.getWhoClicked() instanceof Player)) return;
                    Player p = (Player) event.getWhoClicked();

                    if (isExpired() && state.price > 0) {
                        p.sendMessage(plugin.getMessageManager().getWithPrefix("shop-expired"));
                        return;
                    }
                    
                    if (state.hasStock && state.currentStock <= 0) {
                        p.sendMessage("§cMặt hàng này đã hết!");
                        return;
                    }

                    if (state.price > 0 && !commands.isEmpty()) {
                        if (handlePurchase(p, state.price, commands)) {
                            if (state.hasStock) {
                                state.currentStock--;
                                Bukkit.getScheduler().runTask(plugin, () -> updateDynamicItem(key));
                            }
                        }
                    }
                });

                shopGui.setIngredient(key, guiItem);
            }
        }
    }

    private boolean handlePurchase(Player player, int price, List<String> commands) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
            player.sendMessage("§cPlugin PlayerPoints chưa được cài đặt!");
            return false;
        }

        PlayerPointsAPI ppAPI = PlayerPoints.getInstance().getAPI();
        int balance = ppAPI.look(player.getUniqueId());

        if (balance < price) {
            String msg = plugin.getMessageManager().getWithPrefix("not-enough-points").replace("%price%", String.valueOf(price));
            player.sendMessage(msg);
            return false;
        }

        if (ppAPI.take(player.getUniqueId(), price)) {
            String msg = plugin.getMessageManager().getWithPrefix("buy-success").replace("%price%", String.valueOf(price));
            player.sendMessage(msg);
            
            for (String cmd : commands) {
                String parsedCmd = cmd.replace("%player%", player.getName());
                if (parsedCmd.toLowerCase().startsWith("[player] ")) {
                    player.performCommand(parsedCmd.substring(9));
                } else if (parsedCmd.toLowerCase().startsWith("[console] ")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd.substring(10));
                } else if (parsedCmd.toLowerCase().startsWith("msg ")) {
                    player.sendMessage(parsedCmd.substring(4).replace("&", "§"));
                } else if (parsedCmd.toLowerCase().startsWith("broadcast ")) {
                    Bukkit.broadcastMessage(parsedCmd.substring(10).replace("&", "§"));
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                }
            }
            return true;
        } else {
            player.sendMessage(plugin.getMessageManager().getWithPrefix("buy-error"));
            return false;
        }
    }

    private void setupBroadcast() {
        ConfigurationSection bCfg = config.getConfigurationSection("broadcast");
        if (bCfg == null || !bCfg.getBoolean("enabled", false)) return;
        
        int interval = bCfg.getInt("interval-minutes", 15);
        List<String> messages = bCfg.getStringList("messages");
        String clickTextStr = bCfg.getString("click-text", "&a&l[BẤM VÀO ĐÂY]");
        String hoverTextStr = bCfg.getString("hover-text", "&eNhấp chuột để mở Cửa hàng ngay!");
        
        if (messages.isEmpty() || interval <= 0) return;

        String clickCommand = (aliases != null && !aliases.isEmpty()) ? "/" + aliases.get(0) : "/gme shop " + shopId;

        broadcastTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (isExpired()) {
                Bukkit.getScheduler().runTask(plugin, this::buildGUI);
                broadcastTask.cancel();
                return;
            }
            
            for (String msg : messages) {
                String coloredMsg = ChatColor.translateAlternateColorCodes('&', msg);
                
                if (coloredMsg.contains("%click%")) {
                    int index = coloredMsg.indexOf("%click%");
                    String before = coloredMsg.substring(0, index);
                    String after = coloredMsg.substring(index + 7);
                    
                    TextComponent base = new TextComponent(before);
                    
                    TextComponent clickable = new TextComponent(ChatColor.translateAlternateColorCodes('&', clickTextStr));
                    clickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
                    clickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.translateAlternateColorCodes('&', hoverTextStr))));
                    
                    base.addExtra(clickable);
                    base.addExtra(new TextComponent(after));
                    
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.spigot().sendMessage(base);
                    }
                } else {
                    TextComponent component = new TextComponent(coloredMsg);
                    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, clickCommand));
                    String translatedTitle = ChatColor.translateAlternateColorCodes('&', title);
                    component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§eNhấp vào đây để mở " + translatedTitle)));
                    
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.spigot().sendMessage(component);
                    }
                }
            }
        }, interval * 60L * 20L, interval * 60L * 20L);
    }

    public void open(Player player) {
        if (shopGui != null) {
            if (title != null && title.contains("%time%")) {
                shopGui.setTitle(getTitleWithTime());
                viewers.add(player.getUniqueId());
            }

            if (Bukkit.getPluginManager().isPluginEnabled("GMCore")) {
                vn.gmcore.GMCore.getInstance().getGuiManager().openGUI(player, shopGui);
            } else {
                player.openInventory(shopGui.build());
            }
        }
    }

    public void cancelTasks() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
        if (updateTitleTask != null) {
            updateTitleTask.cancel();
            updateTitleTask = null;
        }
    }

    public List<String> getAliases() {
        return aliases;
    }

    private String getFormattedTime() {
        if (expireTimeMs <= 0) return "";
        long diff = expireTimeMs - System.currentTimeMillis();
        if (diff <= 0) return "Hết hạn";
        
        long days = diff / (1000 * 60 * 60 * 24);
        long hours = (diff / (1000 * 60 * 60)) % 24;
        long minutes = (diff / (1000 * 60)) % 60;
        long seconds = (diff / 1000) % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    private String getTitleWithTime() {
        String t = title != null ? title : "&8Cửa Hàng";
        if (t.contains("%time%")) {
            t = t.replace("%time%", getFormattedTime());
        }
        return ChatColor.translateAlternateColorCodes('&', t);
    }

    private void startTitleUpdater() {
        if ((title == null || !title.contains("%time%")) && dynamicTimeItems.isEmpty()) return;

        updateTitleTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (isExpired()) {
                if (updateTitleTask != null) {
                    updateTitleTask.cancel();
                    updateTitleTask = null;
                }
                return;
            }

            String currentTitle = getTitleWithTime();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean hasTimeInTitle = title != null && title.contains("%time%");
                if (hasTimeInTitle && shopGui != null) {
                    shopGui.setTitle(currentTitle);
                }

                for (Character key : dynamicTimeItems.keySet()) {
                    updateDynamicItem(key);
                }

                java.util.Iterator<UUID> iterator = viewers.iterator();
                while (iterator.hasNext()) {
                    UUID uuid = iterator.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        org.bukkit.inventory.InventoryView view = p.getOpenInventory();
                        if (view != null && view.getTopInventory() != null) {
                            if (view.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING && view.getType() != org.bukkit.event.inventory.InventoryType.CREATIVE) {
                                if (hasTimeInTitle) {
                                    try {
                                        view.setTitle(currentTitle);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            } else {
                                iterator.remove();
                            }
                        } else {
                            iterator.remove();
                        }
                    } else {
                        iterator.remove();
                    }
                }
            });
        }, 20L, 20L);
    }

    private void updateDynamicItem(char key) {
        ConfigurationSection cfg = dynamicTimeItems.get(key);
        if (cfg == null) return;
        ItemState state = itemStates.get(key);
        ItemStack newItem = buildItem(cfg, true, state);
        
        ItemStack cached = dynamicItemStacks.get(key);
        if (cached != null) {
            cached.setItemMeta(newItem.getItemMeta());
        }

        List<Integer> slots = dynamicTimeSlots.get(key);
        if (slots != null && !slots.isEmpty()) {
            for (UUID uuid : viewers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    org.bukkit.inventory.InventoryView view = p.getOpenInventory();
                    if (view != null && view.getTopInventory() != null) {
                        if (view.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING && view.getType() != org.bukkit.event.inventory.InventoryType.CREATIVE) {
                            for (int slot : slots) {
                                if (slot < view.getTopInventory().getSize()) {
                                    view.getTopInventory().setItem(slot, newItem);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection itemCfg, boolean applyTime, ItemState state) {
        String materialName = itemCfg.getString("material", "PAPER");
        ItemStack item;
        
        if (materialName.toLowerCase().startsWith("basehead-")) {
            String base64 = materialName.substring(9);
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(base64));
                    int urlIndex = decoded.indexOf("\"url\":\"");
                    if (urlIndex != -1) {
                        int start = urlIndex + 7;
                        int end = decoded.indexOf("\"", start);
                        String urlStr = decoded.substring(start, end);
                        
                        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                        PlayerTextures textures = profile.getTextures();
                        textures.setSkin(new URL(urlStr));
                        profile.setTextures(textures);
                        meta.setOwnerProfile(profile);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Không thể parse base64 cho " + shopId);
                }
                item.setItemMeta(meta);
            }
        } else {
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) mat = Material.PAPER;
            item = new ItemStack(mat);
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (itemCfg.contains("name")) {
                String name = itemCfg.getString("name");
                if (applyTime) name = name.replace("%time%", getFormattedTime());
                if (state != null) {
                    name = name.replace("%price%", String.valueOf(state.price));
                    if (state.hasStock) name = name.replace("%stock%", String.valueOf(state.currentStock));
                }
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            }
            if (itemCfg.contains("custom-model-data")) {
                meta.setCustomModelData(itemCfg.getInt("custom-model-data"));
            }
            if (itemCfg.contains("item_flags")) {
                for (String flagStr : itemCfg.getStringList("item_flags")) {
                    try {
                        org.bukkit.inventory.ItemFlag flag = org.bukkit.inventory.ItemFlag.valueOf(flagStr.toUpperCase());
                        meta.addItemFlags(flag);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (itemCfg.contains("lore")) {
                List<String> lore = new ArrayList<>();
                for (String l : itemCfg.getStringList("lore")) {
                    if (applyTime) l = l.replace("%time%", getFormattedTime());
                    if (state != null) {
                        l = l.replace("%price%", String.valueOf(state.price));
                        if (state.hasStock) l = l.replace("%stock%", String.valueOf(state.currentStock));
                    }
                    lore.add(ChatColor.translateAlternateColorCodes('&', l));
                }
                meta.setLore(lore);
            }

            if ((isExpired() && state != null && state.price > 0) || (state != null && state.hasStock && state.currentStock <= 0)) {
                meta.setDisplayName(meta.getDisplayName() + " §c(Khóa)");
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("");
                lore.add("§c§lĐÃ HẾT HÀNG / KHÓA");
                meta.setLore(lore);
            }

            item.setItemMeta(meta);
        }
        return item;
    }
}
