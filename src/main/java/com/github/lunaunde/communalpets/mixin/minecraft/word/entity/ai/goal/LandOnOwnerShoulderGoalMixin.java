package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LandOnOwnersShoulderGoal.class)
public abstract class LandOnOwnerShoulderGoalMixin extends Goal {
    @Shadow
    @Final
    private ShoulderRidingEntity entity;

    @Inject(method = "canUse",at = @At("HEAD"),cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir)
    {
        // 两种跟随状态下都可以落到主人肩上（原版这个 goal 只服务主人）
        if(!CommunalPet.isFollowing(CommunalPet.getBehaviorState(this.entity))) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 原版这个 goal 只会落到"原版主人"肩上（{@code getOwner()} 在 {@code canUse} / {@code tick} 里
     * 各一处，反汇编核过）。这里把这两处换成"当前跟随对象"，于是 FOLLOW_NEAREST 时
     * 它会落到正在跟随的那位照顾者肩上，而不是永远飞向原版主人。
     * <p>
     * 解析不出来时（例如 32 格内没有 owner 群玩家）退回原版主人 —— 保持"至少还能落到主人肩上"，
     * 也保证 {@code tick} 不会吃到 null。
     */
    @Redirect(
            method = "canUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/parrot/ShoulderRidingEntity;getOwner()Lnet/minecraft/world/entity/LivingEntity;"
            )
    )
    private LivingEntity onGetShoulderTargetInCanUse(final ShoulderRidingEntity entity) {
        return this.communalPets$shoulderTarget();
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/parrot/ShoulderRidingEntity;getOwner()Lnet/minecraft/world/entity/LivingEntity;"
            )
    )
    private LivingEntity onGetShoulderTargetInTick(final ShoulderRidingEntity entity) {
        return this.communalPets$shoulderTarget();
    }

    /** 当前跟随对象，解析不出来就退回原版主人。 */
    @Unique
    private @Nullable LivingEntity communalPets$shoulderTarget() {
        LivingEntity target = CommunalPet.currentFollowTarget(this.entity);
        return target != null ? target : this.entity.getOwner();
    }
}
