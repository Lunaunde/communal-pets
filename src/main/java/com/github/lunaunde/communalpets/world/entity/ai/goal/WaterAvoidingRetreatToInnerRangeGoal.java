package com.github.lunaunde.communalpets.world.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class WaterAvoidingRetreatToInnerRangeGoal extends RandomStrollGoal {

    private final Vec3 center;
    private final double radius;
    private final double innerRange;
    private boolean tryRetreat = false;
    protected final float probability;

    public WaterAvoidingRetreatToInnerRangeGoal(final TamableAnimal mob, final double speedModifier, final Vec3 center, final double radius) {
        this(mob, speedModifier, center, radius, 0.8);
    }

    public WaterAvoidingRetreatToInnerRangeGoal(final TamableAnimal mob, final double speedModifier, final Vec3 center, final double radius, final double innerRange) {
        this(mob, speedModifier, center, radius, innerRange, 0.001F);
    }

    public WaterAvoidingRetreatToInnerRangeGoal(final TamableAnimal mob, final double speedModifier, final Vec3 center, final double radius, final double innerRange, final float probability) {
        super(mob, speedModifier);
        this.probability = probability;
        this.center = center;
        this.radius = radius;
        this.innerRange = innerRange;
    }

    @Override
    public boolean canUse() {
        if (this.mob instanceof TamableAnimal tamableAnimal) {
            if (CommunalPetBehavior.getBehaviorState(tamableAnimal) != CommunalPetBehavior.BEHAVIOR_WANDER) {
                return false;
            }
            if((center.distanceTo(mob.position())<= radius)){
                if(tryRetreat && mob.distanceToSqr(center)<= radius * radius * innerRange * innerRange) {
                    tryRetreat = false;
                    return false;
                }
            }
            else{
                tryRetreat = true;
            }

        }
        return super.canUse();
    }

    @Override
    protected @Nullable Vec3 getPosition() {
        if (this.mob.isInWater()) {
            Vec3 pos = LandRandomPos.getPosTowards(mob, 15, 7, this.center);
            return pos == null ? super.getPosition() : pos;
        } else {
            return this.mob.getRandom().nextFloat() >= this.probability
                ? LandRandomPos.getPosTowards(this.mob, 10, 7, this.center)
                : super.getPosition();
        }
    }
}
