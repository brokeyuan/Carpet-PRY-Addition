package me.primaryuan.carpet.replay.storage;

import me.primaryuan.carpet.replay.common.ReplayAccess;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayFormatException;
import me.primaryuan.carpet.replay.common.ReplayLimits;
import me.primaryuan.carpet.replay.common.ReplayMetadata;
import me.primaryuan.carpet.replay.common.ReplayNames;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 录像文件存储：{@code <存档>/carpet-pry-addition/replay/recordings/<recordingId>.repry}。
 * 文件名使用稳定录像 ID，名称只存在于元数据，因此同名并发不会覆盖另一份录像；
 * 写入走有界 IO 队列 + 原子替换；读取与写入资源预算一致。
 * 自动轮换只保护创作者自己的收藏，不把任意访问者的收藏变成永久占位授权。
 */
public final class ReplayStorage
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final String ROOT_FOLDER = "carpet-pry-addition";
    private static final String REPLAY_FOLDER = "replay";
    private static final String RECORDINGS = "recordings";
    private static final String CHECKPOINTS = "checkpoints";
    private static final String FAVORITES = "favorites";
    private static final String SUFFIX = ".repry";

    private static final AtomicInteger PENDING_SAVES = new AtomicInteger();
    private static final AtomicLong PENDING_BYTES = new AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<String, AtomicInteger> PENDING_BY_OWNER =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> CHECKPOINT_WRITING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(ReplayLimits.MAX_PENDING_SAVES),
            r ->
            {
                Thread t = new Thread(r, "pry-replay-io");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private ReplayStorage() {}

    public static String ROOT_FOLDER()
    {
        return ROOT_FOLDER;
    }

    public static String REPLAY_FOLDER()
    {
        return REPLAY_FOLDER;
    }

    public static String FAVORITES_FOLDER()
    {
        return FAVORITES;
    }

    /** 录像元信息（列表/定位/悬停用） */
    public record ReplayEntry(String name, String recordingId, long createdAt, int durationTicks,
                              String ownerUuid, String ownerName, ReplayAccess access,
                              String initialDimension, double initialX, double initialY, double initialZ,
                              boolean recovered)
    {
    }

    public static Path file(MinecraftServer server, String recordingId)
    {
        return dir(server, RECORDINGS).resolve(recordingId + SUFFIX);
    }

    public static Path dir(MinecraftServer server, String child)
    {
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(ROOT_FOLDER)
                .resolve(REPLAY_FOLDER)
                .resolve(child);
    }

    // ==================== 列表 ====================

    public static List<ReplayEntry> listAll(MinecraftServer server)
    {
        List<ReplayEntry> out = new ArrayList<>();
        Path d = dir(server, RECORDINGS);
        if (!Files.isDirectory(d))
        {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d, "*" + SUFFIX))
        {
            for (Path p : stream)
            {
                ReplayEntry e = readEntry(p);
                if (e != null)
                {
                    out.add(e);
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 列出录像失败: {}", d, e);
        }
        out.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return out;
    }

    public static List<ReplayEntry> listOwned(MinecraftServer server, String ownerUuid)
    {
        List<ReplayEntry> out = new ArrayList<>();
        for (ReplayEntry e : listAll(server))
        {
            if (e.ownerUuid().equals(ownerUuid))
            {
                out.add(e);
            }
        }
        return out;
    }

    public static ReplayEntry entry(MinecraftServer server, String recordingId)
    {
        Path p = file(server, recordingId);
        return Files.isRegularFile(p) ? readEntry(p) : null;
    }

    /** 只读文件头（gzip 流式），不整段解码 */
    private static ReplayEntry readEntry(Path file)
    {
        try (var gis = new java.util.zip.GZIPInputStream(Files.newInputStream(file), 1 << 14);
             var in = new DataInputStream(gis))
        {
            int fmt = in.readInt();
            if (fmt != ReplayMetadata.FORMAT_VERSION)
            {
                return null;
            }
            String id = in.readUTF();
            String ownerUuid = in.readUTF();
            String ownerName = in.readUTF();
            String name = in.readUTF();
            long createdAt = in.readLong();
            int duration = in.readInt();
            ReplayAccess access = ReplayAccess.byOrdinal(in.readByte());
            String dim = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            in.readFloat();
            in.readFloat();
            boolean recovered = in.readBoolean();
            if (id.isEmpty() || duration <= 0 || duration > ReplayLimits.MAX_DURATION_TICKS)
            {
                return null;
            }
            return new ReplayEntry(name, id, createdAt, duration, ownerUuid, ownerName, access,
                    dim, x, y, z, recovered);
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // ==================== 保存 ====================

    public static String saveBlockReason(MinecraftServer server, ReplayData data)
    {
        if (data == null)
        {
            return "没有录像数据";
        }
        ReplayMetadata m = data.metadata;
        if (!ReplayNames.isValidId(m.recordingId))
        {
            return "录像标识非法";
        }
        if (!ReplayNames.isValid(m.name))
        {
            return "名称非法";
        }
        if (m.durationTicks <= 0)
        {
            return "没有有效录制内容";
        }
        if (m.durationTicks > ReplayLimits.MAX_DURATION_TICKS)
        {
            return "超过单段时长上限";
        }
        if (data.participants.isEmpty())
        {
            return "没有参与者";
        }
        if (Files.exists(file(server, m.recordingId)))
        {
            return "录像标识冲突（已存在同 ID 文件）";
        }
        if (nameExists(server, m.ownerUuid, m.name, m.recordingId))
        {
            return "同名录像已存在";
        }
        return null;
    }

    /** 保存时名称冲突的保守方案：换一个同所有者下唯一的名称，绝不覆盖另一份录像 */
    public static String uniqueName(MinecraftServer server, String ownerUuid, String base)
    {
        String stem = base == null || base.isEmpty() ? "录像" : base;
        for (int i = 2; i < 100; i++)
        {
            String suffix = "-" + i;
            int keep = Math.max(1, ReplayLimits.MAX_NAME_LENGTH - suffix.length());
            String candidate = (stem.length() > keep ? stem.substring(0, keep) : stem) + suffix;
            if (!nameExists(server, ownerUuid, candidate, null))
            {
                return candidate;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static boolean nameExists(MinecraftServer server, String ownerUuid, String name, String exceptId)
    {
        for (ReplayEntry e : listOwned(server, ownerUuid))
        {
            if (e.name().equals(name) && !e.recordingId().equals(exceptId))
            {
                return true;
            }
        }
        return false;
    }

    public static String startBlockReason(MinecraftServer server, String ownerUuid)
    {
        int effective = listOwned(server, ownerUuid).size() + pendingFor(ownerUuid);
        if (effective < ReplayLimits.MAX_PER_PLAYER)
        {
            return null;
        }
        List<String> favIds = ReplayFavorites.list(server, ownerUuid);
        if (favIds == null)
        {
            return null;
        }
        List<ReplayEntry> entries = listOwned(server, ownerUuid);
        boolean allFavorited = entries.stream().allMatch(e -> favIds.contains(e.recordingId()));
        if (allFavorited)
        {
            return "录像已满（" + ReplayLimits.MAX_PER_PLAYER + " 段）且全部被收藏，请取消部分收藏或删除录像";
        }
        return null;
    }

    private static int pendingFor(String ownerUuid)
    {
        AtomicInteger n = PENDING_BY_OWNER.get(ownerUuid);
        return n == null ? 0 : n.get();
    }

    private static void changeOwnerPending(String ownerUuid, int delta)
    {
        if (ownerUuid == null)
        {
            return;
        }
        AtomicInteger n = PENDING_BY_OWNER.computeIfAbsent(ownerUuid, k -> new AtomicInteger());
        n.addAndGet(delta);
        if (n.get() <= 0)
        {
            PENDING_BY_OWNER.remove(ownerUuid, n);
        }
    }

    /**
     * 异步保存：主线程只做前置校验，编码与写盘在 IO 线程完成。
     * onDone 收到 true 才代表真实落盘。
     */
    public static boolean saveAsync(MinecraftServer server, ReplayData data,
                                    java.util.function.BiConsumer<Boolean, String> onDone)
    {
        String reason = saveBlockReason(server, data);
        if (reason != null)
        {
            LOGGER.error("[replay] 保存被拒绝: {} ({})", data == null ? "-" : data.metadata.name, reason);
            if (onDone != null)
            {
                server.execute(() -> onDone.accept(false, reason));
            }
            return false;
        }
        long estimate = estimateBytes(data);
        if (PENDING_BYTES.get() + estimate > ReplayLimits.MAX_PENDING_SAVE_BYTES)
        {
            if (onDone != null)
            {
                server.execute(() -> onDone.accept(false, "保存队列内存已达上限"));
            }
            return false;
        }
        PENDING_SAVES.incrementAndGet();
        PENDING_BYTES.addAndGet(estimate);
        changeOwnerPending(data.metadata.ownerUuid, 1);
        Path target = file(server, data.metadata.recordingId);
        try
        {
            IO.execute(() ->
            {
                try
                {
                    List<String> rotated = new ArrayList<>();
                    String rotateNote = null;
                    boolean ok;
                    try
                    {
                        ok = writeAtomic(target, data);
                    }
                    catch (Exception e)
                    {
                        LOGGER.error("[replay] 保存异常: {}", target, e);
                        ok = false;
                    }
                    if (ok)
                    {
                        rotated.addAll(autoRotateOldest(server, data.metadata.ownerUuid, data.metadata.recordingId));
                        if (!rotated.isEmpty())
                        {
                            rotateNote = "已自动删除最早录像: " + String.join("、", rotated);
                        }
                    }
                    final boolean success = ok;
                    final String note = rotateNote;
                    if (onDone != null)
                    {
                        server.execute(() -> onDone.accept(success, note));
                    }
                }
                finally
                {
                    PENDING_SAVES.decrementAndGet();
                    PENDING_BYTES.addAndGet(-estimate);
                    changeOwnerPending(data.metadata.ownerUuid, -1);
                }
            });
            return true;
        }
        catch (RejectedExecutionException e)
        {
            PENDING_SAVES.decrementAndGet();
            PENDING_BYTES.addAndGet(-estimate);
            changeOwnerPending(data.metadata.ownerUuid, -1);
            LOGGER.error("[replay] IO 队列已满，保存拒绝: {}", data.metadata.name);
            if (onDone != null)
            {
                server.execute(() -> onDone.accept(false, "IO 队列已满"));
            }
            return false;
        }
    }

    /** 停服保存失败的兜底：同步写一份检查点，开服按“异常恢复（非完整）”转换 */
    public static boolean saveCheckpointBlocking(MinecraftServer server, ReplayData data)
    {
        if (!ReplayNames.isValidId(data.metadata.recordingId))
        {
            return false;
        }
        return writeAtomic(checkpointFile(server, data.metadata.recordingId), data);
    }

    /** 同步保存（正常停服用）：直接编码落盘并等待结果，不依赖异步回调或守护线程 */
    public static boolean saveBlocking(MinecraftServer server, ReplayData data)
    {
        if (data != null && data.metadata.ownerUuid != null
                && nameExists(server, data.metadata.ownerUuid, data.metadata.name, data.metadata.recordingId))
        {
            data.metadata.name = uniqueName(server, data.metadata.ownerUuid, data.metadata.name);
        }
        String reason = saveBlockReason(server, data);
        if (reason != null)
        {
            LOGGER.error("[replay] 停服保存被拒绝: {} ({})", data == null ? "-" : data.metadata.name, reason);
            return false;
        }
        Path target = file(server, data.metadata.recordingId);
        boolean ok = writeAtomic(target, data);
        if (ok)
        {
            autoRotateOldest(server, data.metadata.ownerUuid, data.metadata.recordingId);
        }
        return ok;
    }

    /** 检查点异步写出：不参与数量轮换，不与正式保存争用同一路径 */
    public static void saveCheckpointAsync(MinecraftServer server, ReplayData data)
    {
        String id = data.metadata.recordingId;
        if (!ReplayNames.isValidId(id) || !CHECKPOINT_WRITING.add(id))
        {
            return;
        }
        long estimate = estimateBytes(data);
        if (PENDING_BYTES.get() + estimate > ReplayLimits.MAX_PENDING_SAVE_BYTES)
        {
            CHECKPOINT_WRITING.remove(id);
            return;
        }
        PENDING_BYTES.addAndGet(estimate);
        Path target = checkpointFile(server, id);
        try
        {
            IO.execute(() ->
            {
                try
                {
                    writeAtomic(target, data);
                }
                catch (Exception e)
                {
                    LOGGER.warn("[replay] 检查点写入失败: {}", id, e);
                }
                finally
                {
                    PENDING_BYTES.addAndGet(-estimate);
                    CHECKPOINT_WRITING.remove(id);
                }
            });
        }
        catch (RejectedExecutionException e)
        {
            PENDING_BYTES.addAndGet(-estimate);
            CHECKPOINT_WRITING.remove(id);
        }
    }

    private static long estimateBytes(ReplayData data)
    {
        long ticks = 0;
        for (var p : data.participants)
        {
            for (var seg : p.segments)
            {
                ticks += Math.max(0, seg.duration());
            }
        }
        return 4096L + data.participants.size() * 512L + ticks * 24L;
    }

    /**
     * 自动轮换：数量超过上限时删除创作者最早且未被创作者自己收藏的录像。
     * 收藏文件不可读时宁可不删。
     */
    private static List<String> autoRotateOldest(MinecraftServer server, String ownerUuid, String keepId)
    {
        List<String> deleted = new ArrayList<>();
        List<ReplayEntry> entries = listOwned(server, ownerUuid);
        int over = Math.max(0, entries.size() - ReplayLimits.MAX_PER_PLAYER);
        if (over <= 0)
        {
            return deleted;
        }
        List<String> favIds = ReplayFavorites.list(server, ownerUuid);
        if (favIds == null)
        {
            LOGGER.error("[replay] 创作者收藏不可读，跳过自动轮换");
            return deleted;
        }
        for (ReplayEntry e : selectRotationCandidates(entries, favIds, keepId, over))
        {
            if (delete(server, e.recordingId()))
            {
                LOGGER.info("[replay] 自动轮换删除最早录像: {}", e.name());
                deleted.add(e.name());
            }
        }
        return deleted;
    }

    /** 已落盘数量超出上限的份数 */
    static int overCount(int entryCount)
    {
        return Math.max(0, entryCount - ReplayLimits.MAX_PER_PLAYER);
    }

    /** entries 按创建时间倒序；从最旧开始跳过刚写入 ID 与创作者收藏 */
    static List<ReplayEntry> selectRotationCandidates(List<ReplayEntry> entries, List<String> favIds,
                                                      String keepId, int over)
    {
        List<ReplayEntry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0 && out.size() < over; i--)
        {
            ReplayEntry e = entries.get(i);
            if (e.recordingId().equals(keepId) || favIds.contains(e.recordingId()))
            {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    // ==================== 载入/修改/删除 ====================

    public static ReplayData load(MinecraftServer server, String recordingId)
    {
        if (!ReplayNames.isValidId(recordingId))
        {
            return null;
        }
        Path target = file(server, recordingId);
        if (!Files.isRegularFile(target))
        {
            return null;
        }
        try
        {
            long size = Files.size(target);
            if (size > ReplayLimits.MAX_FILE_BYTES)
            {
                LOGGER.error("[replay] 录像超过大小上限，拒绝载入: {} ({} 字节)", target, size);
                return null;
            }
            return ReplayCodec.decode(Files.readAllBytes(target));
        }
        catch (ReplayFormatException e)
        {
            LOGGER.error("[replay] 录像损坏，拒绝载入: {} ({})", target, e.getMessage());
            return null;
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 录像读取失败: {}", target, e);
            return null;
        }
    }

    public static boolean delete(MinecraftServer server, String recordingId)
    {
        if (!ReplayNames.isValidId(recordingId))
        {
            return false;
        }
        try
        {
            return Files.deleteIfExists(file(server, recordingId));
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 录像删除失败: {}", recordingId, e);
            return false;
        }
    }

    /** 改名只改元数据，文件名仍是稳定 ID */
    public static boolean rename(MinecraftServer server, String recordingId, String newName)
    {
        if (!ReplayNames.isValid(newName))
        {
            return false;
        }
        ReplayData data = load(server, recordingId);
        if (data == null || nameExists(server, data.metadata.ownerUuid, newName, recordingId))
        {
            return false;
        }
        data.metadata.name = newName;
        return writeAtomic(file(server, recordingId), data);
    }

    public static boolean setAccess(MinecraftServer server, String recordingId, ReplayAccess access)
    {
        ReplayData data = load(server, recordingId);
        if (data == null || access == null)
        {
            return false;
        }
        data.metadata.access = access;
        boolean ok = writeAtomic(file(server, recordingId), data);
        if (ok)
        {
            me.primaryuan.carpet.replay.phantom.PhantomEntityTracker.revalidateAccess(
                    server, recordingId, access, data.metadata.ownerUuid);
        }
        return ok;
    }

    // ==================== 检查点/恢复 ====================

    private static Path checkpointFile(MinecraftServer server, String id)
    {
        return dir(server, CHECKPOINTS).resolve(id + SUFFIX);
    }

    public static void deleteCheckpoint(MinecraftServer server, String id)
    {
        try
        {
            Files.deleteIfExists(checkpointFile(server, id));
        }
        catch (IOException e)
        {
            LOGGER.warn("[replay] 检查点删除失败: {}", id, e);
        }
    }

    /**
     * 开服恢复：崩溃遗留的检查点转成已结束录像，并明确标记 recovered。
     * 只恢复到最后可靠保存点；不恢复任务、不自动续录。
     */
    public static int recoverCheckpoints(MinecraftServer server)
    {
        Path d = dir(server, CHECKPOINTS);
        if (!Files.isDirectory(d))
        {
            return 0;
        }
        int recovered = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(d, "*" + SUFFIX))
        {
            for (Path p : stream)
            {
                try
                {
                    ReplayData probe = ReplayCodec.decode(Files.readAllBytes(p));
                    // 正式录像已存在说明检查点只是迟到写入的旧快照，不能用它覆盖新内容
                    if (Files.isRegularFile(file(server, probe.metadata.recordingId)))
                    {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    byte[] bytes = Files.readAllBytes(p);
                    ReplayData data = ReplayCodec.decode(bytes);
                    data.metadata.recoveredFromCrash = true;
                    data.diagnostics.captureLimitations.add(
                            "服务器异常中断：本录像恢复自最近一次自动检查点，可能缺少最后一段");
                    if (nameExists(server, data.metadata.ownerUuid, data.metadata.name, data.metadata.recordingId))
                    {
                        data.metadata.name = data.metadata.name + "-恢复";
                    }
                    if (writeAtomic(file(server, data.metadata.recordingId), data))
                    {
                        Files.deleteIfExists(p);
                        recovered++;
                    }
                }
                catch (Exception e)
                {
                    LOGGER.error("[replay] 检查点恢复失败: {}", p, e);
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 检查点目录读取失败", e);
        }
        return recovered;
    }

    // ==================== IO 工具 ====================

    /** 等待已排队写入完成（正常停服用，不依赖守护线程碰巧跑完） */
    public static void flushBlocking()
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        java.util.concurrent.Future<?> barrier = null;
        while (barrier == null)
        {
            try
            {
                barrier = IO.submit(() -> { });
            }
            catch (RejectedExecutionException e)
            {
                if (System.nanoTime() > deadline)
                {
                    LOGGER.error("[replay] 保存队列持续满载，等待超时");
                    return;
                }
                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        try
        {
            barrier.get(15, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            LOGGER.error("[replay] 等待保存队列完成失败", e);
        }
    }

    private static boolean writeAtomic(Path target, ReplayData data)
    {
        try
        {
            byte[] bytes = ReplayCodec.encode(data);
            if (bytes.length > ReplayLimits.MAX_FILE_BYTES)
            {
                LOGGER.error("[replay] 编码超过大小上限: {} 字节", bytes.length);
                return false;
            }
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp-" + System.nanoTime());
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 录像保存失败: {}", target, e);
            return false;
        }
        catch (Exception e)
        {
            LOGGER.error("[replay] 录像编码异常，保存放弃: {}", target, e);
            return false;
        }
    }
}
