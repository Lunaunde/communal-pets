package com.github.lunaunde.communalpets.menu;

import com.github.lunaunde.communalpets.Messages;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 界面的"门口"：手势判定、身份判定、打开哪张表、以及 UUID ↔ 显示名 / 通知。
 * <p>
 * 对外只暴露一个入口 {@link #tryOpen}：三个宠物 mixin 在 {@code mobInteract} 的 HEAD 调它，
 * 返回 true 就把这次交互吃掉（原版逻辑不再跑，也不会顺带坐下 / 喂食）。
 */
public final class PetMenus {

    private PetMenus() {
    }

    // ------------------------------------------------------------------
    // 打开
    // ------------------------------------------------------------------

    /**
     * 蹲下 + 右键已驯服的宠物 → 打开对应界面。
     *
     * @return {@code null} 表示这次交互不归界面管（原版逻辑继续跑）；否则就是该交回原版的交互结果
     */
    public static @Nullable InteractionResult tryOpen(TamableAnimal pet, Player player, InteractionHand hand) {
        // 只认主手：客户端在"主手这次交互没被消费掉"的时候会紧接着再发一个**副手**的交互包，
        // 两个都处理的话原版会给主手和副手各补一次挥手，swingingArm 被后者覆盖 —— 看到的就是副手在动。
        if (hand != InteractionHand.MAIN_HAND) {
            return null;
        }
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return null;
        }
        if (!pet.isTame()) {
            return null;
        }
        if (!player.isSecondaryUseActive()) {
            return null;
        }
        // 不立刻开：先让服务端补发的挥手包落地。同一 tick 就开界面的话，玩家自己的视角会被
        // 刚弹出的箱子界面挡住，看起来就像"右键了但手没动"（别人视角不受影响）。
        deferOpen(serverPlayer, pet, viewFor(pet, serverPlayer), 0, 0, 0, null);
        // 主人：原版客户端在这个位置能自己预测出挥手 → 用 CLIENT 源，服务端别再补一次（会连摆两下）。
        // 照顾者：原版客户端不认这个概念、预测不出挥手 → 交给服务端补（SERVER 源）。
        return CommunalPet.isOwner(pet, serverPlayer)
                ? InteractionResult.SUCCESS
                : InteractionResult.SUCCESS_SERVER;
    }

    /**
     * 这只宠物该给这个玩家看哪张表：
     * 抚养者（主人 ∪ 照顾者）→ 表1；被邀请的人 → 表0.1；其余（含 op）→ 表0。
     * <p>
     * op 故意<b>不</b>直接进表1：设计文档里 op 是从表0 的"命令方块"进表1 的。
     */
    public static PetMenuView viewFor(TamableAnimal pet, ServerPlayer player) {
        if (pet.isOwnedBy(player)) {
            return PetMenuView.MEMBER;
        }
        if (CommunalPet.hasInvitation(pet, player.getUUID())) {
            return PetMenuView.INVITED;
        }
        return PetMenuView.OUTSIDER;
    }

    /** 打开（或换界面重开）一张表；页码和确认目标会带着走，所以从表3 返回表2 还停在同一页。 */
    static void open(ServerPlayer player, TamableAnimal pet, PetMenuView view,
                     int memberPage, int outsiderPage, int applicationPage, @Nullable UUID confirmTarget) {
        MenuProvider provider = new SimpleMenuProvider(
                (syncId, inventory, opener) -> new PetOwnerMenu(syncId, inventory, player, pet, view,
                        memberPage, outsiderPage, applicationPage, confirmTarget),
                Messages.tr(titleKey(view), pet.getName()));
        player.openMenu(provider);
    }

    private static String titleKey(PetMenuView view) {
        return switch (view) {
            case OUTSIDER -> "communal-pets.menu.title.outsider";
            case INVITED -> "communal-pets.menu.title.invited";
            case MEMBER -> "communal-pets.menu.title.member";
            case MANAGE -> "communal-pets.menu.title.manage";
            case CONFIRM_KICK, CONFIRM_TRANSFER -> "communal-pets.menu.title.confirm";
            case APPLICATIONS -> "communal-pets.menu.title.applications";
        };
    }

    // ------------------------------------------------------------------
    // 名字 / 头像
    // ------------------------------------------------------------------

    /**
     * UUID → 显示名：在线玩家优先，其次 usercache（{@code nameToIdCache} 是本地文件缓存，<b>不联网</b>），
     * 最后退化成 UUID 字符串。
     * <p>
     * 别用 {@code services().profileResolver().fetchById(uuid)}：那个在缓存未命中时会向 Mojang 发请求，
     * 在服务端线程上会把整个服务器卡住。
     */
    public static Component nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        return server.services().nameToIdCache().get(id)
                .<Component>map(entry -> Component.literal(entry.name()))
                .orElseGet(() -> Component.literal(id.toString()));
    }

    /**
     * 带头像属性的档案：只有在线玩家拿得到（离线玩家只有 UUID，交给客户端自己解析，
     * 解析不到就是默认皮肤 —— 这比在服务端线程上联网查档案安全得多）。
     */
    public static @Nullable GameProfile profileOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        return online == null ? null : online.getGameProfile();
    }

    // ------------------------------------------------------------------
    // 通知（设计文档 STEP3）
    // ------------------------------------------------------------------

    /** 普通提示：只发给在线的本人；离线就不发（状态本身存在实体上，不会丢）。 */
    public static void notifyPlayer(MinecraftServer server, UUID id, String key, Object... args) {
        ServerPlayer target = server.getPlayerList().getPlayer(id);
        if (target != null) {
            target.sendSystemMessage(Messages.tr(key, args));
        }
    }

    /**
     * "有新的申请 / 邀请"提示：除了文本，再附一行可点击的
     * {@code [同意] [拒绝]}（跑的是 {@code /communalpets accept|reject}）。
     * <p>
     * 主人离线时这里什么都不做 —— 请求存在实体上（界面里长期有效），他下次打开表4 就能看到。
     */
    public static void notifyPending(MinecraftServer server, UUID id, String key, Object... args) {
        ServerPlayer target = server.getPlayerList().getPlayer(id);
        if (target != null) {
            target.sendSystemMessage(Messages.tr(key, args));
            target.sendSystemMessage(PetOwnerMenu.answerButtons());
        }
    }

    /** 通知这只宠物的主人（在线才发）。 */
    public static void notifyOwner(TamableAnimal pet, String key, Object... args) {
        MinecraftServer server = pet.level().getServer();
        LivingEntity owner = pet.getOwner();
        if (server != null && owner instanceof ServerPlayer ownerPlayer) {
            ownerPlayer.sendSystemMessage(Messages.tr(key, args));
            ownerPlayer.sendSystemMessage(PetOwnerMenu.answerButtons());
        }
    }

    /** 可点击文本（原版客户端也认这个，因为是原版聊天组件）。 */
    public static MutableComponent clickable(String key, String command, ChatFormatting color) {
        return Messages.tr(key).withStyle(style -> style
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withColor(color));
    }

    // ------------------------------------------------------------------
    // 延迟一 tick 开界面：把"挥手"让回玩家自己的视角
    // ------------------------------------------------------------------

    /**
     * 开界面前等这么多个 tick。1 个 tick 就够让服务端补发的挥手包先到客户端。
     * <p>
     * 原因：{@code ServerGamePacketListenerImpl.handleInteract} 是在交互处理<b>结束之后</b>
     * 才调 {@code player.swing(hand, true)} 的，所以挥手包永远排在开界面包<b>后面</b>；
     * 不等这一下，玩家自己的视角就是"箱子界面先弹出来、挥手在界面后面播完"——
     * 别人视角正常，因为他们的屏幕不会打开。
     */
    private static final int OPEN_DELAY_TICKS = 1;

    /** 待打开的界面（等 {@code readyAt} 这个 tick 到了再开）。 */
    private record PendingOpen(ServerPlayer player, TamableAnimal pet, PetMenuView view,
                               int memberPage, int outsiderPage, int applicationPage,
                               @Nullable UUID confirmTarget, int readyAt) {
    }

    private static final List<PendingOpen> PENDING_OPENS = new ArrayList<>();

    /** 由 {@code CommunalPets#onInitialize()} 注册。 */
    public static void registerTickHandler() {
        ServerTickEvents.END_SERVER_TICK.register(PetMenus::flushPendingOpens);
    }

    /** 排一个延迟打开；同一个玩家之前排的会被顶掉，免得一次点击开出两个界面。 */
    private static void deferOpen(ServerPlayer player, TamableAnimal pet, PetMenuView view,
                                  int memberPage, int outsiderPage, int applicationPage,
                                  @Nullable UUID confirmTarget) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PENDING_OPENS.removeIf(pending -> pending.player() == player);
        PENDING_OPENS.add(new PendingOpen(player, pet, view, memberPage, outsiderPage,
                applicationPage, confirmTarget, server.getTickCount() + OPEN_DELAY_TICKS));
    }

    /**
     * 到点了就开界面。这一 tick 里玩家可能已经断线、开了别的容器、走远，或者宠物死了 ——
     * 任何一条不满足就直接丢弃，不补开。
     */
    private static void flushPendingOpens(MinecraftServer server) {
        if (PENDING_OPENS.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        PENDING_OPENS.removeIf(pending -> {
            if (now < pending.readyAt()) {
                return false;
            }
            ServerPlayer player = pending.player();
            TamableAnimal pet = pending.pet();
            if (player.connection == null || player.containerMenu != player.inventoryMenu) {
                return true;
            }
            if (!pet.isAlive() || !pet.isTame() || pet.level() != player.level()
                    || player.distanceToSqr(pet) > 64.0) {
                return true;
            }
            open(player, pet, pending.view(), pending.memberPage(), pending.outsiderPage(),
                    pending.applicationPage(), pending.confirmTarget());
            return true;
        });
    }

    // ------------------------------------------------------------------
    // 服务端补挥手
    // ------------------------------------------------------------------

    /**
     * 补一次服务端挥手（会广播 {@code ClientboundAnimatePacket}，附近玩家也能看到）。
     * <p>
     * <b>为什么需要</b>：切行为那段原版代码是 {@code isOwnedBy(player)} 门控的，而
     * <b>原版客户端不认"照顾者"这个概念</b> —— 照顾者点击时客户端本地走不到那段、预测不出挥手；
     * 服务端那条 {@code InteractionResult.SUCCESS} 又是 CLIENT 源（"客户端自己摆手"），
     * 两边都不摆手，玩家看到的就是"右键了但手不动"。
     * <p>
     * <b>为什么只补照顾者</b>：主人身上原版客户端能预测出挥手，服务端再补一次会变成连摆两下。
     * <p>
     * 已知取舍：装了本 mod 的客户端在照顾者身上也会预测挥手（{@code isOwnedBy} 被本模组放宽了），
     * 这种组合下会多摆一次 —— 本机 / 局域网看不出来，高延迟下是一次轻微的重摆。
     */
    public static void swingForCaretakerInteraction(TamableAnimal pet, @Nullable LivingEntity who,
                                                    InteractionHand hand) {
        if (pet.level().isClientSide() || !(who instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (CommunalPet.isOwner(pet, serverPlayer)) {
            return;
        }
        serverPlayer.swing(hand, true);
    }
}
