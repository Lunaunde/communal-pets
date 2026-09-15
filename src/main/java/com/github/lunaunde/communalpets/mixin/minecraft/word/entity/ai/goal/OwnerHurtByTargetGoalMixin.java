package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 让宠物"护主"扩展到整个 owner 群：<b>谁打了主人或任一照护者，宠物就去打谁</b>。
 * <p>
 * 和 {@link OwnerHurtTargetGoalMixin} 同一套做法，只是数据源换成
 * {@code getLastHurtByMob()} / {@code getLastHurtByMobTimestamp()}（谁打了它），
 * 写回的 vanilla 私有字段是 {@code ownerLastHurtBy}。判定条件同样照抄 26.2 的
 * {@code OwnerHurtByTargetGoal.canUse}（反汇编核过）：
 * <pre>
 * isTame / isOrderedToSit → i != timestamp &amp;&amp; canAttack(lastHurtBy, DEFAULT) &amp;&amp; wantsToAttack(lastHurtBy, member)
 * </pre>
 * 去重按成员各记一份（原因见 {@link OwnerHurtTargetGoalMixin} 的类注释：那个时间戳是各人自己的 tickCount）。
 */
@Mixin(OwnerHurtByTargetGoal.class)
public abstract class OwnerHurtByTargetGoalMixin extends TargetGoal {

    @Shadow
    @Final
    private TamableAnimal tameAnimal;

    /** vanilla 私有字段：{@code start} 会 {@code setTarget(ownerLastHurtBy)}。 */
    @Shadow
    private @Nullable LivingEntity ownerLastHurtBy;

    /** 每个成员"已经处理过的时间戳"，防止同一个事件被反复锁定。 */
    @Unique
    private final Map<UUID, Integer> communalPets$handledHurtBy = new HashMap<>();

    protected OwnerHurtByTargetGoalMixin(final TamableAnimal tameAnimal) {
        super(tameAnimal, false);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void communalPets$canUseForOwnerGroup(final CallbackInfoReturnable<Boolean> cir) {
        if (!this.tameAnimal.isTame() || this.tameAnimal.isOrderedToSit()) {
            cir.setReturnValue(false);
            return;
        }

        for (LivingEntity member : CommunalPet.getOwnerGroup(this.tameAnimal)) {
            int timestamp = member.getLastHurtByMobTimestamp();
            Integer handled = this.communalPets$handledHurtBy.get(member.getUUID());
            if (handled != null && handled.intValue() == timestamp) {
                continue;   // 这个成员的这次被打已经处理过了
            }
            LivingEntity lastHurtBy = member.getLastHurtByMob();
            if (this.canAttack(lastHurtBy, TargetingConditions.DEFAULT)
                    && this.tameAnimal.wantsToAttack(lastHurtBy, member)) {
                this.communalPets$handledHurtBy.put(member.getUUID(), timestamp);
                this.ownerLastHurtBy = lastHurtBy;
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
