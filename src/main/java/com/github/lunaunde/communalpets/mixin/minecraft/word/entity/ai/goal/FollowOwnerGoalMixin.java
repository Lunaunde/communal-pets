package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin extends Goal {
    @Shadow
    @Final
    private TamableAnimal tamable;

    @Inject(method="canUse",at = @At("HEAD"), cancellable = true)
    private void onCanUse(final CallbackInfoReturnable<Boolean> cir)
    {
        if(CommunalPetBehavior.getBehaviorState(this.tamable)!=CommunalPetBehavior.BEHAVIOR_FOLLOW) {
            cir.setReturnValue(false);
        }
    }
}
