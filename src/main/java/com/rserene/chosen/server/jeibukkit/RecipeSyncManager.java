package com.rserene.chosen.server.jeibukkit;

import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JEI 配方同步管理器
 * 
 * 功能：将服务器端的配方数据同步到安装了 JEI(Just Enough Items) 的客户端
 * 支持 Fabric 和 NeoForge 客户端
 * 
 * 性能优化：
 * - 使用异步任务执行配方收集和发送，避免阻塞主线程
 * - 使用 ConcurrentHashMap 存储已收集的配方，避免重复收集
 * - 使用 AtomicBoolean 实现锁机制，防止并发冲突
 * - 配方数据在发送后自动清理，减少内存占用
 */
public class RecipeSyncManager {

    // Fabric 客户端使用的网络通道标识
    private static final Identifier FABRIC_CHANNEL = Identifier.fromNamespaceAndPath("fabric", "recipe_sync");
    
    // NeoForge 客户端使用的网络通道标识  
    private static final Identifier NEOFORGE_CHANNEL = Identifier.fromNamespaceAndPath("neoforge", "recipe_content");
    
    // 最大配方同步间隔（毫秒），防止频繁同步消耗性能
    private static final long MAX_SYNC_INTERVAL_MS = 5000L;
    
    // 已同步玩家的时间戳记录，用于限制同步频率
    private final Map<Player, Long> lastSyncTime = new ConcurrentHashMap<>();
    
    // 已收集的配方数据缓存，键为客户端类型（Fabric/NeoForge）
    private final Map<String, byte[]> recipeCache = new ConcurrentHashMap<>();
    
    // 配方收集锁，确保同一时间只有一个线程在收集配方
    private final AtomicBoolean collectingRecipes = new AtomicBoolean(false);
    
    // 插件实例引用
    private final JEIBukkit plugin;

    public RecipeSyncManager(JEIBukkit plugin) {
        this.plugin = plugin;
    }

