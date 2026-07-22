package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.CommunalPets;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
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

import java.util.*;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin extends Animal implements CommunalPet, OwnableEntity {

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

    @Unique
    private final List<Caregiver> caregivers = new ArrayList<>();

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
                        0.3, 0.2, 0.3,
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
                        0.3, 0.2, 0.3,
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
                        0.3, 0.2, 0.3,
                        0.1
                );
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal(displayerName + "坐下")
                ));
                break;
        }
    }

    @Override
    @Unique
    public List<Caregiver> communalPets$getCaregivers() {
        return caregivers;
    }

    @Unique
    private void setOwnerAsCaregiver() {
        caregivers.add(new Caregiver(Objects.requireNonNull(this.getOwnerReference()).getUUID(), "Owner"));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddAdditionalSaveData(final ValueOutput output, CallbackInfo ci) {
        if (this.isTame()) {
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

            ValueOutput.ValueOutputList caregiversData = output.childrenList("caregivers");
            for (Caregiver caregiver : caregivers) {
                ValueOutput entry = caregiversData.addChild();
                entry.putString("uuid", caregiver.getUUID().toString());
                entry.putString("type", caregiver.getType());
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(final ValueInput input, CallbackInfo ci) {
        if (this.isTame()) {
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

            this.caregivers.clear();
            input.childrenList("caregivers").ifPresent(list -> {
                for (ValueInput entry : list) {
                    UUID uuid = UUID.fromString(entry.getStringOr("uuid", ""));
                    String type = entry.getStringOr("type", "undefined");
                    if(type.equals("Owner")) {
                        if(!uuid.equals(Objects.requireNonNull(this.getOwnerReference()).getUUID())){
                            CommunalPets.LOGGER.warn("Owner Caregiver UUID({}) mismatch to Owner UUID({})", uuid, this.getOwnerReference().getUUID());
                        }
                        setOwnerAsCaregiver();
                    }else if(type.equals("Caregiver")) {
                        caregivers.add(new Caregiver(uuid, type));
                    }else{
                        CommunalPets.LOGGER.warn("Caregiver {} Type unknown", uuid);
                    }
                }
            });

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

    @Inject(method = "tame", at = @At("TAIL"))
    private void onTame(final Player player, CallbackInfo ci) {
        setOwnerAsCaregiver();
        CommunalPets.LOGGER.info("Owner id:{}", String.valueOf(player.getId()));
    }

    @Inject(method = "isOwnedBy", at = @At("HEAD"), cancellable = true)
    private void onIsOwnedBy(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CommunalPets.LOGGER.info("entity id:{}", String.valueOf(entity.getId()));
        for (Caregiver caregiver : caregivers) {
            CommunalPets.LOGGER.info("caregiver id:{}", String.valueOf(caregiver.getUUID()));
            if (caregiver.getUUID().equals(entity.getUUID())) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
