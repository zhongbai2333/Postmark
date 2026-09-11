package dev.postmark.render;

import com.mojang.blaze3d.vertex.*;
import dev.postmark.client.ClientSession;
import dev.postmark.compat.CounterCollectionState;
import dev.postmark.model.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import org.joml.Quaternionf;
import java.util.*;

/** Constant-size geometry, depth-tested like the counter; no world scans or captures per frame. */
public final class CounterCollectionMarker {
    private static Album cachedAlbum;
    private static Identifier texture;
    private static Set<String> owned=Set.of();
    private record Access(java.lang.reflect.Method venue,java.lang.reflect.Method id,java.lang.reflect.Method item) {}
    private static final ClassValue<Access> ACCESS=new ClassValue<>() {
        protected Access computeValue(Class<?> type) {
            try{return new Access(type.getMethod("getExhibition"),type.getMethod("getStampID"),type.getMethod("getItem"));}
            catch(ReflectiveOperationException e){throw new IllegalArgumentException(e);}
        }
    };
    private CounterCollectionMarker() {}
    public static boolean collected(Object counter) {
        try {
            var album=ClientSession.get().album();
            if(album!=cachedAlbum) {var keys=new HashSet<String>();for(var s:album.stamps())if(!s.practice())keys.add(s.key());owned=Set.copyOf(keys);cachedAlbum=album;}
            var access=ACCESS.get(counter.getClass());var venue=(UUID)access.venue.invoke(counter);var id=(String)access.id.invoke(counter);var item=access.item.invoke(counter);
            return venue!=null && id!=null && item!=null && owned.contains(StampIdentity.key(venue,id,item.toString()));
        } catch(Exception ignored){return false;}
    }
    public static void submit(CounterCollectionState state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if(!state.postmark$collected())return;
        var type=RenderTypes.entityCutout(texture());
        // Permanent tabletop badge at 15/16, slightly raised to prevent z-fighting.
        pose.pushPose();pose.translate(.5,.945,.5);pose.mulPose(new Quaternionf().rotateX((float)-Math.PI/2));pose.scale(.044f,.044f,.044f);
        collector.submitCustomGeometry(pose,type,CounterCollectionMarker::badge);pose.popPose();
        // A small upright tabletop pennant is legible from a level, distant camera too.
        pose.pushPose();pose.translate(.5,1.18,.5);pose.mulPose(camera.orientation);pose.scale(.029f,.029f,.029f);
        collector.submitCustomGeometry(pose,type,CounterCollectionMarker::badge);pose.popPose();
        if(!state.postmark$visible())return;
        double seconds=Math.max(0,(Util.getNanos()-state.postmark$visibleSince())/1e9);
        float grow=(float)Math.min(1,seconds/.5);
        pose.pushPose();pose.translate(.5,3+Math.sin(seconds/1.5*Math.PI*2)*.1,.5);pose.scale(grow,grow,grow);pose.mulPose(camera.orientation);
        // Same billboard transform and entrance/bob as SMU's icon, offset toward the viewer.
        pose.translate(.38,-.36,.015);pose.scale(.025f,.025f,.025f);
        collector.submitCustomGeometry(pose,type,CounterCollectionMarker::badge);pose.popPose();
    }
    private static Identifier texture() {
        if(texture!=null)return texture;
        var pixels=new NativeImage(16,16,false);
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)pixels.setPixel(x,y,0);
        fill(pixels,2,0,14,16,0xFFF3E6BC);fill(pixels,0,2,16,14,0xFFF3E6BC);
        fill(pixels,3,2,13,14,0xFF326D48);fill(pixels,2,3,14,13,0xFF326D48);
        // Connected thick diagonals; bake all layers together to avoid translucent quad sorting.
        for(int i=0;i<3;i++)fill(pixels,3+i,7+i,5+i,9+i,0xFFF7F0CC);
        for(int i=0;i<6;i++)fill(pixels,5+i,9-i,7+i,11-i,0xFFF7F0CC);
        texture=Identifier.fromNamespaceAndPath("postmark","counter_collected");
        Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"Collected stamp counter",pixels));
        return texture;
    }
    private static void fill(NativeImage pixels,int x0,int y0,int x1,int y1,int color) {
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)pixels.setPixel(x,y,color);
    }
    private static void vertex(PoseStack.Pose pose,VertexConsumer v,float x,float y,float u,float w) {
        v.addVertex(pose,x,y,0).setColor(-1).setUv(u,w).setLight(LightCoordsUtil.pack(15,15)).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose,0,0,1);
    }
    private static void badge(PoseStack.Pose pose,VertexConsumer v) {
        vertex(pose,v,-8,-8,0,1);vertex(pose,v,8,-8,1,1);
        vertex(pose,v,8,8,1,0);vertex(pose,v,-8,8,0,0);
    }
}
