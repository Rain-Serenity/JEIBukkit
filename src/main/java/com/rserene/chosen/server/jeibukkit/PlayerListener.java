package com.rserene.chosen.server.jeibukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * 玩家事件监听器
 * 
 * 功能：监听玩家加入服务器的事件，自动触发配方同步
 * 
 * 性能优化：
 * - 使用 MONITOR 优先级，确保在其他插件处理完成后才执行
 * - 在主线程延迟执行，避免阻塞玩家加入流程
 * - 40 刻（约 2 秒）延迟，等待客户端完全连接后再发送数据
 */
public class PlayerListener implements Listener {
    
    // 引用主插件实例
    private final JEIBukkit plugin;
    
    // 引用配方同步管理器
    private final RecipeSyncManager recipeSyncManager;

    public PlayerListener(JEIBukkit plugin, RecipeSyncManager recipeSyncManager) {
        this.plugin = plugin;
        this.recipeSyncManager = recipeSyncManager;
    }

    /**
     * 监听玩家加入事件
     * 
     * 当玩家加入服务器时，自动向该玩家的客户端发送 JEI 配方数据
     * 使用延迟执行避免在玩家连接过程中发送大量数据导致卡顿
     * 
     * @param event 玩家加入事件
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 记录日志，方便调试和监控
        plugin.getLogger().info("玩家 " + player.getName() + " 已加入，计划同步配方数据");
        
        // 在主线程中延迟执行，等待网络稳定后再发送配方数据
        // 40 刻 = 2 秒，给客户端足够时间建立完整连接
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            recipeSyncManager.syncRecipesToPlayer(player);
        }, 40L);
    }
}
