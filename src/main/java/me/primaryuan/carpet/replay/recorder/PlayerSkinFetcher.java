package me.primaryuan.carpet.replay.recorder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.primaryuan.carpet.replay.common.ReplayData;
import me.primaryuan.carpet.replay.common.ReplayParticipant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 离线服务器玩家 profile 不携带 textures（Mojang hasJoined 仅在线验证时调用），
 * 录像保存时按名字向 Mojang API 取正版 profile 的皮肤数据回填，取不到（非正版名/无网络）保持为空。
 * 仅在 IO 线程调用，不阻塞主线程。
 */
public final class PlayerSkinFetcher
{
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    private PlayerSkinFetcher() {}

    /** 带一次重试的 GET（Mojang API 偶发超时） */
    private static HttpResponse<String> sendWithRetry(URI uri)
    {
        for (int attempt = 0; attempt < 2; attempt++)
        {
            try
            {
                HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).GET().build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 429)
                {
                    Thread.sleep(1500);
                    continue;
                }
                return res;
            }
            catch (Exception e)
            {
                if (attempt == 1)
                {
                    LOGGER.warn("[replay] Mojang API 请求失败: {} ({})", uri, e.toString());
                }
            }
        }
        return null;
    }

    /** 参与者皮肤为空时按名字抓取（value 直接来自 sessionserver，未签名即可渲染） */
    public static void fetchInto(ReplayData data)
    {
        for (ReplayParticipant participant : data.participants)
        {
            if (participant.skinTextures != null && !participant.skinTextures.isEmpty())
            {
                continue;
            }
            try
            {
                String name = participant.skinName == null || participant.skinName.isEmpty()
                        ? participant.name : participant.skinName;
                if (name == null || name.isEmpty())
                {
                    continue;
                }
                HttpResponse<String> idRes = sendWithRetry(URI.create(
                        "https://api.mojang.com/users/profiles/minecraft/" + name));
                if (idRes == null || idRes.statusCode() != 200)
                {
                    continue;
                }
                JsonObject id = JsonParser.parseString(idRes.body()).getAsJsonObject();
                String uuid = id.get("id").getAsString();
                HttpResponse<String> texRes = sendWithRetry(URI.create(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=true"));
                if (texRes == null || texRes.statusCode() != 200)
                {
                    continue;
                }
                JsonObject profile = JsonParser.parseString(texRes.body()).getAsJsonObject();
                for (var prop : profile.getAsJsonArray("properties"))
                {
                    JsonObject p = prop.getAsJsonObject();
                    if ("textures".equals(p.get("name").getAsString()))
                    {
                        participant.skinTextures = p.get("value").getAsString();
                        LOGGER.info("[replay] 已从 Mojang 回填皮肤数据: {} ({} 字符)", name,
                                participant.skinTextures.length());
                        break;
                    }
                }
            }
            catch (Exception e)
            {
                LOGGER.warn("[replay] 皮肤抓取失败（不影响录像数据）: {}", e.toString());
            }
        }
    }
}
