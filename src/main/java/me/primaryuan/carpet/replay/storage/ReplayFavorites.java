package me.primaryuan.carpet.replay.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 个人收藏：按观看者 UUID 保存录像稳定 ID 列表，只存引用，不复制录像，
 * 不授予任何管理权限。收藏读取失败返回 null，调用方必须保守处理。
 */
public final class ReplayFavorites
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "favorites.json";
    private static final int VERSION = 1;

    private ReplayFavorites() {}

    public static List<String> list(MinecraftServer server, String viewerUuid)
    {
        Path file = file(server, viewerUuid);
        if (!Files.isRegularFile(file))
        {
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 0;
            if (version > VERSION || !root.has("ids") || !root.get("ids").isJsonArray())
            {
                LOGGER.error("[replay] 收藏文件版本或结构异常: {}", file);
                return null;
            }
            List<String> out = new ArrayList<>();
            root.getAsJsonArray("ids").forEach(e -> out.add(e.getAsString()));
            return out;
        }
        catch (Exception e)
        {
            LOGGER.error("[replay] 收藏文件读取失败", e);
            return null;
        }
    }

    public static boolean add(MinecraftServer server, String viewerUuid, String recordingId)
    {
        return mutate(server, viewerUuid, recordingId, true);
    }

    public static boolean remove(MinecraftServer server, String viewerUuid, String recordingId)
    {
        return mutate(server, viewerUuid, recordingId, false);
    }

    private static synchronized boolean mutate(MinecraftServer server, String viewerUuid,
                                               String recordingId, boolean add)
    {
        List<String> ids = list(server, viewerUuid);
        if (ids == null)
        {
            LOGGER.error("[replay] 收藏文件不可读，操作放弃");
            return false;
        }
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>(ids);
        boolean changed = add ? set.add(recordingId) : set.remove(recordingId);
        if (!changed && set.size() == ids.size())
        {
            return true;
        }
        return write(server, viewerUuid, new ArrayList<>(set));
    }

    private static boolean write(MinecraftServer server, String viewerUuid, List<String> ids)
    {
        Path file = file(server, viewerUuid);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp-" + System.nanoTime());
        try
        {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            ids.forEach(arr::add);
            root.add("ids", arr);
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
            {
                GSON.toJson(root, writer);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        }
        catch (IOException e)
        {
            LOGGER.error("[replay] 收藏写入失败: {}", file, e);
            return false;
        }
    }

    private static Path file(MinecraftServer server, String viewerUuid)
    {
        return ReplayStorage.dir(server, ReplayStorage.FAVORITES_FOLDER())
                .resolve(viewerUuid + "-" + FILE);
    }
}
