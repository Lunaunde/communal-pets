package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin extends Animal implements CommunalPetBehavior {

    @Shadow
    private boolean orderedToSit;

    @Shadow
    public abstract boolean isTame();

    @Unique
    private int behaviorState = BEHAVIOR_FOLLOW;

    @Unique
    private Vec3 wanderCenter;

    @Unique
    private double wanderRadius;

    @Unique
    private double wanderInnerRange;

    protected TamableAnimalMixin(final EntityType<? extends Animal> type, final Level level) {
        super(type, level);
    }

    @Override
    @Unique
    public int communalPets$getBehaviorState() {
        return behaviorState;
    }

    @Override
    @Unique
    public Vec3 communalPets$getWanderCenter() {
        if (wanderCenter == null)
            wanderCenter = this.position();
        return wanderCenter;
    }

    @Override
    @Unique
    public double communalPets$getWanderRadius() {
        return wanderRadius;
    }

    @Override
    @Unique
    public double communalPets$getWanderInnerRange() {
        return wanderInnerRange;
    }

    @Override
    @Unique
    public void communalPets$setBehaviorState(int state) {
        behaviorState = state;
        switch (state) {
            case BEHAVIOR_WANDER:
                this.wanderCenter = this.position();
            case BEHAVIOR_FOLLOW:
                this.orderedToSit = false;
                break;
            case BEHAVIOR_SIT:
                this.orderedToSit = true;
                break;
        }
    }

    @Override
    @Unique
    public void communalPets$cycleBehavior() {
        switch (behaviorState) {
            case BEHAVIOR_FOLLOW:
                this.communalPets$setBehaviorState(BEHAVIOR_WANDER);
                break;
            case BEHAVIOR_WANDER:
                this.communalPets$setBehaviorState(BEHAVIOR_SIT);
                this.wanderCenter = this.position();
                break;
            case BEHAVIOR_SIT:
                this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW);
                break;
            default:
                this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW);
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void onAddAdditionalSaveData(final ValueOutput output, CallbackInfo ci) {
        if(this.isTame()) {
            String behavior = switch (behaviorState) {
                case BEHAVIOR_FOLLOW -> "follow";
                case BEHAVIOR_WANDER -> "wander";
                case BEHAVIOR_SIT -> "sit";
                default -> "follow";
            };
            output.putString("behavior", behavior);
            output.store("wander_center", Vec3.CODEC, communalPets$getWanderCenter());
            output.putDouble("wander_radius", wanderRadius);
            output.putDouble("wander_inner_range", wanderInnerRange);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
    private void onReadAdditionalSaveData(final ValueInput input, CallbackInfo ci) {
        if(this.isTame()) {
            String behavior = String.valueOf(input.getString("behavior"));
            switch (behavior) {
                case "follow":
                    this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW);
                    break;
                case "wander":
                    this.communalPets$setBehaviorState(BEHAVIOR_WANDER);
                    break;
                case "sit":
                    this.communalPets$setBehaviorState(BEHAVIOR_SIT);
                    break;
                default:
                    this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW);
            }
            Optional<Vec3> wanderCenter = input.read("wander_center", Vec3.CODEC);
            this.wanderCenter = wanderCenter.orElse(this.position());
            this.wanderRadius = input.getDoubleOr("wander_radius", 32);
            this.wanderInnerRange = input.getDoubleOr("wander_inner_range", 0.8);
        }
    }

    @Inject(method = "setOrderedToSit(Z)V", at = @At("HEAD"))
    private void onSetOrderedToSit(boolean orderedToSit, CallbackInfo ci) {
        if (orderedToSit) {
            this.communalPets$setBehaviorState(BEHAVIOR_SIT);
        } else {
            this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW);
        }
    }
}
