package com.rserene.chosen.server.jeibukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /jeisync 命令处理器
 * 
 * 功能：手动触发 JEI 配方同步
 * 权限要求：
 * - 玩家自己同步：jeibukkit.sync (默认允许所有玩家)
 * - 为其他玩家同步：jeibukkit.sync.others (默认仅 OP)
 */
public class JEISyncCommand implements CommandExecutor, TabCompleter {
    
    // 引用主插件实例
    private final JEIBukkit plugin;
    
    // 引用配方同步管理器
    private final RecipeSyncManager recipeSyncManager;

    public JEISyncCommand(JEIBukkit plugin, RecipeSyncManager recipeSyncManager) {
        this.plugin = plugin;
        this.recipeSyncManager = recipeSyncManager;
    }

    /**
     * 处理 /jeisync 命令
     * 
     * @param sender 命令执行者
     * @param command 命令对象
     * @param label 命令标签
     * @param args 命令参数
     * @return true 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        
        // 情况 1: 没有参数，为自己同步
        if (args.length == 0) {
            // 检查是否为玩家执行
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "此命令只能由玩家使用，或者指定一个玩家参数。");
                return true;
            }
            
            // 检查权限
            if (!sender.hasPermission("jeibukkit.sync")) {
                sender.sendMessage(ChatColor.RED + "你没有使用此命令的权限。");
                return true;
            }
            
            // 执行同步
            Player player = (Player) sender;
            recipeSyncManager.syncRecipesToPlayer(player);
            sender.sendMessage(ChatColor.GREEN + "JEI 配方已同步到你的客户端。");
            return true;
        }
        
        // 情况 2: 有参数，为他人同步
        if (!sender.hasPermission("jeibukkit.sync.others")) {
            sender.sendMessage(ChatColor.RED + "你没有为其他玩家同步配方的权限。");
            return true;
        }
        
        // 获取目标玩家名称
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        // 检查玩家是否存在
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家 '" + targetName + "'。");
            return true;
        }
        
        // 执行同步
        recipeSyncManager.syncRecipesToPlayer(target);
        sender.sendMessage(ChatColor.GREEN + "JEI 配方已同步到 " + target.getName() + " 的客户端。");
        
        // 通知目标玩家（如果不是自己）
        if (sender != target) {
            target.sendMessage(ChatColor.GREEN + "JEI 配方已由 " + sender.getName() + " 同步到你的客户端。");
        }
        
        return true;
    }

    /**
     * 处理命令自动补全
     * 
     * 当玩家输入 /jeisync [Tab] 时，显示在线玩家列表
     * 
     * @param sender 命令执行者
     * @param command 命令对象
     * @param alias 命令别名
     * @param args 当前已输入的参数
     * @return 补全建议列表
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // 只有拥有权限的玩家才能查看其他玩家列表
        if (args.length == 1 && sender.hasPermission("jeibukkit.sync.others")) {
            String partial = args[0].toLowerCase();
            
            // 遍历所有在线玩家，查找匹配的用户名
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }
}
