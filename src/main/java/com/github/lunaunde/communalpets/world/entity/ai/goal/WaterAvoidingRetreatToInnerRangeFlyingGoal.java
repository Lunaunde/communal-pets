package com.github.lunaunde.communalpets.world.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterAvoidingRetreatToInnerRangeFlyingGoal extends WaterAvoidingRandomFlyingGoal {

    private boolean tryRetreat = false;
    final TamableAnimal tamableAnimal;

    public WaterAvoidingRetreatToInnerRangeFlyingGoal(final TamableAnimal mob, final double speedModifier) {
        super(mob, speedModifier);
        this.tamableAnimal = mob;
    }

    @Override
    public boolean canUse() {
        Vec3 center = ((CommunalPet) this.tamableAnimal).communalPets$getWanderCenter();
        double radius = ((CommunalPet) this.tamableAnimal).communalPets$getWanderRadius();
        double innerRange = ((CommunalPet) this.tamableAnimal).communalPets$getWanderInnerRange();
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
            } else {
                return false;
            }
        } else {
            tryRetreat = true;
        }
        return super.canUse();
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        Vec3 center = ((CommunalPet)this.tamableAnimal).communalPets$getWanderCenter();
        Vec3 toCenter = center.subtract(mob.position()).normalize();
        return AirAndWaterRandomPos.getPos(mob, 16, 4, -2, toCenter.x, toCenter.z, Math.PI / 4);
    }
}
