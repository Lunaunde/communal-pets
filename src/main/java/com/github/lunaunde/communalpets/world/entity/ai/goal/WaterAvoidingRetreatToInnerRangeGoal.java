package com.github.lunaunde.communalpets.world.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import static com.github.lunaunde.communalpets.CommunalPets.MOD_ID;

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
        Vec3 center = ((CommunalPetBehavior)this.tamableAnimal).communalPets$getWanderCenter();
        double radius = ((CommunalPetBehavior)this.tamableAnimal).communalPets$getWanderRadius();
        double innerRange = ((CommunalPetBehavior)this.tamableAnimal).communalPets$getWanderInnerRange();
        if (CommunalPetBehavior.getBehaviorState(tamableAnimal) != CommunalPetBehavior.BEHAVIOR_WANDER) {
            return false;
        }
        double radiusSqr = radius * radius;
        if((center.distanceToSqr(mob.position())<= radiusSqr)){
            if(tryRetreat) {
                if(mob.distanceToSqr(center)<= radiusSqr * innerRange * innerRange) {
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

    @Override
    protected @Nullable Vec3 getPosition() {
        Vec3 center = ((CommunalPetBehavior)this.tamableAnimal).communalPets$getWanderCenter();
        if (this.mob.isInWater()) {
            Vec3 pos = LandRandomPos.getPosTowards(mob, 15, 7, center);
            return pos == null ? super.getPosition() : center;
        } else {
            return this.mob.getRandom().nextFloat() >= this.probability
                ? LandRandomPos.getPosTowards(this.mob, 10, 7, center)
                : center;
        }
    }
}
