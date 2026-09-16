package com.github.lunaunde.communalpets;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

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
 *     <li>客户端装了本 mod（或装了对应资源包）→ 用客户端自己的 {@code assets/communal-pets/lang/*.json}，
 *         所以语言切换是客户端实时生效的；</li>
 *     <li>客户端没有这份语言文件（本 mod 常见的"只在服务端安装"情况）→ 用兜底文本，
 *         而 {@code translatable} 在这种情况下会直接显示裸键名。</li>
 * </ul>
 * <b>兜底文本按"谁在看"选语言</b>：玩家 → 他客户端上报的语言
 * （{@link ServerPlayer#clientInformation()}{@code .language()}，例如 {@code zh_cn}）；
 * 控制台 / 命令方块 / 没人在看 → 配置里的 {@link CommunalPetsConfig#fallbackLanguage()}。
 * 这是原版客户端也能跟着语言切换的关键：他们的客户端没有语言文件，文本只能由服务端按他的语言生成。
 * <p>
 * 每种语言的语言文件都是<b>第一次用到才读</b>（配置那时也已经加载完了），读不到就退回
 * {@code fallback_language} → {@code en_us} → 裸键名。兜底文本会随组件一起发给客户端，
 * 并且套用 {@code %s} 参数（见 {@code TranslatableContents}），所以嵌套的组件（玩家名等）
 * 保留自己的样式。
 */
public final class Messages {

    private static final Gson GSON = new Gson();
    private static final String LANG_DIR = "/assets/" + CommunalPets.MOD_ID + "/lang/";

    /** language -> (key -> text)。{@code Map.of()} 表示这个语言<b>没有</b>语言文件（负结果也缓存）。 */
    private static final Map<String, Map<String, String>> TABLES = new HashMap<>();

    private Messages() {
    }

    /** 服务端默认语言的文本（控制台、命令方块、以及拿不到玩家的地方）。 */
    public static MutableComponent tr(String key, Object... args) {
        return tr((ServerPlayer) null, key, args);
    }

    /** 这个玩家看到的那份文本（{@code player == null} 就是服务端默认语言）。 */
    public static MutableComponent tr(@Nullable ServerPlayer player, String key, Object... args) {
        return Component.translatableWithFallback(key, fallback(languageOf(player), key), args);
    }

    /** 指令输出：玩家执行就按玩家的语言，控制台 / 命令方块按服务端默认语言。 */
    public static MutableComponent tr(CommandSourceStack source, String key, Object... args) {
        return tr(source.getEntity() instanceof ServerPlayer player ? player : null, key, args);
    }

    /**
     * 这个玩家该用哪种语言：优先他客户端上报的语言，但<b>只认本 mod 真的带了语言文件的语言</b>
     * （否则会退化成裸键名），其余一律用配置里的 {@code fallback_language}。
     */
    public static String languageOf(@Nullable ServerPlayer player) {
        if (player == null) {
            return CommunalPetsConfig.fallbackLanguage();
        }
        String language = player.clientInformation().language();
        if (language != null && !language.isBlank() && !table(language).isEmpty()) {
            return language;
        }
        return CommunalPetsConfig.fallbackLanguage();
    }

    /** 兜底文本：目标语言 → fallback_language → en_us → 键名。 */
    private static String fallback(String language, String key) {
        String text = table(language).get(key);
        if (text != null) {
            return text;
        }
        for (String other : List.of(CommunalPetsConfig.fallbackLanguage(), CommunalPetsConfig.DEFAULT_FALLBACK_LANGUAGE)) {
            if (!other.equals(language)) {
                text = table(other).get(key);
                if (text != null) {
                    return text;
                }
            }
        }
        return key;
    }

    /**
     * 读一种语言的语言文件（走 classloader，所以开发环境和正式 jar 都一样）。
     * <p>
     * {@code synchronized}：客户端线程和服务端线程都可能第一次进来，靠监视器建立 happens-before；
     * 否则另一个线程可能看到缓存里已经有这个语言、却还没看到内容（会显示裸键名）。
     */
    private static synchronized Map<String, String> table(String language) {
        Map<String, String> table = TABLES.get(language);
        if (table == null) {
            table = load(language);
            TABLES.put(language, table);
        }
        return table;
    }

    private static Map<String, String> load(String language) {
        String path = LANG_DIR + language + ".json";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in == null) {
                return Map.of();
            }
            JsonObject json = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            if (json == null || json.isEmpty()) {
                return Map.of();
            }
            Map<String, String> table = new HashMap<>();
            json.entrySet().forEach(entry -> table.put(entry.getKey(), entry.getValue().getAsString()));
            CommunalPets.LOGGER.info("Loaded {} strings from lang/{}.json", table.size(), language);
            return Map.copyOf(table);
        } catch (Exception e) {
            CommunalPets.LOGGER.warn("Failed to read {} as a language file", path, e);
            return Map.of();
        }
    }
}
