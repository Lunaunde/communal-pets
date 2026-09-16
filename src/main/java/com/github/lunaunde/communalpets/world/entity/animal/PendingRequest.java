package com.github.lunaunde.communalpets.world.entity.animal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * 一条"待处理"记录：要么是玩家<b>申请</b>成为照顾者（等主人批），要么是主人<b>邀请</b>玩家成为照顾者（等对方答）。
 * <p>
 * <b>时间戳用现实时间</b>（{@link System#currentTimeMillis()}）而不是游戏刻：指令应答的 15 分钟有效期
 * 该按现实时间算 —— 服务器停机、区块卸载、游戏刻暂停都不应该给一条旧请求续命。
 * <p>
 * <b>界面里的应答不看时间戳</b>（设计文档：界面请求长期有效），只有
 * {@code /communalpets accept|reject} 这条快捷路径才受 {@link #COMMAND_WINDOW_MILLIS} 限制。
 */
public record PendingRequest(UUID player, long createdAtMillis) {

    /** 指令应答的有效期：15 分钟。超过它就只能去界面里点。 */
    public static final long COMMAND_WINDOW_MILLIS = 15L * 60L * 1000L;

    public static final Codec<PendingRequest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(PendingRequest::player),
            Codec.LONG.fieldOf("at").forGetter(PendingRequest::createdAtMillis)
    ).apply(instance, PendingRequest::new));

    /** 存进实体 NBT 用的列表编解码器（{@code applications} / {@code invitations} 两个键共用）。 */
    public static final Codec<List<PendingRequest>> LIST_CODEC = CODEC.listOf();

    /** 记一条"现在发生的"请求。 */
    public static PendingRequest now(UUID player) {
        return new PendingRequest(player, System.currentTimeMillis());
    }

    /** 还在"可以用指令应答"的时间窗内吗。 */
    public boolean withinCommandWindow() {
        return System.currentTimeMillis() - this.createdAtMillis <= COMMAND_WINDOW_MILLIS;
    }
}
