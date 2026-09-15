package com.github.lunaunde.communalpets.world.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterAvoidingRetreatToInnerRangeGoal extends RandomStrollGoal {

    private boolean tryRetreat = false;
    private final TamableAnimal tamableAnimal;
    protected final float probability;

    public WaterAvoidingRetreatToInnerRangeGoal(final TamableAnimal mob, final double speedModifier) {
        this(mob, speedModifier, 0.001F);
    }

    public WaterAvoidingRetreatToInnerRangeGoal(final TamableAnimal mob, final double speedModifier, final float probability) {
        super(mob, speedModifier);
        this.probability = probability;
        this.tamableAnimal = mob;
    }

    @Override
    public boolean canUse() {
        Vec3 center = ((CommunalPet)this.tamableAnimal).communalPets$getWanderCenter();
        double radius = ((CommunalPet)this.tamableAnimal).communalPets$getWanderRadius();
        double innerRange = ((CommunalPet)this.tamableAnimal).communalPets$getWanderInnerRange();
        if (CommunalPet.getBehaviorState(tamableAnimal) != CommunalPet.BEHAVIOR_WANDER) {
            return false;
        }
        double radiusSqr = radius * radius;
        if (this.mob.distanceToSqr(center) <= radiusSqr) {
            if (tryRetreat) {
                if (this.mob.distanceToSqr(center) <= radiusSqr * innerRange * innerRange) {
                    tryRetreat = false;
                    return false;
                }
            }
            else {
                return false;
            }
        }
        else{
            tryRetreat = true;
        }
        return super.canUse();
    }

    /**
     * 寻路分两遍，做到"优先不走水路，无路可走时才下水"：
     * <ol>
     *     <li>先把水当成墙（{@link PathType#BLOCKED} 的 malus = -1），只找纯陆路；</li>
     *     <li>陆路不通，再把水的代价设为 0（和原版 {@code FollowOwnerGoal} 一样），让它游过去。</li>
     * </ol>
     * 水的代价只影响"算路"，不影响已经算出来的路径，所以算完立刻还原，
     * 不需要像 FollowOwnerGoal 那样在整个 goal 生命周期里改来改去。
     */
    @Override
    public void start() {
        PathNavigation navigation = this.mob.getNavigation();
        BlockPos target = BlockPos.containing(this.wantedX, this.wantedY, this.wantedZ);

        float originalWaterCost = this.tamableAnimal.getPathfindingMalus(PathType.WATER);
        Path path;
        try {
            this.tamableAnimal.setPathfindingMalus(PathType.WATER, PathType.BLOCKED.getMalus());
            path = navigation.createPath(target, 1);

            if (path == null) {
                this.tamableAnimal.setPathfindingMalus(PathType.WATER, 0.0F);
                path = navigation.createPath(target, 1);
            }
        } finally {
            this.tamableAnimal.setPathfindingMalus(PathType.WATER, originalWaterCost);
        }

        navigation.moveTo(path, this.speedModifier);
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        Vec3 center = ((CommunalPet)this.tamableAnimal).communalPets$getWanderCenter();
        if (this.mob.isInWater()) {
            // 水里就往游荡中心方向找一块陆地（对齐 vanilla WaterAvoidingRandomStrollGoal 的意图）；
            // 找不到再退回父类实现。之前这里算出了 pos 却返回 center，属于写错了。
            Vec3 pos = LandRandomPos.getPosTowards(this.mob, 15, 7, center);
            return pos == null ? super.getPosition() : pos;
        } else {
            return this.mob.getRandom().nextFloat() >= this.probability
                ? LandRandomPos.getPosTowards(this.mob, 10, 7, center)
                : center;
        }
    }
}
