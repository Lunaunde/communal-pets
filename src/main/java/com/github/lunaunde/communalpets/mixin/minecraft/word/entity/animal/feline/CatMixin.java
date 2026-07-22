package com.github.lunaunde.communalpets.mixin.minecraft.word.entity.animal.feline;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.ai.goal.WaterAvoidingRetreatToInnerRangeGoal;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Cat.class)
public abstract class CatMixin extends TamableAnimal {
    @Unique
    private Player lastInteractor;
    @Unique
    private InteractionHand lastInteractorHand;

    protected CatMixin(EntityType<? extends Cat> entityType, Level level) {super(entityType,level);}

    @Inject(method = "registerGoals",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/world/entity/ai/goal/Goal;)V",
                    ordinal = 5,
                    shift = At.Shift.AFTER
            )
    )
    private void onRegisterGoals(final CallbackInfo ci){
        this.goalSelector.addGoal(6,new WaterAvoidingRetreatToInnerRangeGoal(this,1.0));
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    private void capturePlayer(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir){
        this.lastInteractor = player;
    }
    @Redirect(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/feline/Cat;setOrderedToSit(Z)V",
                    ordinal = 0
            )
    )
    private void redirectSetOrderedToSitToCycleBehavior(Cat instance, boolean orderedToSit) {
        if(lastInteractorHand == InteractionHand.MAIN_HAND)
            CommunalPet.cycleBehavior(instance,this.lastInteractor);
    }
}
