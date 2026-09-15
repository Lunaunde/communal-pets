package com.github.lunaunde.communalpets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有发给玩家的文本都从这里走。
 * <p>
 * 用 {@link Component#translatableWithFallback} 而不是 {@code translatable}：
 * <ul>
 *     <li>客户端装了本 mod（或装了对应资源包）→ 用 {@code assets/communal-pets/lang/*.json} 里的翻译；</li>
 *     <li>客户端没有这份语言文件（本 mod 常见的"只在服务端安装"情况）→ 用兜底文本，
 *         而 {@code translatable} 在这种情况下会直接显示裸键名。</li>
 * </ul>
 * <b>兜底文本不在代码里写死</b>，而是启动后按 {@link CommunalPetsConfig#fallbackLanguage()} 指定的语言，
 * 直接从 mod 自带的语言文件里读出来（走 classloader，所以开发环境和正式 jar 都一样）。
 * 兜底文本同样会套用 {@code %s} 参数（见 {@code TranslatableContents#decompose}）。
 */
public final class Messages {

    private static final Gson GSON = new Gson();
    private static final String LANG_DIR = "/assets/" + CommunalPets.MOD_ID + "/lang/";

    /** key -> 兜底文本。 */
    private static final Map<String, String> FALLBACKS = new HashMap<>();

    private static boolean loaded;

    private Messages() {
    }

    /** 走 lang + 兜底文本。 */
    public static MutableComponent tr(String key, Object... args) {
        ensureLoaded();
        return Component.translatableWithFallback(key, FALLBACKS.getOrDefault(key, key), args);
    }

    /**
     * 第一次用到时才读语言文件（那时配置已经加载完了）。
     * 顺序：配置指定的语言 → en_us → zh_cn，全都没有就只能显示键名了。
     * <p>
     * {@code synchronized}：客户端线程和服务端线程都可能第一次进来，靠监视器建立 happens-before；
     * 否则另一个线程可能看到 {@code loaded == true} 却还没看到 {@link #FALLBACKS} 的内容（会显示裸键名）。
     */
    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;

        for (String language : List.of(
                CommunalPetsConfig.fallbackLanguage(),
                "en_us",
                CommunalPetsConfig.DEFAULT_FALLBACK_LANGUAGE)) {
            if (loadFrom(language)) {
                CommunalPets.LOGGER.info("Loaded {} fallback strings from lang/{}.json",
                        FALLBACKS.size(), language);
                return;
            }
        }
        CommunalPets.LOGGER.warn("No usable language file found under {}; "
                + "clients without this mod will see raw translation keys", LANG_DIR);
    }

    private static boolean loadFrom(String language) {
        String path = LANG_DIR + language + ".json";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                return false;
            }
            JsonObject json = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (json == null || json.isEmpty()) {
                return false;
            }
            json.entrySet().forEach(entry -> FALLBACKS.put(entry.getKey(), entry.getValue().getAsString()));
            return true;
        } catch (Exception e) {
            CommunalPets.LOGGER.warn("Failed to read {} as a fallback language", path, e);
            return false;
        }
    }
}
