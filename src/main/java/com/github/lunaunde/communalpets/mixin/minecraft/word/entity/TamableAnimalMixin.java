package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.world.entity.ai.behavior.CommunalPetBehavior;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
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

import static com.github.lunaunde.communalpets.CommunalPets.MOD_ID;

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
    private double wanderRadius = 16;

    @Unique
    private double wanderInnerRange = 0.8;

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

    @Override
    @Unique
    public void communalPets$cycleBehavior(Player player) {
        communalPets$cycleBehavior();
        // mobInteract 在客户端和服务端都会执行，只在服务端发包
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String displayerName = this.getName().getString();
        ServerLevel level = (ServerLevel) this.level();
        switch (behaviorState) {
            case BEHAVIOR_FOLLOW:
                level.sendParticles(
                        ParticleTypes.WAX_ON,
                        this.getX(),
                        this.getEyeY(),
                        this.getZ(),
                        15,
                        0.3,0.2,0.3,
                        0.1
                );
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                   Component.literal(displayerName + "跟随中")
                ));
                break;
            case BEHAVIOR_WANDER:
                level.sendParticles(
                        ParticleTypes.WAX_OFF,
                        this.getX(),
                        this.getEyeY(),
                        this.getZ(),
                        15,
                        0.3,0.2,0.3,
                        0.1
                );
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal(displayerName + "游荡中")
                ));
                break;
            case BEHAVIOR_SIT:
                level.sendParticles(
                        ParticleTypes.SCRAPE,
                        this.getX(),
                        this.getEyeY(),
                        this.getZ(),
                        15,
                        0.3,0.2,0.3,
                        0.1
                );
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal(displayerName + "坐下")
                ));
                break;
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
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

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(final ValueInput input, CallbackInfo ci) {
        if(this.isTame()) {
            String behavior = input.getStringOr("behavior", "sit");
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
                    this.communalPets$setBehaviorState(BEHAVIOR_SIT);
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
