package com.github.lunaunde.communalpets.menu;

import com.github.lunaunde.communalpets.Messages;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 假箱子界面里的"控件"全部是原版物品：文字靠 {@code CUSTOM_NAME} + {@code LORE}，
 * 数量靠 {@code count}，发光靠 {@code ENCHANTMENT_GLINT_OVERRIDE}，头颅皮肤靠 {@code PROFILE}。
 * <p>
 * 所有文案都走 {@link Messages#tr}：它用的是 {@code translatableWithFallback}，兜底文本会随组件
 * 一起发给客户端，所以<b>没装本 mod 的原版客户端也能看到完整中文 / 英文</b>（这正是纯服务端方案的关键）。
 * <p>
 * <b>两条硬性规矩都收口在这个类里，别在调用方另起炉灶：</b>
 * <ul>
 *     <li><b>物品名和 Lore 一律不许斜体</b>：原版对 {@code CUSTOM_NAME} / {@code LORE} 是<b>默认斜体</b>渲染的，
 *         必须显式 {@code withItalic(false)} 才会正着显示 —— 见 {@link #plain}，所有出栈的物品都过它；</li>
 *     <li><b>配色按角色 / 语义走</b>，不要一片默认白：主人=金、照顾者=蓝、邀请候选=灰、申请=黄、
 *         正面动作=绿、拒绝取消=红、管理入口=金、页码=白、说明行=深灰、强调=黄、
 *         行为按钮=和发光色一致的 黄绿 / 浅蓝 / 橙 / 红。</li>
 * </ul>
 * 注意 26.2 把染色物品合并成了 {@code ColorCollection<Item>}：没有 {@code LIME_STAINED_GLASS}
 * 这种常量，要用 {@code Items.STAINED_GLASS.pick(DyeColor.LIME)} 这类写法。
 */
final class PetMenuIcons {

    /** 正在看这个界面的人：兜底文本按他客户端上报的语言生成（null = 服务端默认语言）。 */
    private final @Nullable ServerPlayer player;

    PetMenuIcons(@Nullable ServerPlayer player) {
        this.player = player;
    }

    // ------------------------------------------------------------------
    // 文本：颜色 + 去斜体
    // ------------------------------------------------------------------

    /**
     * 去掉斜体（保留调用方已经设好的颜色）。
     * <p>
     * 原版渲染 {@code CUSTOM_NAME} / {@code LORE} 时会套一层斜体，只有显式写 {@code withItalic(false)}
     * 才能压掉 —— 这就是"物品名和 Lore 全是斜体"的根因。
     */
    private static Component plain(Component component) {
        return component.copy().withStyle(style -> style.withItalic(false));
    }

    /** 物品名：指定颜色。 */
    MutableComponent label(String key, ChatFormatting color, Object... args) {
        return Messages.tr(this.player, key, args).withStyle(color);
    }

    /** 说明行（"怎么点"）：深灰，别抢主角。 */
    Component hint(String key, Object... args) {
        return Messages.tr(this.player, key, args).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** 强调行（有待处理的东西、已邀请等）：黄色。 */
    Component note(String key, Object... args) {
        return Messages.tr(this.player, key, args).withStyle(ChatFormatting.YELLOW);
    }

    /**
     * 头颅名称的角色配色：<b>主人=金、照顾者=蓝、邀请候选=灰、申请=黄</b>，
     * 只有"只显示玩家名"那种（表3 / 表3.1 中间那颗）是白。
     * <p>
     * 键和 {@code PetOwnerMenu} 的调用点共用同一批字面量 —— 想换颜色只改这里一处。
     */
    ChatFormatting headColor(@Nullable String nameKey) {
        if (nameKey == null) {
            return ChatFormatting.WHITE;
        }
        return switch (nameKey) {
            case "communal-pets.menu.head.owner" -> ChatFormatting.GOLD;
            case "communal-pets.menu.head.caretaker" -> ChatFormatting.BLUE;
            case "communal-pets.menu.head.invite" -> ChatFormatting.GRAY;
            case "communal-pets.menu.head.application" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.WHITE;
        };
    }

    /**
     * 宠物图标下面那行「主人：X」：整行深灰，主人名单独金色
     * （{@code ownerName} 为 null 表示这只宠物没有主人）。
     */
    Component ownerLine(@Nullable Component ownerName) {
        Component name = ownerName == null
                ? Messages.tr(this.player, "communal-pets.menu.head.no_owner").withStyle(ChatFormatting.GRAY)
                : ownerName.copy().withStyle(ChatFormatting.GOLD);
        return Messages.tr(this.player, "communal-pets.menu.pet.lore.owner", name).withStyle(ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------
    // 组装
    // ------------------------------------------------------------------

    /** 统一的出栈口：名称和 Lore 都过一遍 {@link #plain}，所以不会有斜体漏网。 */
    private static ItemStack decorate(ItemStack stack, Component name, List<Component> lore) {
        stack.set(DataComponents.CUSTOM_NAME, plain(name));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (Component line : lore) {
                lines.add(plain(line));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    private static ItemStack of(Item item, Component name, List<Component> lore) {
        return decorate(new ItemStack(item), name, lore);
    }

    private static ItemStack of(Item item, Component name) {
        return of(item, name, List.of());
    }

    // ------------------------------------------------------------------
    // 宠物本体
    // ------------------------------------------------------------------

    /**
     * 宠物图标：优先用它自己的刷怪蛋（狼 / 猫 / 鹦鹉在 26.2 都有），拿不到就退化成玩家头颅。
     * 名称 = 宠物名，唯一一行 lore = 主人名（由调用方拼好，主人名是金色的）。
     */
    ItemStack petIcon(TamableAnimal pet, Component ownerLine) {
        ItemStack stack = SpawnEggItem.byId(pet.getType())
                .map(holder -> new ItemStack(holder))
                .orElseGet(() -> new ItemStack(Items.PLAYER_HEAD));
        return decorate(stack, pet.getName(), List.of(ownerLine));
    }

    // ------------------------------------------------------------------
    // 表0 / 表0.1 / 表1 的按钮
    // ------------------------------------------------------------------

    ItemStack applyFeather() {
        return of(Items.FEATHER,
                label("communal-pets.menu.apply", ChatFormatting.GREEN),
                List.of(hint("communal-pets.menu.apply.lore")));
    }

    ItemStack adminBlock() {
        return of(Items.COMMAND_BLOCK,
                label("communal-pets.menu.admin", ChatFormatting.AQUA),
                List.of(hint("communal-pets.menu.admin.lore")));
    }

    ItemStack acceptPane() {
        return of(Items.STAINED_GLASS_PANE.lime(),
                label("communal-pets.menu.invite.accept", ChatFormatting.GREEN),
                List.of(hint("communal-pets.menu.invite.accept.lore")));
    }

    ItemStack rejectPane() {
        return of(Items.STAINED_GLASS_PANE.red(),
                label("communal-pets.menu.invite.reject", ChatFormatting.RED),
                List.of(hint("communal-pets.menu.invite.reject.lore")));
    }

    /**
     * 行为状态按钮：当前状态 = 混凝土 + <b>加粗</b>，其它 = 玻璃块。
     * 文字颜色跟着发光色走（黄绿 / 浅蓝 / 橙 / 红），所以界面和宠物头顶的光是同一个语义。
     */
    ItemStack behaviorButton(boolean active, DyeColor color, Component name) {
        Item item = active ? Items.CONCRETE.pick(color) : Items.STAINED_GLASS.pick(color);
        MutableComponent text = name.copy().withStyle(textColorOf(color));
        if (active) {
            text.withStyle(ChatFormatting.BOLD);
        }
        return of(item, text, List.of(hint(active
                ? "communal-pets.menu.behavior.current"
                : "communal-pets.menu.behavior.lore")));
    }

    /** 染色物品的颜色 → 聊天色（TeamColor 里没有 lime / light_blue / orange，只能这样对齐）。 */
    private static ChatFormatting textColorOf(DyeColor color) {
        return switch (color) {
            case LIME -> ChatFormatting.GREEN;
            case LIGHT_BLUE -> ChatFormatting.AQUA;
            case ORANGE -> ChatFormatting.GOLD;
            default -> ChatFormatting.RED;
        };
    }

    /**
     * 游荡范围命名牌：数量就是半径（界面里可调 1~99）。
     * 半径被指令设到 99 以上时数量只能显示 99（{@code ItemStack} 上限就是 99），真实值写进 lore。
     */
    ItemStack radiusNameTag(double radius) {
        int shown = (int) Math.max(1, Math.min(99, Math.round(radius)));
        List<Component> lore = new ArrayList<>();
        lore.add(radius > 99
                ? hint("communal-pets.menu.radius.lore.capped", number(radius))
                : hint("communal-pets.menu.radius.lore", number(radius)));
        ItemStack stack = of(Items.NAME_TAG,
                label("communal-pets.menu.radius", ChatFormatting.YELLOW), lore);
        stack.setCount(shown);
        return stack;
    }

    /** 半径步进粒：名称就是步长（黄），说明里写清左键加、右键减。 */
    ItemStack radiusNugget(Item item, int step) {
        return of(item,
                Component.literal("+" + step).withStyle(ChatFormatting.YELLOW),
                List.of(hint("communal-pets.menu.radius.step", step, step)));
    }

    /** 页码命名牌：数量 = 当前页。 */
    ItemStack pageNameTag(int page, int pageCount, String titleKey) {
        ItemStack stack = of(Items.NAME_TAG,
                label(titleKey, ChatFormatting.WHITE, page, Math.max(1, pageCount)),
                List.of(hint("communal-pets.menu.page.lore")));
        stack.setCount(Math.max(1, Math.min(99, page)));
        return stack;
    }

    /** 纸张：抚养者管理入口（名称金）；有未处理申请时加附魔纹理，并多一行黄色强调。 */
    ItemStack paper(boolean pending) {
        List<Component> lore = new ArrayList<>();
        lore.add(hint("communal-pets.menu.paper.lore.manage"));
        lore.add(hint("communal-pets.menu.paper.lore.requests"));
        if (pending) {
            lore.add(note("communal-pets.menu.paper.lore.pending"));
        }
        ItemStack stack = of(Items.PAPER,
                label("communal-pets.menu.paper", ChatFormatting.GOLD), lore);
        if (pending) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return stack;
    }

    /** 表2 中间那列分界线。 */
    ItemStack divider() {
        return of(Items.STAINED_GLASS_PANE.gray(),
                label("communal-pets.menu.divider", ChatFormatting.DARK_GRAY),
                List.of(hint("communal-pets.menu.divider.lore")));
    }

    ItemStack confirmPane(Component text) {
        return of(Items.STAINED_GLASS_PANE.lime(),
                text.copy().withStyle(ChatFormatting.GREEN), List.of(hint("communal-pets.menu.confirm.lore")));
    }

    ItemStack cancelPane(Component text) {
        return of(Items.STAINED_GLASS_PANE.red(),
                text.copy().withStyle(ChatFormatting.RED), List.of(hint("communal-pets.menu.cancel.lore")));
    }

    // ------------------------------------------------------------------
    // 玩家头颅
    // ------------------------------------------------------------------

    /**
     * 玩家头颅按钮。颜色由调用方按角色给（主人=金、照顾者=蓝、邀请候选=灰、申请=黄）。
     * <p>
     * 在线玩家直接用服务端手上的 {@link GameProfile}（带皮肤属性）→ 客户端不用再联网解析；
     * 离线玩家只有 UUID，用 {@code createUnresolved} 交给客户端自己解析，解析不到就是默认皮肤。
     */
    ItemStack playerHead(UUID id, @Nullable GameProfile profile, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, profile != null
                ? ResolvableProfile.createResolved(profile)
                : ResolvableProfile.createUnresolved(id));
        return decorate(stack, name, lore);
    }

    /** 没有内容时给一个"空"提示物，免得玩家以为界面坏了。 */
    ItemStack emptyHint(Component name) {
        return of(Items.STAINED_GLASS_PANE.white(), name.copy().withStyle(ChatFormatting.GRAY));
    }

    private static String number(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
