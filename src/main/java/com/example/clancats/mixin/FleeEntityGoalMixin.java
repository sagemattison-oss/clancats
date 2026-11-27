package com.example.clancats.mixin;

import com.example.clancats.CatAbilities;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla cats run from players. They don't run from a player who is a cat. */
@Mixin(FleeEntityGoal.class)
public abstract class FleeEntityGoalMixin {
    @Shadow
    @Final
    protected PathAwareEntity mob;

    @Inject(method = "canStart", at = @At("RETURN"), cancellable = true)
    private void clancats$catsAreNotScary(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || !(this.mob instanceof CatEntity)) return;
        PlayerEntity nearest = this.mob.getWorld().getClosestPlayer(this.mob, 16.0);
        if (nearest != null && CatAbilities.isCat(nearest)) {
            cir.setReturnValue(false);
        }
    }
}
