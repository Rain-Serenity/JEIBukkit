package com.rserene.chosen.server.jeibukkit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fabric 配方同步数据包
 * 
 * 用途：向 Fabric 客户端发送服务器端配方数据
 * 
 * 数据格式说明：
 * 数据包包含多个 Entry，每个 Entry 代表一种序列化器及其对应的配方列表
 * 
 * 网络协议格式：
 * [Entry 数量]
 *   [序列化器 ID][配方数量][配方 1...配方 N]
 *   [序列化器 ID][配方数量][配方 1...配方 N]
 *   ...
 * 
 * @see <a href="https://github.com/mezz/JustEnoughItems">JEI Project</a>
 */
public record FabricRecipeSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    /**
     * 数据包编解码器
     * 用于将数据包序列化为字节流和反序列化
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricRecipeSyncPayload> CODEC =
            Entry.CODEC.apply(ByteBufCodecs.list())
                    .map(FabricRecipeSyncPayload::new, FabricRecipeSyncPayload::entries);

    /**
     * 数据包类型标识
     * Fabric 客户端通过此标识识别配方同步数据包
     */
    public static final Type<FabricRecipeSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("fabric", "recipe_sync"));

    /**
     * 单个序列化器的配方条目
     * 
     * @param serializer 配方序列化器（如工作台、熔炉等）
     * @param recipes 该序列化器对应的所有配方
     */
    public record Entry(RecipeSerializer<?> serializer, List<RecipeHolder<?>> recipes) {

        /**
         * 条目的编解码器
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.ofMember(
                Entry::write,
                Entry::read
        );

        /**
         * 从字节流读取条目数据
         * 
         * @param buf 输入缓冲区
         * @return 解析后的条目
         */
        private static Entry read(RegistryFriendlyByteBuf buf) {
            // 读取序列化器 ID
            Identifier recipeSerializerId = buf.readIdentifier();
            RecipeSerializer<?> recipeSerializer = BuiltInRegistries.RECIPE_SERIALIZER.getValue(recipeSerializerId);

            if (recipeSerializer == null) {
                throw new RuntimeException("不支持的数据包序列化器：" + recipeSerializerId);
            }

            // 读取配方数量并解析每个配方
            int count = buf.readVarInt();
            List<RecipeHolder<?>> list = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                ResourceKey<Recipe<?>> id = buf.readResourceKey(Registries.RECIPE);
                Recipe<?> recipe = recipeSerializer.streamCodec().decode(buf);
                list.add(new RecipeHolder<>(id, recipe));
            }

            return new Entry(recipeSerializer, list);
        }

        /**
         * 将条目写入字节流
         * 
         * @param buf 输出缓冲区
         */
        private void write(RegistryFriendlyByteBuf buf) {
            // 写入序列化器 ID
            buf.writeIdentifier(Objects.requireNonNull(BuiltInRegistries.RECIPE_SERIALIZER.getKey(this.serializer)));
            
            // 写入配方数量
            buf.writeVarInt(this.recipes.size());

            // 写入每个配方
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> serializer =
                    (StreamCodec<RegistryFriendlyByteBuf, Recipe<?>>) this.serializer.streamCodec();

            for (RecipeHolder<?> recipe : this.recipes) {
                buf.writeResourceKey(recipe.id());
                serializer.encode(buf, recipe.value());
            }
        }
    }

    /**
     * 获取数据包类型
     * 
     * @return 数据包类型
     */
    @NotNull
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
