package me.primaryuan.carpet.util;

import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.RegistryLayer;
import net.minecraft.tags.TagKey;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 纯 JVM 测试的注册表引导：bootstrap + 26.x 必需的组件烘焙绑定。
 *
 * 26.x 起物品默认组件在 bootstrap 后仍需显式烘焙绑定（正式流程在服务端资源重载时进行），
 * 且烘焙过程会查询 damage_type / item 等"数据包标签"。纯 JVM 测试须：
 * 1. 手动执行等效的组件绑定序列（RegistryLayer 静态层 + DATA_COMPONENT_INITIALIZERS）；
 * 2. 用标签容忍的 Provider 包装查找器：任何 TagKey 查询返回空命名集，数据驱动注册表按空处理。
 * （原实现见 InventoryTransferTest，此处抽出供多个测试类复用）
 */
public final class TestRegistries {

    private static boolean done;

    private TestRegistries() {}

    public static synchronized void bootstrap() {
        if (done) {
            return;
        }
        done = true;
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
            public <T> HolderLookup.RegistryLookup<T> lookupOrThrow(ResourceKey<? extends Registry<? extends T>> key) {
                HolderLookup.RegistryLookup<T> real = access.lookup(key).orElse(null);
                return real != null ? wrap(real) : emptyLookup(key);
            }

            @Override
            public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> key) {
                return Optional.of(lookupOrThrow(key));
            }

            @Override
            public <T> Holder.Reference<T> getOrThrow(ResourceKey<T> key) {
                return lookupOrThrow(key.registryKey()).getOrThrow(key);
            }

            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return access.listRegistryKeys();
            }
        };
    }

    @SuppressWarnings("deprecation") // 原版自用（RegistrySetBuilder$EmptyTagLookup）且无替代工厂，仅测试引导使用
    private static <T> HolderLookup.RegistryLookup<T> wrap(HolderLookup.RegistryLookup<T> real) {
        return new HolderLookup.RegistryLookup<>() {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
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

    @SuppressWarnings("deprecation") // 同上：emptyNamed 无替代，原版 EmptyTagLookup 亦自用
    private static <T> HolderLookup.RegistryLookup<T> emptyLookup(ResourceKey<? extends Registry<? extends T>> key) {
        return new HolderLookup.RegistryLookup<>() {
            @Override
            public ResourceKey<? extends Registry<? extends T>> key() {
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
}
