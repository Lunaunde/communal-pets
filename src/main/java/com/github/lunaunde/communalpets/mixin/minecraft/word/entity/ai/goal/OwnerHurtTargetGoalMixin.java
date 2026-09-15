package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.ai.goal;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
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
 * 让宠物"接过战果"扩展到整个 owner 群：<b>主人或任一照护者打了谁，宠物就去打谁</b>。
 * <p>
 * vanilla 的 {@code canUse}（26.2，反汇编核过）只对"主人"这一位做判定：
 * <pre>
 * isTame / isOrderedToSit → getOwner() → ownerLastHurt = owner.getLastHurtMob()
 *   → i = owner.getLastHurtMobTimestamp()
 *   → i != timestamp &amp;&amp; canAttack(ownerLastHurt, DEFAULT) &amp;&amp; tameAnimal.wantsToAttack(ownerLastHurt, owner)
 * </pre>
 * 这里用 cancellable HEAD 注入接管它，把同一套判定对 owner 群里的<b>每一位</b>各问一遍；命中就把
 * {@code ownerLastHurt} 写进 vanilla 的私有字段（vanilla 的 {@code start} 会拿它 {@code setTarget}）。
 * <p>
 * <b>为什么不再用 @Redirect 换掉 getOwner()：</b>Redirect 一次只能返回一个值，没法"逐个成员判定"；
 * 而且它要求 {@code getOwner()} 在每个方法里恰好一处、还要靠字段把选中者从 {@code canUse} 传到 {@code start}，
 * 又脆又绕（原版多一处调用点就会启动崩）。接管 {@code canUse} 之后这些全部不需要，
 * vanilla 的 {@code start} 也<b>完全不用动</b>。
 * <p>
 * <b>为什么去重记账要按成员各记一份：</b>vanilla 用单个 int {@code timestamp} 记账，是因为比较对象永远是同一个主人；
 * 那个值是各人<b>自己的 tickCount</b>（{@code LivingEntity.setLastHurtByMob} 里写的就是 {@code this.tickCount}），
 * 跨成员比就没有意义了 —— 拿 A 的水位去卡 B 的事件会漏判（两人计数器撞上同一个值）或错判。
 * 所以这里改成 {@code Map<UUID,Integer>} 按成员各记一份"已处理到哪个时间戳"，判定条件本身仍照抄 vanilla。
 */
@Mixin(OwnerHurtTargetGoal.class)
public abstract class OwnerHurtTargetGoalMixin extends TargetGoal {

    @Shadow
    @Final
    private TamableAnimal tameAnimal;

    /** vanilla 私有字段：{@code start} 会 {@code setTarget(ownerLastHurt)}，所以必须由我们写。 */
    @Shadow
    private @Nullable LivingEntity ownerLastHurt;

    /** 每个成员"已经处理过的时间戳"，防止同一个事件被反复锁定。 */
    @Unique
    private final Map<UUID, Integer> communalPets$handledHurt = new HashMap<>();

    protected OwnerHurtTargetGoalMixin(final TamableAnimal tameAnimal) {
        super(tameAnimal, false);
    }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void communalPets$canUseForOwnerGroup(final CallbackInfoReturnable<Boolean> cir) {
        // vanilla 的两个早退：坐着的 / 没驯服的宠物连搜索都不做（P1-5 就是这一步省下来的）
        if (!this.tameAnimal.isTame() || this.tameAnimal.isOrderedToSit()) {
            cir.setReturnValue(false);
            return;
        }

        // 逐个成员套 vanilla 那套判定；getOwnerGroup 里主人排在第一位，所以并列时主人优先
        for (LivingEntity member : CommunalPet.getOwnerGroup(this.tameAnimal)) {
            int timestamp = member.getLastHurtMobTimestamp();
            Integer handled = this.communalPets$handledHurt.get(member.getUUID());
            if (handled != null && handled.intValue() == timestamp) {
                continue;   // 这个成员的这次出手已经处理过了
            }
            LivingEntity lastHurt = member.getLastHurtMob();
            if (this.canAttack(lastHurt, TargetingConditions.DEFAULT)
                    && this.tameAnimal.wantsToAttack(lastHurt, member)) {
                this.communalPets$handledHurt.put(member.getUUID(), timestamp);
                this.ownerLastHurt = lastHurt;
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
