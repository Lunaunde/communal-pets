package com.github.lunaunde.communalpets.world.entity.animal;

import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Interface for accessing the communal pets behavior state on {@link TamableAnimal}.
 * <p>
 * The {@code TamableAnimalMixin} implements this interface at runtime via Mixin.
 * Use {@link #getBehaviorState(TamableAnimal)} and
 * {@link #setBehaviorState(TamableAnimal, int)} for compile-time safe access.
 */
public interface CommunalPet {

    int BEHAVIOR_FOLLOW_COMMANDER = 0;
    int BEHAVIOR_FOLLOW_NEAREST = 1;
    int BEHAVIOR_WANDER = 2;
    int BEHAVIOR_SIT = 3;
    int GLOW_DURATION_TICKS = 30;

    /** 找"最近的 owner 群成员"时的搜索半径（格）；{@code FollowNearestPlayerGoal} 和落肩逻辑共用。 */
    double OWNER_GROUP_SEARCH_RADIUS = 32.0;

    /** 是否属于"跟随"类状态（肩上的鹦鹉、坐姿判定等按它处理）。 */
    static boolean isFollowing(int behaviorState) {
        return behaviorState == BEHAVIOR_FOLLOW_COMMANDER || behaviorState == BEHAVIOR_FOLLOW_NEAREST;
    }

    /**
     * 假队伍名字前缀。带冒号是为了让服务端真实队伍永远撞不到这个名字
     * （原版 {@code /team} 的队名不允许冒号，而队伍包本身不校验名字）。
     */
    String GLOW_TEAM_PREFIX = "communalpets:glow_";

    /**
     * 行为状态 -> 发光颜色：跟随主人=绿，跟随最近者=蓝，游荡=黄，坐下=红。
     * <p>
     * 纯服务端方案下，原版客户端只认计分板队伍颜色这一个颜色来源
     * （{@code Entity#getTeamColor()} 读的是客户端本地 scoreboard），
     * 所以颜色只能是 {@link TeamColor} 的 16 种之一。
     */
    static TeamColor glowColorOf(int behaviorState) {
        return switch (behaviorState) {
            case BEHAVIOR_FOLLOW_NEAREST -> TeamColor.BLUE;
            case BEHAVIOR_WANDER -> TeamColor.YELLOW;
            case BEHAVIOR_SIT -> TeamColor.RED;
            default -> TeamColor.GREEN;
        };
    }

    /**
     * 行为状态 -> 语言键（动作栏和指令共用同一份映射）。
     */
    static String behaviorKeyOf(int behaviorState) {
        return switch (behaviorState) {
            case BEHAVIOR_FOLLOW_NEAREST -> "communal-pets.behavior.follow_nearest";
            case BEHAVIOR_WANDER -> "communal-pets.behavior.wander";
            case BEHAVIOR_SIT -> "communal-pets.behavior.sit";
            default -> "communal-pets.behavior.follow_commander";
        };
    }

    /**
     * 和发光色一致的文本颜色，用来给动作栏里的状态词上色。
     * 直接从 {@link #glowColorOf} 推导，所以两边永远不会不一致。
     */
    static TextColor glowTextColorOf(int behaviorState) {
        return TextColor.fromRgb(glowColorOf(behaviorState).rgb());
    }

    int communalPets$getBehaviorState();

    /** 由 MobMixin 每 tick 调用一次，用来把发光倒计时走完。 */
    void communalPets$tickGlow();

    /** 由 EndLevelTick 每 tick 调用一次：只给 owner 群伪发"发光位=1"（服务端自己始终没有发光）。 */
    void communalPets$pushGlowFlag();

    Vec3 communalPets$getWanderCenter();

    /** 设置游荡中心（/communalpets &lt;宠物&gt; wander_center set &lt;pos&gt; 用它）。 */
    void communalPets$setWanderCenter(Vec3 center);

    double communalPets$getWanderRadius();
    double communalPets$getWanderInnerRange();

    void communalPets$setBehaviorState(int state);

    void communalPets$cycleBehavior();
    void communalPets$cycleBehavior(Player player);

    /**
     * 照护者的<b>实体视图</b>（只读）：把 UUID 解析成实体，只包含当前能解析到的（在线 / 已加载）照护者，
     * 离线的会被跳过。
     * <p>
     * <b>不要往这个列表里写</b> —— 它是每次调用新建的派生列表。增删照护者请用
     * {@link #addCaregiver}/{@link #removeCaregiver}（内部走 UUID 存储），
     * 因为存储以 UUID 为准（要落档、玩家会离线）。
     * <p>
     * <b>主人不在这个列表里</b>：主人由原版的 {@code TamableAnimal} 自己存
     * （同步字段 {@code DATA_OWNERUUID_ID} + 存档键 {@code Owner}），
     * 判断"是不是自己人"用 {@code TamableAnimal#isOwnedBy}
     * —— 它已经被本模组改成"原版主人 ∪ 照护者"。
     */
    List<LivingEntity> communalPets$getCaregivers();

    /**
     * 照护者 UUID 的<b>只读快照</b>（存储本体的副本）：查询、落档都用它，
     * 定位相当于原版的 {@code OwnableEntity#getOwnerReference()}。
     * <p>
     * 增删请走 {@link #communalPets$addCaregiver(UUID)} / {@link #communalPets$removeCaregiver(UUID)}，
     * 别再对返回的列表做增删（它只是副本，改了不会生效）。
     */
    List<UUID> communalPets$getCaregiverUUIDs();

    /** 新增照护者（按 UUID 去重）。 */
    void communalPets$addCaregiver(UUID id);

    /** 移除照护者，返回是否真的移除了。 */
    boolean communalPets$removeCaregiver(UUID id);

    /**
     * "指挥者"：最后一次右键指挥这只宠物的玩家。{@link #BEHAVIOR_FOLLOW_COMMANDER} 跟随的就是他。
     * <p>
     * 还没被指挥过（比如拆状态之前的老存档）时返回 null，此时跟随逻辑会退回原版主人，
     * 避免老宠物突然不跟人。
     */
    @Nullable LivingEntity communalPets$getCommander();

    void communalPets$setCommander(@Nullable LivingEntity commander);

    /**
     * Safe accessor — casts through Object because the interface is injected at
     * runtime by Mixin and is not visible to javac on the vanilla class.
     */
    static int getBehaviorState(TamableAnimal animal) {
        return ((CommunalPet) animal).communalPets$getBehaviorState();
    }

    static void setBehaviorState(TamableAnimal animal, int state) {
        ((CommunalPet) animal).communalPets$setBehaviorState(state);
    }



    static void cycleBehavior(TamableAnimal animal) {
        ((CommunalPet) animal).communalPets$cycleBehavior();
    }
    static void cycleBehavior(TamableAnimal animal, Player player) {((CommunalPet) animal).communalPets$cycleBehavior(player);}

    static Vec3 getWanderCenter(TamableAnimal animal) {
        return ((CommunalPet) animal).communalPets$getWanderCenter();
    }

    static void setWanderCenter(TamableAnimal animal, Vec3 center) {
        ((CommunalPet) animal).communalPets$setWanderCenter(center);
    }

    static List<LivingEntity> getCaregivers(TamableAnimal animal) { return ((CommunalPet) animal).communalPets$getCaregivers(); }

    static List<UUID> getCaregiverUUIDs(TamableAnimal animal) {
        return ((CommunalPet) animal).communalPets$getCaregiverUUIDs();
    }

    /**
     * owner 群 = 主人 + 在线照护者。保护类 goal（{@code OwnerHurtTargetGoal} /
     * {@code OwnerHurtByTargetGoal}）用它挑"该保护谁"。
     */
    static List<LivingEntity> getOwnerGroup(TamableAnimal animal) {
        List<LivingEntity> group = new ArrayList<>();
        LivingEntity owner = animal.getOwner();
        if (owner != null) {
            group.add(owner);
        }
        group.addAll(getCaregivers(animal));
        return group;
    }

    /**
     * owner 群里离宠物最近的在线玩家；找不到就返回 null。
     * <p>
     * "是不是自己人"用 {@code isOwnedBy} 判断 —— 它已经被本模组改成"原版主人 ∪ 照护者"。
     */
    static @Nullable Player nearestOwnerGroupPlayer(TamableAnimal animal, double radius) {
        return animal.level()
                .getEntitiesOfClass(Player.class, animal.getBoundingBox().inflate(radius),
                        player -> player.isAlive() && !player.isSpectator() && animal.isOwnedBy(player))
                .stream()
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
    }

    /**
     * 当前"跟随对象"：FOLLOW_COMMANDER → 指挥者（没有则退回原版主人）；
     * FOLLOW_NEAREST → owner 群里最近的在线玩家；其它状态 → null。
     * <p>
     * 给那些"原版写死了主人"的地方用（例如鹦鹉落肩），让它们跟着当前跟随对象走。
     */
    static @Nullable LivingEntity currentFollowTarget(TamableAnimal animal) {
        return switch (getBehaviorState(animal)) {
            case BEHAVIOR_FOLLOW_COMMANDER -> {
                LivingEntity commander = getCommander(animal);
                yield commander != null ? commander : animal.getOwner();
            }
            case BEHAVIOR_FOLLOW_NEAREST -> nearestOwnerGroupPlayer(animal, OWNER_GROUP_SEARCH_RADIUS);
            default -> null;
        };
    }

    static @Nullable LivingEntity getCommander(TamableAnimal animal) {
        return ((CommunalPet) animal).communalPets$getCommander();
    }

    static void setCommander(TamableAnimal animal, @Nullable LivingEntity commander) {
        ((CommunalPet) animal).communalPets$setCommander(commander);
    }

    static void addCaregiver(TamableAnimal animal, LivingEntity newCaregiver){
        // 走接口的写入口（内部操作 UUID 存储本体）；getCaregiverUUIDs() 只是只读快照
        ((CommunalPet) animal).communalPets$addCaregiver(newCaregiver.getUUID());
    }

    /** 移除额外的照护者。主人不受影响：主人身份来自原版的 Owner 字段，不靠这个列表。 */
    static boolean removeCaregiver(TamableAnimal animal, UUID uuid){
        return ((CommunalPet) animal).communalPets$removeCaregiver(uuid);
    }
}
