package dev.nocturne.mixin;

import dev.nocturne.module.impl.FakePlayer;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    // Hits on the client-side FakePlayer never reach the server; they are simulated locally instead.
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void nocturne$fakeHit(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (FakePlayer.isFake(target)) {
            FakePlayer.INSTANCE.hit(player, target);
            ci.cancel();
        }
    }
}
