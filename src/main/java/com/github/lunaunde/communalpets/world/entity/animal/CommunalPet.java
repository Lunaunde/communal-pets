package com.github.lunaunde.communalpets.world.entity.animal;

import com.github.lunaunde.communalpets.CommunalPets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Interface for accessing the communal pets behavior state on {@link TamableAnimal}.
 * <p>
 * The {@link TamableAnimalMixin} implements this interface at runtime via Mixin.
 * Use {@link #getBehaviorState(TamableAnimal)} and
 * {@link #setBehaviorState(TamableAnimal, int)} for compile-time safe access.
 */
public interface CommunalPet {

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

    List<Caregiver> communalPets$getCaregivers();

    /**
     * Safe accessor — casts through Object because the interface is injected at
     * runtime by Mixin and is not visible to javac on the vanilla class.
     */
    static int getBehaviorState(TamableAnimal animal) {
        return ((CommunalPet) animal).communalPets$getBehaviorState();
    }

    static void setBehaviorState(TamableAnimal animal, int state) {
        ((CommunalPet) animal).communalPets$setBehaviorState(state);
    }



    static void cycleBehavior(TamableAnimal animal) {
        ((CommunalPet) animal).communalPets$cycleBehavior();
    }
    static void cycleBehavior(TamableAnimal animal, Player player) {((CommunalPet) animal).communalPets$cycleBehavior(player);}

    static class Caregiver{
        UUID uuid;
        String type;
        public Caregiver(final @Nullable UUID uuid, String type) {
            this.uuid = uuid;
            this.type = type;
        }
        public UUID getUUID() {return uuid;}
        public String getType() {return type;}
    }

    static List<Caregiver> getCaregivers(TamableAnimal animal) { return ((CommunalPet) animal).communalPets$getCaregivers(); }

    static void addNormalCaregiver(TamableAnimal animal, Caregiver caregiver){
        List<Caregiver> caregivers = getCaregivers(animal);
        if(!caregivers.contains(caregiver))
            caregivers.add(caregiver);
    }
    static void addNormalCaregiver(TamableAnimal animal, UUID uuid){
        Caregiver newCaregiver = new Caregiver(uuid,"Caregiver");
        addNormalCaregiver(animal,newCaregiver);
    }
    static void addNormalCaregiver(TamableAnimal animal, LivingEntity entity){
        addNormalCaregiver(animal,entity.getUUID());
    }

    static void removeNormalCaregiver(TamableAnimal animal, Caregiver caregiver){
        if(caregiver.getType().equals("Owner")){
            CommunalPets.LOGGER.warn("Owner cannot remove as normal caregiver");
        }
        else if(caregiver.getType().equals("Caregiver")){
            List<Caregiver> caregivers = getCaregivers(animal);
            caregivers.remove(caregiver);
        }
        else{
            CommunalPets.LOGGER.warn("Caregiver type unknown");
        }
    }
}
