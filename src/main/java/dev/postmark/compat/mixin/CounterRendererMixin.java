package dev.postmark.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.postmark.compat.*;
import dev.postmark.render.CounterCollectionMarker;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntityRenderer",remap=false)
public abstract class CounterRendererMixin {
    @Inject(method="extractRenderState(Lorg/teacon/exhibition_portal/client/interaction/StampingCounterBlockEntity;Lorg/teacon/exhibition_portal/client/interaction/StampingCounterBlockEntityRenderer$RenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",at=@At("TAIL"))
    private void postmark$extract(@Coerce Object entity,@Coerce Object state,float partialTick,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay,CallbackInfo ci) {
        ((CounterCollectionState)state).postmark$collected(CounterCollectionMarker.collected(entity));
    }
    @Inject(method="submit(Lorg/teacon/exhibition_portal/client/interaction/StampingCounterBlockEntityRenderer$RenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"))
    private void postmark$submit(@Coerce Object state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera,CallbackInfo ci) {
        CounterCollectionMarker.submit((CounterCollectionState)state,pose,collector,camera);
    }
}
