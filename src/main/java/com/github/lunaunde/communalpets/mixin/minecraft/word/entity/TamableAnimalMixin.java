package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.CommunalPets;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.animal.PetGlow;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    /** 剩余发光 tick，0 表示当前没有在发光。 */
    @Unique
    private int glowTicks;

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
        // mobInteract 在客户端和服务端都会执行；发光只由服务端决定，
        // 客户端只是照着同步过去的队伍颜色渲染描边，不需要任何客户端代码。
        if (this.level().isClientSide()) {
            return;
        }
        this.communalPets$startGlow(this.behaviorState);
    }

    @Override
    @Unique
    public void communalPets$tickGlow() {
        if (this.level().isClientSide() || this.glowTicks <= 0) {
            return;
        }
        if (--this.glowTicks == 0) {
            this.communalPets$stopGlow();
        }
    }

    /**
     * 开始发光：给宠物染上假队伍的颜色（只发 owner 群），并登记进发光名单。
     * <p>
     * 这里【不再】调用 {@code setGlowingTag(true)}：服务端始终保持"没有发光"，
     * 于是 {@code ServerEntity} 不会把发光位广播给所有追踪者。真正让 owner 群看到描边的，
     * 是 {@link PetGlow#pushGlowFlags} 挂在 {@code ServerTickEvents.END_LEVEL_TICK} 上
     * 每 tick 伪发一次的共享 flag 字节 —— 那个时机在 {@code entityManagement} 之后，
     * 保证是本 tick 最后一个写这个字节的人。
     */
    @Unique
    private void communalPets$startGlow(int behaviorState) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        PetGlow.paint(this, serverLevel, CommunalPet.glowColorOf(behaviorState));
        PetGlow.registerGlow(this);
        this.glowTicks = CommunalPet.GLOW_DURATION_TICKS;
    }

    /** 结束发光：收回颜色、退出发光名单，并把服务端的真实字节推回 owner 群（客户端发光位归零）。 */
    @Unique
    private void communalPets$stopGlow() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        PetGlow.clear(this, serverLevel);
        PetGlow.unregisterGlow(this);
        // 用真实字节而不是强制清零：宠物真带着发光药水效果时不会被我们误关
        this.communalPets$pushServerFlags();
    }

    /** 由 {@link PetGlow#pushGlowFlags} 每 tick 调用：只给 owner 群伪发"发光位=1"。 */
    @Override
    @Unique
    public void communalPets$pushGlowFlag() {
        this.communalPets$sendSharedFlags(
                (byte) (this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) | 1 << Entity.FLAG_GLOWING));
    }

    /** 把服务端的真实共享 flag 字节推给 owner 群。 */
    @Unique
    private void communalPets$pushServerFlags() {
        this.communalPets$sendSharedFlags(this.entityData.get(Entity.DATA_SHARED_FLAGS_ID));
    }

    /**
     * 只给 owner 群发一次共享 flag 字节。
     * <p>
     * 客户端对 {@code DATA_SHARED_FLAGS_ID} 是整字节替换，所以伪造时必须在<b>当前真实字节</b>上
     * 只 OR 发光位；只写一个 {@code 1 << 6} 会把隐身 / 着火 / 游泳这些位一起清掉。
     */
    @Unique
    private void communalPets$sendSharedFlags(final byte flags) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        PetGlow.sendToOwnerGroup(serverLevel, this, new ClientboundSetEntityDataPacket(
                this.getId(),
                List.of(SynchedEntityData.DataValue.create(Entity.DATA_SHARED_FLAGS_ID, flags))
        ));
    }

    /** 发光中途死掉也要收回来，免得客户端上永远留着一条摘不掉的颜色。 */
    @Inject(method = "die", at = @At("HEAD"))
    private void communalPets$stopGlowOnDeath(final DamageSource source, final CallbackInfo ci) {
        if (PetGlow.isGlowing(this)) {
            this.glowTicks = 0;
            this.communalPets$stopGlow();
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

        // 重新加载出来的宠物一律不算在发光中：清掉客户端上可能残留的颜色、退出发光名单。
        // （PetGlow.clear 只在宠物确实还在假队伍里时才发包，平时什么都不做。）
        if (!this.level().isClientSide()) {
            this.glowTicks = 0;
            PetGlow.unregisterGlow(this);
            if (this.level() instanceof ServerLevel serverLevel) {
                PetGlow.clear(this, serverLevel);
            }
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
    }

    @Inject(method = "isOwnedBy", at = @At("HEAD"), cancellable = true)
    private void onIsOwnedBy(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        for (Caregiver caregiver : caregivers) {
            if (caregiver.getUUID().equals(entity.getUUID())) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
