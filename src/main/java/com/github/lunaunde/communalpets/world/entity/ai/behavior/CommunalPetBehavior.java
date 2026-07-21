package com.github.lunaunde.communalpets.world.entity.ai.behavior;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Interface for accessing the communal pets behavior state on {@link TamableAnimal}.
 * <p>
 * The {@link TamableAnimalMixin} implements this interface at runtime via Mixin.
 * Use {@link #getBehaviorState(TamableAnimal)} and
 * {@link #setBehaviorState(TamableAnimal, int)} for compile-time safe access.
 */
public interface CommunalPetBehavior {

    int BEHAVIOR_FOLLOW = 0;
    int BEHAVIOR_WANDER = 1;
    int BEHAVIOR_SIT = 2;

    int communalPets$getBehaviorState();

    Vec3 communalPets$getWanderCenter();
    double communalPets$getWanderRadius();
    double communalPets$getWanderInnerRange();

    void communalPets$setBehaviorState(int state);

    void communalPets$cycleBehavior();
    void communalPets$cycleBehavior(Player player);

    /**
     * Safe accessor — casts through Object because the interface is injected at
     * runtime by Mixin and is not visible to javac on the vanilla class.
     */
    static int getBehaviorState(TamableAnimal animal) {
        return ((CommunalPetBehavior) animal).communalPets$getBehaviorState();
    }

    static void setBehaviorState(TamableAnimal animal, int state) {
        ((CommunalPetBehavior) animal).communalPets$setBehaviorState(state);
    }

    static void cycleBehavior(TamableAnimal animal) {
        ((CommunalPetBehavior) animal).communalPets$cycleBehavior();
    }
    static void cycleBehavior(TamableAnimal animal, Player player) {((CommunalPetBehavior) animal).communalPets$cycleBehavior(player);}
}
