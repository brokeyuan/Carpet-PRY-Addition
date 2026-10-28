package me.primaryuan.carpet.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 数据文件统一落盘基类：版本信封 + 旧格式收编 + 两级作用域 + 原子写。
 *
 * <p>文件格式为 {@code {"version": N, "data": {...}}} 信封。version 高于当前版本
 * 按只读处理（内存用默认值且绝不回写，降级运行旧版模组不破坏新格式文件）；
 * 低于当前版本沿 {@link #migrate} 逐级升版并立即回写；旧平铺文件（无信封、
 * 位于 config/ 根目录的 v0 文件）经 {@link #fromLegacy} 收编到新路径后改名 .bak。</p>
 *
 * <p>作用域：GLOBAL → {@code config/carpet-pry-addition/<file>}（跟随服务器）；
 * WORLD → {@code <存档>/carpet-pry-addition/<file>}（跟随存档）。WORLD 数据随
 * {@link #attach}/{@link #detach} 走服务器生命周期，单人多次进出世界各自加载、
 * 互不残留。文件懒创建：attach 不落盘，首次 save 才建目录。</p>
 */
public abstract class PryJsonStore {
    protected static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 两种作用域共用的目录名 */
    public static final String FOLDER = "carpet-pry-addition";

    public enum Scope { GLOBAL, WORLD }

    private final String tag;
    private final String fileName;
    private final Scope scope;
    private final int version;
    private final String legacyFileName;
    private MinecraftServer server;
    private boolean readOnly;

    /**
     * @param tag            日志前缀（如 "TPP"）
     * @param fileName       文件名（如 "tpp.json"）
     * @param scope          作用域（GLOBAL 跟随服务器 / WORLD 跟随存档）
     * @param version        当前 data 的 schema 版本
     * @param legacyFileName 旧平铺文件名（config/ 根目录），null = 无旧格式可收编；
     *                       只存名字、attach 时才解析 configDir（单测环境无 fabric loader）
     */
    protected PryJsonStore(String tag, String fileName, Scope scope, int version, String legacyFileName) {
        this.tag = tag;
        this.fileName = fileName;
        this.scope = scope;
        this.version = version;
        this.legacyFileName = legacyFileName;
    }

    /** 旧平铺文件路径（config/ 根目录）；无旧格式返回 null */
    private Path legacyPath() {
        return legacyFileName == null ? null
                : FabricLoader.getInstance().getConfigDir().resolve(legacyFileName);
    }

    /** 当前文件路径；WORLD 作用域未 attach 时返回 null */
    private Path resolvePath() {
        return switch (scope) {
            case GLOBAL -> FabricLoader.getInstance().getConfigDir().resolve(FOLDER).resolve(fileName);
            case WORLD -> server == null ? null
                    : server.getWorldPath(LevelResource.ROOT).resolve(FOLDER).resolve(fileName);
        };
    }

    /** 数据文件路径；单测无 fabric loader，可覆盖注入固定路径 */
    protected Path filePath() {
        return resolvePath();
    }

    /**
     * 加载数据：无文件时内存清为默认态（绝不落盘建空文件）；有旧平铺文件时收编。
     * WORLD 作用域必须在服务器生命周期内调用（此时 getWorldPath 可用）。
     */
    public final synchronized void attach(MinecraftServer server) {
        this.server = server;
        this.readOnly = false;
        Path file = resolvePath();
        Path legacy = legacyPath();
        if (Files.notExists(file)) {
            if (legacy != null && Files.exists(legacy)) {
                migrateLegacyFile(legacy);
            } else {
                clearData();
            }
            return;
        }
        loadRoot(readFileOrNull(file));
    }

    /** 服务器关闭：清空内存态。单人切换世界时上一世界数据不残留 */
    public final synchronized void detach() {
        clearData();
        server = null;
        readOnly = false;
    }

    /** 持久化当前内存态（信封 + 原子写）；只读（版本超前）时拒绝写盘 */
    public final synchronized void save() {
        Path file = resolvePath();
        if (file == null) {
            LOGGER.error("[{}] Save skipped: not attached to a server", tag);
            return;
        }
        if (readOnly) {
            LOGGER.error("[{}] Save skipped: file schema is newer than mod version", tag);
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        root.add("data", collectData());
        writeFile(file, root);
    }

    protected final MinecraftServer server() {
        return server;
    }

    private void loadRoot(JsonObject root) {
        if (root == null) {
            // 读取失败：内存用默认态，磁盘原样保留
            clearData();
            return;
        }
        try {
            int fileVersion;
            JsonObject data;
            if (root.has("version") && root.get("version").isJsonPrimitive()) {
                fileVersion = root.get("version").getAsInt();
                if (fileVersion > version) {
                    LOGGER.error("[{}] Config version {} is newer than supported {} (downgraded mod?), "
                            + "using defaults; file untouched", tag, fileVersion, version);
                    readOnly = true;
                    clearData();
                    return;
                }
                data = root.getAsJsonObject("data");
                if (data == null) {
                    LOGGER.error("[{}] Config missing 'data' object, using defaults; "
                            + "file will be rewritten on next save", tag);
                    clearData();
                    return;
                }
            } else {
                // 新路径下出现无信封文件：按 v0 旧格式收编（自我修复丢失信封的手改文件）。
                // 不认识的键会被丢弃，下次 save 以信封格式重写整个文件
                fileVersion = 0;
                LOGGER.warn("[{}] Config has no version envelope, importing as legacy v0", tag);
                data = fromLegacy(root);
                if (data == null) {
                    LOGGER.error("[{}] Unversioned config not recognized, using defaults; "
                            + "file will be rewritten on next save", tag);
                    clearData();
                    return;
                }
            }
            while (fileVersion < version) {
                data = migrate(data, fileVersion);
                fileVersion++;
            }
            applyData(data);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to load config (memory state reset to defaults)", tag, e);
            clearData();
        }
    }

    /** 收编旧平铺文件：解析 → 迁移 → 写入新路径 → 旧文件改名 .bak（避免重复收编） */
    private void migrateLegacyFile(Path legacyFile) {
        JsonObject legacy = readFileOrNull(legacyFile);
        if (legacy == null) {
            LOGGER.error("[{}] Legacy config {} is unreadable, using defaults; file untouched", tag, legacyFile);
            clearData();
            return;
        }
        try {
            JsonObject data = fromLegacy(legacy);
            if (data == null) {
                LOGGER.error("[{}] Legacy config not recognized, using defaults; file untouched", tag);
                clearData();
                return;
            }
            int v = 0;
            while (v < version) {
                data = migrate(data, v);
                v++;
            }
            applyData(data);
            Path file = resolvePath();
            if (file == null) {
                return;
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", version);
            root.add("data", data);
            writeFile(file, root);
            try {
                Files.move(legacyFile, legacyFile.resolveSibling(legacyFile.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // 走到这里说明新文件已写入成功（改名在写入之后），下次启动加载新文件、
                // 不会再收编；旧文件滞留原地，仅提示手动清理
                LOGGER.warn("[{}] Legacy config migrated but rename failed; legacy file kept at {}, "
                        + "safe to delete manually", tag, legacyFile, e);
            }
            LOGGER.info("[{}] Migrated legacy config {} -> {}", tag, legacyFile, file);
        } catch (Exception e) {
            LOGGER.error("[{}] Legacy config migration failed, using defaults; legacy file untouched", tag, e);
            clearData();
        }
    }

    /** 读取文件为 JSON 根；任何失败（含 JSON 语法错误）返回 null 并记日志 */
    protected final JsonObject readFileOrNull(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("[{}] Failed to read {}", tag, file, e);
            return null;
        }
    }

    /** 原子写：先写临时文件再原子替换，避免写一半崩溃/断电留下损坏的 JSON（统一 UTF-8） */
    private void writeFile(Path file, JsonObject root) {
        if (!writeFileAtomic(file, root)) {
            LOGGER.error("[{}] Failed to save {}", tag, file);
        }
    }

    /**
     * 原子写并返回结果：先写临时文件再原子替换（统一 UTF-8）。
     * 供需要明确成功/失败的子类（如 TptoStore 的候选写入）使用；失败不抛出、只返回 false。
     */
    protected boolean writeFileAtomic(Path file, JsonObject root) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            LOGGER.error("[{}] Failed to save {}", tag, file, e);
            return false;
        }
    }

    /**
     * 备份当前文件到同目录唯一 .bak（文件名形如 {@code <file>.<label>-<时间戳>-<序号>.bak}）。
     * 不覆盖已存在的备份；文件不存在视为已备份（返回 true）。失败返回 false。
     */
    protected boolean backupOriginalFile(String label) {
        Path file = filePath();
        if (file == null || Files.notExists(file)) {
            return true;
        }
        String stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(java.time.LocalDateTime.now());
        for (int n = 0; ; n++) {
            Path target = file.resolveSibling(
                    file.getFileName() + "." + label + "-" + stamp + "-" + n + ".bak");
            try {
                Files.copy(file, target);
                LOGGER.info("[{}] Backed up {} -> {}", tag, file, target);
                return true;
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // 时间戳撞车：递增序号重试，保证不覆盖旧备份
            } catch (IOException e) {
                LOGGER.error("[{}] Failed to back up {} -> {}", tag, file, target, e);
                return false;
            }
        }
    }

    // ===== 子类钩子 =====

    /** 将当前版本的 data 应用到内存态；实现须解析到临时结构、全部成功后再原子提交 */
    protected abstract void applyData(JsonObject data);

    /** 收集内存态为当前版本的 data */
    protected abstract JsonObject collectData();

    /** v → v+1 逐级迁移一步；默认原样返回（v1 无历史迁移步骤） */
    protected JsonObject migrate(JsonObject data, int fromVersion) {
        return data;
    }

    /** v0 旧平铺格式（无信封）解析为 v1 起点的 data；不认识该格式返回 null */
    protected JsonObject fromLegacy(JsonObject legacyRoot) {
        return null;
    }

    /** 内存态清空为默认值（未 attach、读取失败、服务器关闭时调用） */
    protected abstract void clearData();
}
