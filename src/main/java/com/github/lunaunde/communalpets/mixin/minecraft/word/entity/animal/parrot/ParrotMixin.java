package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.animal.parrot;

import com.github.lunaunde.communalpets.CommunalPets;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.ai.goal.WaterAvoidingRetreatToInnerRangeFlyingGoal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.parrot.ShoulderRidingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Parrot.class)
public abstract class ParrotMixin extends ShoulderRidingEntity {
    @Unique
    private Player lastInteractor;
    @Unique
    private InteractionHand lastInteractorHand;

    protected ParrotMixin(final EntityType<? extends ShoulderRidingEntity> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void onRegisterGoals(final CallbackInfo ci){
        this.goalSelector.addGoal(2,new WaterAvoidingRetreatToInnerRangeFlyingGoal(this,1.0));
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void capturePlayer(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir){
        CommunalPets.LOGGER.info("hand: {}", hand);
        this.lastInteractor = player;
        this.lastInteractorHand = hand;
    }
    @Redirect(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/parrot/Parrot;setOrderedToSit(Z)V",
                    ordinal = 0
            )
    )
    private void redirectSetOrderedToSitToCycleBehavior(Parrot instance, boolean orderedToSit) {
        if(lastInteractorHand == InteractionHand.MAIN_HAND)
            CommunalPet.cycleBehavior(instance,this.lastInteractor);
    }
}
