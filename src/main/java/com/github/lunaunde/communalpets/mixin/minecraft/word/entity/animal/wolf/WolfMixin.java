package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.animal.wolf;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.menu.PetMenus;
import com.github.lunaunde.communalpets.world.entity.ai.goal.FollowNearestPlayerGoal;
import com.github.lunaunde.communalpets.world.entity.ai.goal.WaterAvoidingRetreatToInnerRangeGoal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Wolf.class)
public abstract class WolfMixin extends TamableAnimal{
    @Unique
    private Player lastInteractor;
    @Unique
    private InteractionHand lastInteractorHand;

    protected WolfMixin(final EntityType<? extends TamableAnimal> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V",
                    ordinal = 5,
                    shift = At.Shift.AFTER
            )
    )
    private void onRegisterGoals(final CallbackInfo ci){
        this.goalSelector.addGoal(6,new FollowNearestPlayerGoal(this,1.0));
        this.goalSelector.addGoal(6,new WaterAvoidingRetreatToInnerRangeGoal(this,1.0));
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void capturePlayer(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir){
        this.lastInteractor = player;
        this.lastInteractorHand = hand;
    }

    /**
     * 蹲下 + 右键 → 打开抚养者界面（表0 / 表0.1 / 表1）。
     * <p>
     * 注入在 HEAD 并直接 cancel：这一下原版逻辑（切行为 / 喂食 / 驯服）都不会跑。
     * <b>cancel 会让同方法上的 {@code @At("RETURN")} 注入失效</b>，所以这里要自己把
     * "本次交互中转"的两个字段清掉，否则宠物会一直强引用这个 Player。
     * <p>
     * 返回值由 {@link PetMenus#tryOpen} 决定（主人给 CLIENT 源、照顾者给 SERVER 源，
     * 原因见那边的注释）；它返回 {@code null} 表示这次不看界面、原版逻辑继续。
     */
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void communalPets$openMenu(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir){
        InteractionResult menuResult = PetMenus.tryOpen(this, player, hand);
        if (menuResult != null) {
            this.lastInteractor = null;
            this.lastInteractorHand = null;
            cir.setReturnValue(menuResult);
        }
    }

    /**
     * 交互一结束就松手：这两个字段只是"本次 mobInteract 内部"的中转，
     * 不清空的话宠物会一直强引用一个可能已下线 / 已卸载的 Player 实体。
     */
    @Inject(method = "mobInteract", at = @At("RETURN"))
    private void clearInteractor(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir){
        this.lastInteractor = null;
        this.lastInteractorHand = null;
    }
    @Redirect(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/wolf/Wolf;setOrderedToSit(Z)V",
                    ordinal = 0
            )
    )
    private void redirectSetOrderedToSitToCycleBehavior(Wolf instance, boolean orderedToSit) {
        if(lastInteractorHand == InteractionHand.MAIN_HAND) {
            CommunalPet.cycleBehavior(instance, this.lastInteractor);
            // 原版客户端在"照顾者"身上预测不出挥手（客户端不认照顾者这个概念），这一下要服务端补
            PetMenus.swingForCaretakerInteraction(instance, this.lastInteractor, this.lastInteractorHand);
        }
    }
}
