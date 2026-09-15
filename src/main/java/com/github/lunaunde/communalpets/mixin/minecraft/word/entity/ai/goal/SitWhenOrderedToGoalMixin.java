package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;


import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
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

    /**
     * 只覆盖与 vanilla 的差异：游荡状态下不用这个 goal。
     * <p>
     * 之前这里把 vanilla 的整段判定（{@code !orderedToSit && !isTame} / 水 / 落地 / 距离与 lastHurtBy）
     * 抄了一遍 —— 逐指令比对虽然等价，但原版一改判定这里就会静默漂移，
     * 所以改成 RETURN 注入：只把 vanilla 已经算出来的 {@code true} 改成 {@code false}。
     */
    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void onCanUse(final CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && CommunalPet.getBehaviorState(this.mob) == CommunalPet.BEHAVIOR_WANDER) {
            cir.setReturnValue(false);
        }
    }
}
