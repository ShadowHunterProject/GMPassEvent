package com.gmpass.event;

import com.gmpass.event.gui.ShopManager;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GMPassEvent extends JavaPlugin {

    private EventManager eventManager;
    private ShopManager shopManager;
    private MessageManager messageManager;
    private NamespacedKey eventKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        eventKey = new NamespacedKey(this, "gme_id");
        eventManager = new EventManager(this);

        // Đăng ký sự kiện
        getServer().getPluginManager().registerEvents(new ClickListener(this), this);

        // Load sự kiện, messages và GUI Shop
        messageManager = new MessageManager(this);
        eventManager.loadEvents();
        shopManager = new ShopManager(this);

        getLogger().info("GMPassEvent đã được kích hoạt thành công!");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.despawnAll();
        }
        getLogger().info("GMPassEvent đã vô hiệu hóa.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("goiqua")) {
            if (sender instanceof Player) {
                shopManager.openShop((Player) sender, "shop");
            } else {
                sender.sendMessage(messageManager.getWithPrefix("not-player"));
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("gmpassevent")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("gmpass.admin")) {
                    sender.sendMessage(messageManager.getWithPrefix("no-permission"));
                    return true;
                }
                reloadConfig();
                messageManager.loadMessages();
                eventManager.loadEvents();
                shopManager.loadConfig();
                sender.sendMessage(messageManager.getWithPrefix("reload-success"));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("shop")) {
                if (sender instanceof Player) {
                    String shopId = args.length > 1 ? args[1] : "shop";
                    shopManager.openShop((Player) sender, shopId);
                } else {
                    sender.sendMessage(messageManager.getWithPrefix("not-player"));
                }
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("open")) {
                if (!sender.hasPermission("gmpass.admin")) {
                    sender.sendMessage(messageManager.getWithPrefix("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§eDùng: /gme open <người_chơi> [tên_file]");
                    return true;
                }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cNgười chơi không online!");
                    return true;
                }
                String shopId = args.length > 2 ? args[2] : "shop";
                shopManager.openShop(target, shopId);
                return true;
            }
            sender.sendMessage("§eDùng: /gme reload | /gme shop <tên_file> | /gme open <người_chơi> <tên_file>");
            return true;
        }
        return false;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public NamespacedKey getEventKey() {
        return eventKey;
    }
}
