package com.github.lunaunde.communalpets.world.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * {@link CommunalPet#BEHAVIOR_FOLLOW_NEAREST}：跟随"owner 群（主人 + 照顾者）里离我最近的那个人"。
 * <p>
 * 结构和原版 {@code FollowOwnerGoal} 基本一致，区别只有两点：
 * <ol>
 *     <li>目标不是固定的主人，而是每次重新选一次"最近的可跟随玩家"；</li>
 *     <li>只在 {@code FOLLOW_NEAREST} 状态下生效（原版那个被限定在 {@code FOLLOW_COMMANDER}，
 *         两者都是 MOVE goal，靠状态互斥，不会打架）。</li>
 * </ol>
 * 水里寻路沿用原版 {@code FollowOwnerGoal} 的做法：跟随时把 {@link PathType#WATER} 的代价清零，
 * 停止时还原。想要"尽量不湿脚"可以照 {@code WaterAvoidingRetreatToInnerRangeGoal} 那样做两遍寻路。
 * <p>
 * 没有做"离太远就传送"（原版的 {@code tryToTeleportToOwner} 绑死在主人身上）。
 */
public class FollowNearestPlayerGoal extends Goal {

    /** 只在这么大的范围内找跟随目标（格）。 */
    private static final double SEARCH_RADIUS = CommunalPet.OWNER_GROUP_SEARCH_RADIUS;
    /** 超过这个距离才开始追（避免宠物贴着人来回抖）。 */
    private static final float START_DISTANCE = 10.0F;
    /** 近到这个距离就停下。 */
    private static final float STOP_DISTANCE = 3.0F;

    private final TamableAnimal pet;
    private final double speedModifier;
    private final PathNavigation navigation;

    private @Nullable Player target;
    private int timeToRecalcPath;
    private float oldWaterCost;

    public FollowNearestPlayerGoal(final TamableAnimal pet, final double speedModifier) {
        this.pet = pet;
        this.speedModifier = speedModifier;
        this.navigation = pet.getNavigation();
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));

        if (!(pet.getNavigation() instanceof GroundPathNavigation)
                && !(pet.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for FollowNearestPlayerGoal");
        }
    }

    @Override
    public boolean canUse() {
        if (CommunalPet.getBehaviorState(this.pet) != CommunalPet.BEHAVIOR_FOLLOW_NEAREST) {
            return false;
        }
        if (!(this.pet.level() instanceof ServerLevel)) {
            return false;   // AI 本来就只在服务端跑，这里只是保险
        }

        this.target = findNearest();
        return this.target != null
                && this.pet.distanceToSqr(this.target) > START_DISTANCE * START_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null) {
            return false;
        }
        if (CommunalPet.getBehaviorState(this.pet) != CommunalPet.BEHAVIOR_FOLLOW_NEAREST) {
            return false;   // 中途被右键切成别的状态
        }
        if (!this.target.isAlive() || this.target.isSpectator()) {
            return false;
        }
        if (!this.pet.isOwnedBy(this.target)) {
            return false;   // 目标被移出照顾者名单，或者已经不是主人了
        }
        return !this.navigation.isDone()
                && this.pet.distanceToSqr(this.target) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        // 和原版 FollowOwnerGoal 一样：跟随时不躲水
        this.oldWaterCost = this.pet.getPathfindingMalus(PathType.WATER);
        this.pet.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.target = null;
        this.navigation.stop();
        this.pet.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.target == null) {
            return;
        }
        this.pet.getLookControl().setLookAt(this.target, 10.0F, this.pet.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            this.navigation.moveTo(this.target, this.speedModifier);
        }
    }

    /** 在 owner 群里挑最近的一个（判定只有一份，见 {@link CommunalPet#nearestOwnerGroupPlayer}）。 */
    private @Nullable Player findNearest() {
        return CommunalPet.nearestOwnerGroupPlayer(this.pet, SEARCH_RADIUS);
    }
}
