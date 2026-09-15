package com.github.lunaunde.communalpets.mixin.minecraft.word.entity;

import com.github.lunaunde.communalpets.world.entity.animal.CommunalPet;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 发光是有时限的（60 tick），需要一个每 tick 都会走到的入口。
 * <p>
 * {@link net.minecraft.world.entity.TamableAnimal} 自己并没有声明 {@code tick()}，
 * 它是继承自 {@link Mob} 的；在 TamableAnimalMixin 里注入 {@code tick} 会把代码
 * 注入到父类方法上，从而影响所有生物，所以这里单独对声明了 {@code tick()} 的
 * {@link Mob} 注入，再用接口判断筛出宠物。
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void communalPets$tickGlow(final CallbackInfo ci) {
        if (this instanceof CommunalPet pet) {
            pet.communalPets$tickGlow();
        }
    }
}
