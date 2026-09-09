package dev.postmark.compat.mixin;

import dev.postmark.compat.SignMeUpBridge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional target: no SignMeUp classes are linked when that mod is absent. */
@Pseudo
@Mixin(targets = "org.teacon.exhibition_portal.client.interaction.StampingCounterBlock", remap = false)
public abstract class StampingCounterMixin {
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lorg/teacon/exhibition_portal/client/screens/stamp/StampScreenLayouts;setEditingExhibition(Lorg/teacon/exhibition_portal/components/Exhibition;Lorg/teacon/exhibition_portal/components/ExhibitionStamp;)V"))
    private void postmark$capture(@Coerce Object exhibition, @Coerce Object stamp) {
        SignMeUpBridge.capture(exhibition, stamp);
    }
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void postmark$open(Minecraft minecraft, Screen original) {
        SignMeUpBridge.open(minecraft, original);
    }
}