package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LandOnOwnersShoulderGoal.class)
public abstract class LandOnOwnerShoulderGoalMixin extends Goal {
    @Shadow
    @Final
    private ShoulderRidingEntity entity;

    @Inject(method = "canUse",at = @At("HEAD"),cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir)
    {
        if(CommunalPetBehavior.getBehaviorState(this.entity)!=CommunalPetBehavior.BEHAVIOR_FOLLOW) {
            cir.setReturnValue(false);
        }
    }
}
