package dev.nocturne.mixin;

import dev.nocturne.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    @Unique private float nocturne$realYaw, nocturne$realPitch;
    @Unique private boolean nocturne$swapped;

    // Swap the camera rotation for the silent rotation only while the movement packet is being built,
    // so the server sees the aim rotation and the player's screen never moves.
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void nocturne$swapIn(CallbackInfo ci) {
        if (!RotationManager.active()) return;
        ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
        nocturne$realYaw = p.getYaw();
        nocturne$realPitch = p.getPitch();
        p.setYaw(RotationManager.yaw);
        p.setPitch(RotationManager.pitch);
        nocturne$swapped = true;
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void nocturne$swapOut(CallbackInfo ci) {
        if (!nocturne$swapped) return;
        nocturne$swapped = false;
        ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
        p.setYaw(nocturne$realYaw);
        p.setPitch(nocturne$realPitch);
    }
}