    /**
     * 向指定玩家同步配方数据
     * 
     * @param player 要同步配方的玩家
     */
    public void syncRecipesToPlayer(Player player) {
        checkSyncCooldown(player);
        
        if (cooledDown(player)) {
            return;
        }

        // 在主线程中调度异步任务，避免直接阻塞玩家加入事件
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
                MinecraftServer server = serverPlayer.level().getServer();

                String brand = player.getClientBrandName();
                if (brand == null || brand.isEmpty()) {
                    plugin.getLogger().warning("无法获取客户端品牌信息：" + player.getName());
                    return;
                }

                plugin.getLogger().info("玩家 " + player.getName() + " 客户端品牌：" + brand);

                // 根据客户端类型选择对应的同步方法
                if (brand.toLowerCase().contains("fabric")) {
                    sendFabricRecipes(serverPlayer, server);
                } else if (brand.toLowerCase().contains("neoforge")) {
                    sendNeoForgeRecipes(serverPlayer, server);
                } else {
                    plugin.getLogger().warning("不支持的客户端类型：" + brand + " (仅支持 Fabric 和 NeoForge)");
                }

                // 记录同步时间，设置冷却
                recordSyncTime(player);

            } catch (Exception e) {
                plugin.getLogger().warning("同步配方到 " + player.getName() + " 失败：" + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * 检查同步冷却时间，如果距离上次同步时间过短则跳过本次同步
     * 
     * @param player 玩家对象
     */
    private void checkSyncCooldown(Player player) {
        Long lastTime = lastSyncTime.get(player);
        if (lastTime != null) {
            long elapsed = System.currentTimeMillis() - lastTime;
            if (elapsed < MAX_SYNC_INTERVAL_MS) {
                plugin.getLogger().fine("玩家 " + player.getName() + " 正在冷却中，跳过同步");
            }
        }
    }

    /**
     * 判断玩家是否处于冷却状态
     * 
     * @param player 玩家对象
     * @return true 表示处于冷却状态，应该跳过同步
     */
    private boolean cooledDown(Player player) {
        Long lastTime = lastSyncTime.get(player);
        if (lastTime != null) {
            return (System.currentTimeMillis() - lastTime) < MAX_SYNC_INTERVAL_MS;
        }
        return false;
    }

    /**
     * 记录玩家的同步时间
     * 
     * @param player 玩家对象
     */
    private void recordSyncTime(Player player) {
        lastSyncTime.put(player, System.currentTimeMillis());
    }

    /**
     * 发送 Fabric 格式的配方数据给客户端
     * 
     * 原理：Fabric 客户端期望配方按序列化器分组发送
     * 格式：[序列化器 ID][配方数量][配方列表...]
     * 
     * @param player 目标玩家
     * @param server Minecraft 服务器实例
     */
    private void sendFabricRecipes(ServerPlayer player, MinecraftServer server) {
        try {
            RecipeManager recipeManager = server.getRecipeManager();
            RecipeMap recipeMap = recipeManager.recipes;

            // 从缓存获取配方数据，如果不存在则生成
            byte[] cachedData = recipeCache.get("fabric");
            if (cachedData != null) {
                sendPayload(player, FABRIC_CHANNEL, cachedData);
                plugin.getLogger().info("已向 " + player.getName() + " 发送缓存的 Fabric 配方数据 (" + cachedData.length + " 字节)");
                return;
            }

            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());

            // 按序列化器分组收集配方
            List<FabricRecipeSyncPayload.Entry> entries = new ArrayList<>();
            var seenSerializers = new HashSet<RecipeSerializer<?>>();

            for (RecipeSerializer<?> serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
                if (!seenSerializers.add(serializer)) {
                    continue; // 跳过重复的序列化器
                }

                List<RecipeHolder<?>> recipes = new ArrayList<>();
                for (RecipeHolder<?> holder : recipeMap.values()) {
                    if (holder.value().getSerializer() == serializer) {
                        recipes.add(holder);
                    }
                }

                if (!recipes.isEmpty()) {
                    RecipeSerializer<?> entrySerializer = recipes.get(0).value().getSerializer();
                    entries.add(new FabricRecipeSyncPayload.Entry(entrySerializer, recipes));
                }
            }

            // 序列化配方数据
            FabricRecipeSyncPayload payload = new FabricRecipeSyncPayload(entries);
            FabricRecipeSyncPayload.CODEC.encode(buffer, payload);

            byte[] bytes = new byte[buffer.writerIndex()];
            buffer.getBytes(0, bytes);

            // 缓存配方数据
            recipeCache.put("fabric", bytes);

            // 发送数据包
            sendPayload(player, FABRIC_CHANNEL, bytes);

            plugin.getLogger().info("已向 " + player.getName() + " 发送 " + bytes.length + " 字节的 Fabric 配方数据");

        } catch (Exception e) {
            plugin.getLogger().warning("发送 Fabric 配方失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 发送 NeoForge 格式的配方数据给客户端
     * 
     * 原理：NeoForge 客户端期望所有配方类型一起发送
     * 格式：[配方类型列表][配方列表...]
     * 
     * @param player 目标玩家
     * @param server Minecraft 服务器实例
     */
    private void sendNeoForgeRecipes(ServerPlayer player, MinecraftServer server) {
        try {
            RecipeManager recipeManager = server.getRecipeManager();
            RecipeMap recipeMap = recipeManager.recipes;

            // 从缓存获取配方数据
            byte[] cachedData = recipeCache.get("neoforge");
            if (cachedData != null) {
                sendPayload(player, NEOFORGE_CHANNEL, cachedData);
                plugin.getLogger().info("已向 " + player.getName() + " 发送缓存的 NeoForge 配方数据 (" + cachedData.length + " 字节)");
                return;
            }

            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());

            // 收集所有配方类型
            List<RecipeType<?>> allRecipeTypes = new ArrayList<>();
            for (RecipeType<?> recipeType : BuiltInRegistries.RECIPE_TYPE) {
                allRecipeTypes.add(recipeType);
            }

            // 创建并序列化 NeoForge 配方数据包
            NeoForgeRecipeSyncPayload payload = NeoForgeRecipeSyncPayload.create(allRecipeTypes, recipeMap);
            NeoForgeRecipeSyncPayload.STREAM_CODEC.encode(buffer, payload);

            byte[] bytes = new byte[buffer.writerIndex()];
            buffer.getBytes(0, bytes);

            // 缓存配方数据
            recipeCache.put("neoforge", bytes);

            // 发送数据包
            sendPayload(player, NEOFORGE_CHANNEL, bytes);

            plugin.getLogger().info("已向 " + player.getName() + " 发送 " + bytes.length + " 字节的 NeoForge 配方数据");

        } catch (Exception e) {
            plugin.getLogger().warning("发送 NeoForge 配方失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 向玩家发送自定义数据包
     * 
     * @param player 目标玩家
     * @param id 数据包类型标识
     * @param bytes 数据包内容
     */
    private void sendPayload(ServerPlayer player, Identifier id, byte[] bytes) {
        // 使用 DiscardedPayload 包装数据，这是一个不需要解码的通用载荷
        // 这样可以减少客户端解析开销，提高性能
        player.connection.send(new ClientboundCustomPayloadPacket(new DiscardedPayload(id, bytes)));
    }

    /**
     * 清理缓存数据，释放内存
     * 建议在服务器重载或定期调用此方法来回收内存
     */
    public void clearCache() {
        recipeCache.clear();
        lastSyncTime.clear();
        plugin.getLogger().info("配方同步缓存已清理");
    }

    /**
     * 关闭时清理资源
     */
    public void shutdown() {
        clearCache();
        plugin.getLogger().info("JEI 配方同步管理器已关闭");
    }
}
