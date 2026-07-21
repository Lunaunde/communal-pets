package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;


import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SitWhenOrderedToGoal.class)
public abstract class SitWhenOrderedToGoalMixin extends Goal {
    @Shadow
    @Final
    private TamableAnimal mob;



    @Inject(method = "canUse",at = @At("HEAD"),cancellable = true)
    private void onCanUse(final CallbackInfoReturnable<Boolean> cir) {
        boolean orderedToSit = this.mob.isOrderedToSit();
        if (!orderedToSit && !this.mob.isTame()) {
            cir.setReturnValue(false);
            return;
        } else if (this.mob.isInWater()) {
            cir.setReturnValue(false);
            return;
        } else if (!this.mob.onGround()) {
            cir.setReturnValue(false);
            return;
        } else if(CommunalPetBehavior.getBehaviorState(this.mob)==CommunalPetBehavior.BEHAVIOR_WANDER){
            cir.setReturnValue(false);
            return;
        }
        else{
            LivingEntity owner = this.mob.getOwner();
            if (owner == null || owner.level() != this.mob.level()) {
                cir.setReturnValue(true);
                return;
            } else {
                cir.setReturnValue((!(this.mob.distanceToSqr(owner) < 144.0) || owner.getLastHurtByMob() == null) && orderedToSit);
                return;
            }
        }
    }
}
