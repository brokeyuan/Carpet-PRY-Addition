package me.primaryuan.carpet.util;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SendtoLinkManager#insertIntoInventory} 的原子定量插入契约（防刷物品核心）：
 * 第一轮合并同类未满堆，第二轮放入空槽；插入多少最多拿多少。
 *
 * 26.x 起物品默认组件在 bootstrap 后仍需显式烘焙绑定（正式流程在服务端资源重载时进行），
 * 且烘焙过程会查询 damage_type / item 等"数据包标签"。纯 JVM 测试须：
 * 1. 手动执行等效的组件绑定序列（RegistryLayer 静态层 + DATA_COMPONENT_INITIALIZERS）；
 * 2. 用标签容忍的 Provider 包装查找器：任何 TagKey 查询返回空命名集，数据驱动注册表按空处理。
 * 本测试只用堆叠上限与组件相等性，不触及被容忍掉的标签语义。
 */
class InventoryTransferTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen access = RegistryLayer.createRegistryAccess().compositeAccess();
        HolderLookup.Provider provider = tagTolerantProvider(access);
        for (DataComponentInitializers.PendingComponents<?> pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)) {
            pending.apply();
        }
    }

    /** 任何标签查询返回空命名集、缺失注册表按空查找器处理，保证组件烘焙在纯 JVM 可完成 */
    private static HolderLookup.Provider tagTolerantProvider(RegistryAccess.Frozen access) {
        return new HolderLookup.Provider() {
            @Override
            public <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key) {
                HolderLookup.RegistryLookup<T> real = access.lookup(key).orElse(null);
                return real != null ? wrap(real) : emptyLookup(key);
            }

            @Override
            public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key) {
                return Optional.of(lookupOrThrow(key));
            }

            @Override
            public <T> Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
                return lookupOrThrow(key.registryKey()).getOrThrow(key);
            }

            @Override
            public Stream<ResourceKey<? extends net.minecraft.core.Registry<?>>> listRegistryKeys() {
                return access.listRegistryKeys();
            }
        };
    }

    private static <T> HolderLookup.RegistryLookup<T> wrap(HolderLookup.RegistryLookup<T> real) {
        return new HolderLookup.RegistryLookup<>() {
            @Override
            public ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key() {
                return real.key();
            }

            @Override
            public Lifecycle registryLifecycle() {
                return real.registryLifecycle();
            }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
                return real.get(key);
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
                return real.get(key).orElseGet(() -> Holder.Reference.createStandAlone(this, key));
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                return Optional.of(HolderSet.emptyNamed(this, tag));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
                return HolderSet.emptyNamed(this, tag);
            }

            @Override
            public Stream<HolderSet.Named<T>> listTags() {
                return real.listTags();
            }

            @Override
            public Stream<Holder.Reference<T>> listElements() {
                return real.listElements();
            }

            @Override
            public Stream<TagKey<T>> listTagIds() {
                return real.listTagIds();
            }
        };
    }

    private static <T> HolderLookup.RegistryLookup<T> emptyLookup(ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key) {
        return new HolderLookup.RegistryLookup<>() {
            @Override
            public ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key() {
                return key;
            }

            @Override
            public Lifecycle registryLifecycle() {
                return Lifecycle.stable();
            }

            @Override
            public Optional<Holder.Reference<T>> get(ResourceKey<T> k) {
                return Optional.empty();
            }

            @Override
            public Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
                return Holder.Reference.createStandAlone(this, key);
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                return Optional.of(HolderSet.emptyNamed(this, tag));
            }

            @Override
            public HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
                return HolderSet.emptyNamed(this, tag);
            }

            @Override
            public Stream<HolderSet.Named<T>> listTags() {
                return Stream.empty();
            }

            @Override
            public Stream<Holder.Reference<T>> listElements() {
                return Stream.empty();
            }

            @Override
            public Stream<TagKey<T>> listTagIds() {
                return Stream.empty();
            }
        };
    }

    @Test
    void mergeIntoPartiallyFilledStackThenPlaceRemainder() {
        SimpleContainer target = new SimpleContainer(36);
        target.setItem(0, new ItemStack(Items.DIAMOND, 50));
        ItemStack source = new ItemStack(Items.DIAMOND, 30);

        int moved = SendtoLinkManager.insertIntoInventory(target, source, 30);

        assertEquals(30, moved);
        assertEquals(64, target.getItem(0).getCount());   // 50 + 14 合并到满
        assertEquals(16, target.getItem(1).getCount());   // 剩余 16 进第一个空槽
    }

    @Test
    void respectsMaxCount() {
        SimpleContainer target = new SimpleContainer(36);
        ItemStack source = new ItemStack(Items.DIAMOND, 30);

        int moved = SendtoLinkManager.insertIntoInventory(target, source, 5);

        assertEquals(5, moved);
        assertEquals(5, target.getItem(0).getCount());
    }

    @Test
    void differentItemNeverMerges() {
        SimpleContainer target = new SimpleContainer(36);
        target.setItem(0, new ItemStack(Items.GOLD_INGOT, 10));
        ItemStack source = new ItemStack(Items.DIAMOND, 8);

        int moved = SendtoLinkManager.insertIntoInventory(target, source, 8);

        assertEquals(8, moved);
        assertEquals(10, target.getItem(0).getCount());   // 金锭堆不受影响
        assertEquals(8, target.getItem(1).getCount());
    }

    @Test
    void fullInventoryReceivesNothing() {
        SimpleContainer target = new SimpleContainer(36);
        for (int i = 0; i < 36; i++) {
            target.setItem(i, new ItemStack(Items.STONE, 64));
        }
        ItemStack source = new ItemStack(Items.DIAMOND, 30);

        assertEquals(0, SendtoLinkManager.insertIntoInventory(target, source, 30));
    }

    @Test
    void partialMergeOnlyWhenNothingElseFits() {
        // 除一个未满堆外全部占满：第二轮无处可放，只发生合并
        SimpleContainer target = new SimpleContainer(36);
        target.setItem(0, new ItemStack(Items.DIAMOND, 60));
        for (int i = 1; i < 36; i++) {
            target.setItem(i, new ItemStack(Items.STONE, 64));
        }
        ItemStack source = new ItemStack(Items.DIAMOND, 10);

        int moved = SendtoLinkManager.insertIntoInventory(target, source, 10);

        assertEquals(4, moved);
        assertEquals(64, target.getItem(0).getCount());
    }
}
