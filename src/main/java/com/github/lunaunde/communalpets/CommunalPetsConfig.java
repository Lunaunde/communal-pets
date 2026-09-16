package com.github.lunaunde.communalpets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置文件：{@code config/communal-pets.json}。
 * <p>
 * 目前只有一个选项：
 * <pre>
 * {
 *   "fallback_language": "zh_cn"
 * }
 * </pre>
 * 含义是<b>兜底语言</b>：文本是按"看的人"的语言生成的（见 {@link Messages}）—— 玩家优先用他客户端上报的
 * 语言（前提是本 mod 资源里有 {@code assets/communal-pets/lang/<语言>.json}，语言代码如 {@code zh_cn}），
 * 控制台 / 命令方块、以及客户端语言我们没有对应语言文件的玩家，都用这里的 {@code fallback_language}。
 * <p>
 * 装了 mod（或装了对应资源包）的客户端不受影响：他们自己就能翻译，永远跟着自己的语言走。
 */
public final class CommunalPetsConfig {

    public static final String DEFAULT_FALLBACK_LANGUAGE = "zh_cn";

    private static final String FILE_NAME = "communal-pets.json";
    private static final String KEY_FALLBACK_LANGUAGE = "fallback_language";

    private static String fallbackLanguage = DEFAULT_FALLBACK_LANGUAGE;

    private CommunalPetsConfig() {
    }

    /** 在 {@code onInitialize()} 里调用一次：文件不存在就写一份默认的。 */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try {
            if (Files.notExists(path)) {
                JsonObject defaults = new JsonObject();
                defaults.addProperty(KEY_FALLBACK_LANGUAGE, DEFAULT_FALLBACK_LANGUAGE);
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(defaults));
                CommunalPets.LOGGER.info("Created default config at {}", path);
                return;
            }

            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (json.has(KEY_FALLBACK_LANGUAGE)) {
                fallbackLanguage = json.get(KEY_FALLBACK_LANGUAGE).getAsString();
            } else {
                CommunalPets.LOGGER.warn("{} has no '{}', using default '{}'",
                        FILE_NAME, KEY_FALLBACK_LANGUAGE, DEFAULT_FALLBACK_LANGUAGE);
                fallbackLanguage = DEFAULT_FALLBACK_LANGUAGE;
            }
            CommunalPets.LOGGER.info("Fallback language (console / unknown client languages): {}", fallbackLanguage);
        } catch (Exception e) {
            CommunalPets.LOGGER.warn("Failed to read {}, using default fallback language '{}'",
                    path, DEFAULT_FALLBACK_LANGUAGE, e);
            fallbackLanguage = DEFAULT_FALLBACK_LANGUAGE;
        }
    }

    /** 配置里指定的保底语言（语言文件的文件名，不含 .json）。 */
    public static String fallbackLanguage() {
        return fallbackLanguage;
    }
}
