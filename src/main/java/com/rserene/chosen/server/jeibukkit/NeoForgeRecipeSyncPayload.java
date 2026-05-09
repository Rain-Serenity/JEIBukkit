package com.rserene.chosen.server.jeibukkit;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NeoForge 配方同步数据包
 * 
 * 用途：向 NeoForge 客户端发送服务器端配方数据
 * 
 * 数据格式说明：
 * 数据包包含所有配方类型和对应的配方列表
 * 
 * 网络协议格式：
 * [配方类型数量][配方类型 1...配方类型 N][配方数量][配方 1...配方 N]
 * 
 * @see <a href="https://github.com/mezz/JustEnoughItems">JEI Project</a>
 */
public record NeoForgeRecipeSyncPayload(
        Set<RecipeType<?>> recipeTypes,
        List<RecipeHolder<?>> recipes) implements CustomPacketPayload {

    /**
     * 数据包类型标识
     * NeoForge 客户端通过此标识识别配方同步数据包
     */
    public static final Type<NeoForgeRecipeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("neoforge", "recipe_content"));

    /**
     * 数据包编解码器
     * 使用 StreamCodec.composite 组合多个字段的编码器
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeRecipeSyncPayload> STREAM_CODEC = StreamCodec.composite(
            // 配方类型字段：注册表 ID 列表
            ByteBufCodecs.registry(Registries.RECIPE_TYPE).apply(ByteBufCodecs.collection(HashSet::new)),
            NeoForgeRecipeSyncPayload::recipeTypes,
            
            // 配方列表字段：配方流编码的列表
            RecipeHolder.STREAM_CODEC.apply(ByteBufCodecs.list()),
            NeoForgeRecipeSyncPayload::recipes,
            
            // 构造函数
            NeoForgeRecipeSyncPayload::new
    );

    /**
     * 创建配方同步数据包
     * 
     * @param recipeTypes 所有配方类型（如 crafting、smelting 等）
     * @param recipes 服务器上的配方映射
     * @return 新的数据包实例
     */
    public static NeoForgeRecipeSyncPayload create(Collection<RecipeType<?>> recipeTypes, RecipeMap recipes) {
        // 复制配方类型集合
        Set<RecipeType<?>> recipeTypeSet = Set.copyOf(recipeTypes);
        
        // 如果没有配方类型，返回空数据包
        if (recipeTypeSet.isEmpty()) {
            return new NeoForgeRecipeSyncPayload(recipeTypeSet, List.of());
        }
        
        // 过滤出属于指定类型的配方
        List<RecipeHolder<?>> recipeSubset = recipes.values().stream()
                .filter(h -> recipeTypeSet.contains(h.value().getType()))
                .toList();
        
        return new NeoForgeRecipeSyncPayload(recipeTypeSet, recipeSubset);
    }

    /**
     * 获取数据包类型
     * 
     * @return 数据包类型
     */
    @NotNull
    @Override
    public Type<NeoForgeRecipeSyncPayload> type() {
        return TYPE;
    }
}
