package com.gmpass.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ClickListener implements Listener {
    private final GMPassEvent plugin;

    public ClickListener(GMPassEvent plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.OFF_HAND) return;
        
        // Chỉ xử lý nếu click vào Interaction entity của chúng ta
        PersistentDataContainer pdc = event.getRightClicked().getPersistentDataContainer();
        
        if (pdc.has(plugin.getEventKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
            
            String eventId = pdc.get(plugin.getEventKey(), PersistentDataType.STRING);
            EventNode node = plugin.getEventManager().getEvent(eventId);
            
            if (node != null) {
                Player p = event.getPlayer();
                
                // Thực thi các lệnh đã cấu hình
                for (String cmd : node.getCommands()) {
                    String parsedCmd = cmd.replace("%player%", p.getName());
                    
                    if (parsedCmd.toLowerCase().startsWith("[player] ")) {
                        p.performCommand(parsedCmd.substring(9));
                    } else if (parsedCmd.toLowerCase().startsWith("[console] ")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd.substring(10));
                    } else if (parsedCmd.toLowerCase().startsWith("msg ")) {
                        // Lệnh tuỳ chỉnh gửi tin nhắn
                        String msg = parsedCmd.substring(4).replace("&", "§");
                        p.sendMessage(msg);
                    } else if (parsedCmd.toLowerCase().startsWith("broadcast ")) {
                        String msg = parsedCmd.substring(10).replace("&", "§");
                        Bukkit.broadcastMessage(msg);
                    } else {
                        // Mặc định chạy từ console nếu không có tiền tố
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                    }
                }
                
                // Đã xóa lệnh despawn() ở đây để cái đầu tồn tại vĩnh viễn
            }
        }
    }
}
