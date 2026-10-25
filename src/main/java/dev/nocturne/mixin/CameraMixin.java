package dev.nocturne.mixin;

import dev.nocturne.module.impl.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    // Freecam moves its camera entity right before the camera reads it -> no one-frame lag / stutter.
    @Inject(method = "update", at = @At("HEAD"))
    private void nocturne$freecamStep(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView,
                                      float tickDelta, CallbackInfo ci) {
        if (Freecam.INSTANCE != null) Freecam.INSTANCE.step(focused);
    }
}
