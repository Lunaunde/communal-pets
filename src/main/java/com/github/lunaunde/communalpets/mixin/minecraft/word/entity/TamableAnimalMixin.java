package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.Messages;
import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import com.github.lunaunde.communalpets.world.entity.animal.PetGlow;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
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

import org.jspecify.annotations.Nullable;

import java.util.*;

@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin extends Animal implements CommunalPet, OwnableEntity {

    @Shadow
    private boolean orderedToSit;

    @Shadow
    public abstract boolean isTame();

    @Unique
    private int behaviorState = BEHAVIOR_FOLLOW_COMMANDER;

    @Unique
    private Vec3 wanderCenter;

    @Unique
    private double wanderRadius = 16;

    @Unique
    private double wanderInnerRange = 0.8;

    @Unique
    private final List<UUID> caregiverUUIDs = new ArrayList<>();

    /**
     * "指挥者"：最后一次右键指挥这只宠物的玩家（{@code follow_commander} 跟随的目标）。
     * 用 {@link EntityReference} 存，和原版存主人的方式一致：实体活着时缓存实体，否则只留 UUID。
     */
    @Unique
    private EntityReference<LivingEntity> commander;

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
    public void communalPets$setWanderCenter(Vec3 center) {
        this.wanderCenter = center;
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
                // 进入游荡前先把中心定在当前位置，然后落进下面的"非坐姿"分支
                this.wanderCenter = this.position();
            case BEHAVIOR_FOLLOW_COMMANDER, BEHAVIOR_FOLLOW_NEAREST:
                this.orderedToSit = false;
                break;
            case BEHAVIOR_SIT:
                this.orderedToSit = true;
                break;
            default:
                break;
        }
    }

    @Override
    @Unique
    public void communalPets$cycleBehavior() {
        // 循环顺序：跟随主人 → 跟随最近者 → 游荡 → 坐下 → 回到跟随主人
        switch (behaviorState) {
            case BEHAVIOR_FOLLOW_COMMANDER:
                this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW_NEAREST);
                break;
            case BEHAVIOR_FOLLOW_NEAREST:
                this.communalPets$setBehaviorState(BEHAVIOR_WANDER);
                break;
            case BEHAVIOR_WANDER:
                this.communalPets$setBehaviorState(BEHAVIOR_SIT);
                this.wanderCenter = this.position();
                break;
            case BEHAVIOR_SIT:
                this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW_COMMANDER);
                break;
            default:
                this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW_COMMANDER);
        }
    }

    @Override
    @Unique
    public void communalPets$cycleBehavior(Player player) {
        if (this.level().isClientSide()) {
            return;
        }

        if(this.commander != null && player.getUUID().equals(this.commander.getUUID())) {
            communalPets$cycleBehavior();
        }
        else{
            this.communalPets$setCommander(player);
            this.communalPets$setBehaviorState(BEHAVIOR_FOLLOW_COMMANDER);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            // 动作栏：宠物名 + 刚切换到的状态；状态词用和发光一样的颜色标出来
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Messages.tr(
                    "communal-pets.actionbar.behavior",
                    this.getName(),
                    Messages.tr(CommunalPet.behaviorKeyOf(this.behaviorState))
                            .withColor(CommunalPet.glowTextColorOf(this.behaviorState)))));
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

    @Unique
    private void communalPets$startGlow(int behaviorState) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        PetGlow.paint(this, serverLevel, CommunalPet.glowColorOf(behaviorState));
        PetGlow.registerGlow(this);
        this.glowTicks = CommunalPet.GLOW_DURATION_TICKS;
    }

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

    @Override
    @Unique
    public void communalPets$pushGlowFlag() {
        this.communalPets$sendSharedFlags(
                (byte) (this.entityData.get(Entity.DATA_SHARED_FLAGS_ID) | 1 << Entity.FLAG_GLOWING));
    }

    @Unique
    private void communalPets$pushServerFlags() {
        this.communalPets$sendSharedFlags(this.entityData.get(Entity.DATA_SHARED_FLAGS_ID));
    }


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

    @Inject(method = "die", at = @At("HEAD"))
    private void communalPets$stopGlowOnDeath(final DamageSource source, final CallbackInfo ci) {
        if (PetGlow.isGlowing(this)) {
            this.glowTicks = 0;
            this.communalPets$stopGlow();
        }
    }

    /**
     * 实体视图：把 UUID 解析成在线玩家。
     * <p>
     * 离线 / 未加载的玩家解析结果是 null，<b>必须跳过</b> —— 直接
     * {@code stream().map(...).toList()} 会把 null 塞进列表（{@code Stream#toList} 允许 null），
     * 之后任何 {@code .map(LivingEntity::getUUID)} 之类的调用都会 NPE。
     * <p>
     * 每次调用新建，返回不可变列表：要增删照护者请走
     * {@code communalPets$getCaregiverUUIDs()} / {@link CommunalPet#addCaregiver} /
     * {@link CommunalPet#removeCaregiver}。
     */
    @Override
    @Unique
    public List<LivingEntity> communalPets$getCaregivers() {
        if (this.caregiverUUIDs.isEmpty()) {
            return List.of();
        }
        List<LivingEntity> caregivers = new ArrayList<>(this.caregiverUUIDs.size());
        for (UUID id : this.caregiverUUIDs) {
            Player player = this.level().getPlayerInAnyDimension(id);
            if (player != null) {
                caregivers.add(player);
            }
        }
        return List.copyOf(caregivers);
    }

    /**
     * 存储本体的<b>只读快照</b>：调用方拿到的永远是一份副本，不会顺手把内部列表改掉。
     * 写入口只有 {@link #communalPets$addCaregiver} / {@link #communalPets$removeCaregiver}；
     * 落档（{@code addAdditionalSaveData}）和读档仍然直接用私有字段，不受影响。
     */
    @Override
    @Unique
    public List<UUID> communalPets$getCaregiverUUIDs() {
        return List.copyOf(caregiverUUIDs);
    }

    @Override
    @Unique
    public void communalPets$addCaregiver(UUID id) {
        if (!caregiverUUIDs.contains(id)) {
            caregiverUUIDs.add(id);
        }
    }

    @Override
    @Unique
    public boolean communalPets$removeCaregiver(UUID id) {
        return caregiverUUIDs.remove(id);
    }

    @Override
    @Unique
    public @Nullable LivingEntity communalPets$getCommander() {
        return EntityReference.getLivingEntity(this.commander, this.level());
    }

    @Override
    @Unique
    public void communalPets$setCommander(@Nullable LivingEntity commander) {
        this.commander = EntityReference.of(commander);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddAdditionalSaveData(final ValueOutput output, CallbackInfo ci) {
        if (this.isTame()) {
            // 老存档里的 "follow" 在读档时归到 follow_commander（见 onReadAdditionalSaveData）
            String behavior = switch (behaviorState) {
                case BEHAVIOR_FOLLOW_COMMANDER -> "follow_commander";
                case BEHAVIOR_FOLLOW_NEAREST -> "follow_nearest";
                case BEHAVIOR_WANDER -> "wander";
                case BEHAVIOR_SIT -> "sit";
                default -> "follow_commander";
            };
            output.putString("behavior", behavior);
            output.store("wander_center", Vec3.CODEC, communalPets$getWanderCenter());
            output.putDouble("wander_radius", wanderRadius);
            output.putDouble("wander_inner_range", wanderInnerRange);


            output.store("caregivers", UUIDUtil.CODEC.listOf(), caregiverUUIDs);
            EntityReference.store(this.commander, output, "commander");
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(final ValueInput input, CallbackInfo ci) {
        if (this.isTame()) {
            // "follow" 是拆状态之前的老值，按"跟随主人"处理
            this.communalPets$setBehaviorState(switch (input.getStringOr("behavior", "sit")) {
                case "follow", "follow_commander" -> BEHAVIOR_FOLLOW_COMMANDER;
                case "follow_nearest" -> BEHAVIOR_FOLLOW_NEAREST;
                case "wander" -> BEHAVIOR_WANDER;
                default -> BEHAVIOR_SIT;
            });
            Optional<Vec3> wanderCenter = input.read("wander_center", Vec3.CODEC);
            this.wanderCenter = wanderCenter.orElse(this.position());
            this.wanderRadius = input.getDoubleOr("wander_radius", 32);
            this.wanderInnerRange = input.getDoubleOr("wander_inner_range", 0.8);

            this.caregiverUUIDs.clear();
            input.read("caregivers", UUIDUtil.CODEC.listOf()).ifPresent(caregiverUUIDs::addAll);
            this.commander = EntityReference.<LivingEntity>read(input, "commander");

        }

        if (!this.level().isClientSide()) {
            this.glowTicks = 0;
            PetGlow.unregisterGlow(this);
            if (this.level() instanceof ServerLevel serverLevel) {
                PetGlow.clear(this, serverLevel);
            }
        }
    }

    /**
     * vanilla 的 {@code setOrderedToSit(false)} 在 26.2 里只剩"受伤站起来"这一个来源
     * （{@code Wolf.hurtServer} / {@code Parrot.hurtServer}，反汇编核过；{@code mobInteract} 里那处
     * 已经被三个 mob 的 Redirect 拦掉），着火走的也是同一条 {@code hurtServer} 路径。
     * <p>
     * 所以这里按"受伤站起"来细化：<b>只有坐着才切到游荡，其它状态一律保留</b> ——
     * 不要再用 FOLLOW_COMMANDER 覆盖，否则一次受伤就会把游荡 / 跟随最近者悄悄重置掉。
     */
    @Inject(method = "setOrderedToSit(Z)V", at = @At("HEAD"))
    private void onSetOrderedToSit(boolean orderedToSit, CallbackInfo ci) {
        if (orderedToSit) {
            this.communalPets$setBehaviorState(BEHAVIOR_SIT);
        } else if (this.behaviorState == BEHAVIOR_SIT) {
            // setBehaviorState(WANDER) 会把游荡中心定在当前位置，并写 orderedToSit = false
            this.communalPets$setBehaviorState(BEHAVIOR_WANDER);
        }
        // FOLLOW_COMMANDER / FOLLOW_NEAREST / WANDER：原样保留，orderedToSit 由 vanilla 方法体写 false
    }

    @Inject(method = "isOwnedBy", at = @At("HEAD"), cancellable = true)
    private void onIsOwnedBy(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (caregiverUUIDs.contains(entity.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
