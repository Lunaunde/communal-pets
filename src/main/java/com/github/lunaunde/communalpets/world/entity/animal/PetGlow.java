package com.github.lunaunde.communalpets.world.entity.animal;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 假队伍发光：颜色队伍只活在"我们自己的 {@link Scoreboard} + 各客户端内存"里，
 * 服务端的真实 scoreboard 完全不被触碰 —— 不落档、不参与 {@code /team}、
 * 不影响友军 / 碰撞 / 名牌可见性这些真队伍语义。
 * <p>
 * 颜色用原版的 {@link ClientboundSetPlayerTeamPacket} 表达；"亮不亮"则不走
 * {@code setGlowingTag}（那个会把发光位广播给所有追踪者），而是由 {@link #pushGlowFlags}
 * 挂在 {@code ServerTickEvents.END_LEVEL_TICK} 上，每 tick 只给 owner 群伪发一次共享 flag 字节。
 * 两者都只发给这只宠物的 owner 群（{@code isOwnedBy} 已被本模组覆写成 owner + caregivers），
 * 所以非 owner 群既收不到颜色、也收不到发光位 —— 完全看不到。
 * <p>
 * <b>队伍名带宠物 UUID，一只宠物一支队。</b>这样 ADD 包里的"全量成员列表"永远只有这一只宠物，
 * 不会把别人家宠物的颜色泄漏给这个 owner 群；清色也只删自己这一支队。
 * <p>
 * 只发两种队伍包，二者在客户端都不会抛异常：
 * <ul>
 *     <li><b>上色</b>：ADD 包（method 0）。客户端会建队 / 覆盖参数 / 把成员加进队伍。</li>
 *     <li><b>清色</b>：整队删除包（method 1）。客户端对整队删除不做任何成员校验；
 *         对于不认识这个队的客户端只会打一条 warn 就返回，绝不会崩。</li>
 * </ul>
 * 特别注意：<b>永远不要发"移除成员"包（method 4）</b>——那个包在客户端成员状态对不上时会抛
 * {@code IllegalStateException}，直接让客户端崩溃。
 */
public final class PetGlow {

    private PetGlow() {
    }

    /** 只用来承载假队伍，永远不会交给服务端的 ServerScoreboard。 */
    private static final Scoreboard GLOW_SCOREBOARD = new Scoreboard();

    /** 当前正在发光的宠物。只在发光窗口期间有条目（几秒级）。 */
    private static final Map<UUID, Entity> GLOWING = new HashMap<>();

    // ------------------------------------------------------------------
    // 颜色：假队伍，只发给 owner 群
    // ------------------------------------------------------------------

    /** 给宠物上色；如果它正带着别的颜色，会先把旧队收干净。 */
    public static void paint(final Entity pet, final ServerLevel level, final TeamColor color) {
        clear(pet, level);

        PlayerTeam team = teamOf(pet, color);
        GLOW_SCOREBOARD.addPlayerToTeam(pet.getStringUUID(), team);
        sendToOwnerGroup(level, pet, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
    }

    /** 把宠物身上那一支颜色队伍收掉（我们这边和客户端一起清）。 */
    public static void clear(final Entity pet, final ServerLevel level) {
        PlayerTeam team = GLOW_SCOREBOARD.getPlayersTeam(pet.getStringUUID());
        if (team == null) {
            return;
        }

        GLOW_SCOREBOARD.removePlayerFromTeam(pet.getStringUUID());
        GLOW_SCOREBOARD.removePlayerTeam(team);
        sendToOwnerGroup(level, pet, ClientboundSetPlayerTeamPacket.createRemovePacket(team));
    }

    /** 一宠一队的队伍名，颜色只体现在 {@code setColor} 上。 */
    private static PlayerTeam teamOf(final Entity pet, final TeamColor color) {
        String name = CommunalPet.GLOW_TEAM_PREFIX + pet.getStringUUID() + "_" + color.getSerializedName();
        PlayerTeam team = GLOW_SCOREBOARD.getPlayerTeam(name);
        if (team == null) {
            team = GLOW_SCOREBOARD.addPlayerTeam(name);
            team.setColor(Optional.of(color));
        }
        return team;
    }

    // ------------------------------------------------------------------
    // 发光位：每 tick 伪发一次，只给 owner 群
    // ------------------------------------------------------------------

    /** 登记一只正在发光的宠物。 */
    public static void registerGlow(final Entity pet) {
        GLOWING.put(pet.getUUID(), pet);
    }

    /** 把宠物移出发光名单。 */
    public static void unregisterGlow(final Entity pet) {
        GLOWING.remove(pet.getUUID());
    }

    /** 这只宠物现在是否在发光名单里。 */
    public static boolean isGlowing(final Entity pet) {
        return GLOWING.containsKey(pet.getUUID());
    }

    /**
     * 挂在 {@code ServerTickEvents.END_LEVEL_TICK} 上（注入点是 {@code ServerLevel.tick()} 的 TAIL，
     * 也就是在 {@code entityManagement} 之后），所以这里是这一 tick 最后一次写共享 flag 字节的地方：
     * 任何"宠物游泳 / 着火 / 隐身 / 玩家刚开始追踪"造成的权威字节广播，都会被我们这一发覆盖回来。
     */
    public static void pushGlowFlags(final ServerLevel level) {
        if (GLOWING.isEmpty()) {
            return;
        }
        for (Entity pet : List.copyOf(GLOWING.values())) {
            if (pet.isRemoved()) {
                GLOWING.remove(pet.getUUID());
                continue;
            }
            if (pet.level() == level && pet instanceof CommunalPet communalPet) {
                communalPet.communalPets$pushGlowFlag();
            }
        }
    }

    /** 只发给这只宠物的 owner 群（owner + caregivers）。 */
    public static void sendToOwnerGroup(final ServerLevel level, final Entity pet, final Packet<?> packet) {
        // 只遍历本维度的玩家：跨维度的玩家不可能追踪这只宠物，之前每次扫全服属于白跑
        for (ServerPlayer player : level.players()) {
            if (pet instanceof TamableAnimal tamableAnimal && tamableAnimal.isOwnedBy(player)) {
                player.connection.send(packet);
            }
        }
    }
}
