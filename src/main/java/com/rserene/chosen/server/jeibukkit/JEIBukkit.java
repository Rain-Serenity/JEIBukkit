package com.rserene.chosen.server.jeibukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * JEI 配方同步插件主类
 * 
 * 功能说明：
 * 本插件用于在 Paper/PaperMC 服务器上向安装了 JEI(Just Enough Items) 的客户端
 * 发送服务器端的配方数据。从 Minecraft 1.21.2 开始，配方存储在服务器端而非客户端，
 * 导致 JEI 无法显示配方。本插件通过模拟 Fabric/NeoForge 的网络协议来解决这个问题。
 * 
 * 支持的功能：
 * - 自动同步：玩家加入服务器时自动发送配方
 * - 手动同步：使用 /jeisync 命令手动触发同步
 * - 性能优化：异步处理、缓存机制、频率限制
 * 
 * @author Rserene
 */
public final class JEIBukkit extends JavaPlugin {
    
    // 配方同步管理器实例
    private RecipeSyncManager recipeSyncManager;

    /**
     * 插件启用时的回调方法
     * 
     * 执行以下操作：
     * 1. 初始化配方同步管理器
     * 2. 注册玩家事件监听器
     * 3. 注册命令处理器
     * 4. 向在线玩家发送配方数据
     */
    @Override
    public void onEnable() {
        try {
            // 创建配方同步管理器实例
            recipeSyncManager = new RecipeSyncManager(this);
            
            // 注册玩家加入事件监听器
            Bukkit.getPluginManager().registerEvents(new PlayerListener(this, recipeSyncManager), this);
            
            // 注册 /jeisync 命令
            JEISyncCommand syncCommand = new JEISyncCommand(this, recipeSyncManager);
            if (getCommand("jeisync") != null) {
                getCommand("jeisync").setExecutor(syncCommand);
                getCommand("jeisync").setTabCompleter(syncCommand);
            }
            
            // 插件完全启动后，向所有在线玩家发送配方数据
            // 延迟 20 刻（1 秒），确保插件完全初始化
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    recipeSyncManager.syncRecipesToPlayer(player);
                }
            }, 20L);
            
            // 记录插件启动日志
            getLogger().info("JEIBukkit 已启用！配方将自动同步到 JEI 客户端。");
            getLogger().info("使用方法：/jeisync [玩家名]");
            
        } catch (Exception e) {
            // 捕获并记录初始化错误
            getLogger().log(Level.SEVERE, "初始化 JEIBukkit 失败", e);
            setEnabled(false); // 禁用插件
        }
    }

    /**
     * 插件禁用时的回调方法
     * 
     * 执行清理工作：
     * 1. 关闭配方同步管理器
     * 2. 释放资源
     */
    @Override
    public void onDisable() {
        // 清理资源
        if (recipeSyncManager != null) {
            recipeSyncManager.shutdown();
        }
        getLogger().info("JEIBukkit 已禁用！");
    }
}
