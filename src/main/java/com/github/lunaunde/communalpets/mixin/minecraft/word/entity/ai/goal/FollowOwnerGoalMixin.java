package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin extends Goal {
    @Shadow
    @Final
    private TamableAnimal tamable;

    @Inject(method="canUse",at = @At("HEAD"), cancellable = true)
    private void onCanUse(final CallbackInfoReturnable<Boolean> cir)
    {
        // 只有"跟随主人/指挥者"状态下才用这个 goal；
        // "跟随最近者"交给 FollowNearestPlayerGoal（两者都是 MOVE goal，靠状态互斥，不会打架）
        if(CommunalPet.getBehaviorState(this.tamable)!= CommunalPet.BEHAVIOR_FOLLOW_COMMANDER) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 把"主人"换成"指挥者"：最后一次右键指挥这只宠物的玩家。
     * <p>
     * {@code canUse} 里这一句是 goal 内部 {@code this.owner} 的<b>唯一</b>来源，
     * 所以只重定向它，{@code canContinueToUse} / {@code tick} 全都会跟着用指挥者。
     * 还没被指挥过（例如拆状态之前的老存档）时退回原版主人，避免宠物突然不跟人。
     * <p>
     * 目标字符串必须是字节码里的实际调用形式：{@code invokevirtual TamableAnimal.getOwner()}（用 javap 核过）。
     */
    @Redirect(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/TamableAnimal;getOwner()Lnet/minecraft/world/entity/LivingEntity;"
            )
    )
    private LivingEntity onGetFollowTarget(final TamableAnimal tamable) {
        LivingEntity commander = CommunalPet.getCommander(tamable);
        return commander != null ? commander : tamable.getOwner();
    }

    /**
     * commander 模式下关掉原版的"离太远就传送"。
     * <p>
     * 因为 {@code TamableAnimal#tryToTeleportToOwner} 内部会再取一次 {@code getOwner()}，
     * 传送目标写死是主人：跟着指挥者的时候会把宠物传到主人那边去。
     * 想要"离指挥者太远就传送到指挥者身边"，得自己写一段传送逻辑。
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/TamableAnimal;shouldTryTeleportToOwner()Z"
            )
    )
    private boolean onShouldTryTeleport(final TamableAnimal tamable) {
        return false;
    }
}
