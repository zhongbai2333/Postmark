package dev.postmark.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.postmark.model.StampCaptureFrame;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;

/** Uses Minecraft's GUI item renderer, including the model, tint and GUI transform. */
public final class ItemStampCapture {
    private record Request(ItemStack stack,CompletableFuture<BufferedImage> result) {}
    private static final class CaptureState extends TrackingItemStackRenderState {
        private StampCaptureFrame frame;
        void fit(int pixels) {
            var bounds=getModelBoundingBox();
            frame=StampCaptureFrame.around(bounds.minX,bounds.minY,bounds.maxX,bounds.maxY,pixels);
        }
        @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,int overlay,int outline) {
            pose.pushPose();
            try {
                float scale=(float)(1/frame.units());pose.scale(scale,scale,scale);
                pose.translate(-frame.centerX(),-frame.centerY(),0);
                super.submit(pose,collector,light,overlay,outline);
            } finally {pose.popPose();}
        }
    }
    private static final ArrayDeque<Request> REQUESTS=new ArrayDeque<>();
    private static boolean busy;
    private ItemStampCapture() {}
    public static CompletableFuture<BufferedImage> request(ItemStack item) {
        var result=new CompletableFuture<BufferedImage>(); REQUESTS.addLast(new Request(item,result)); return result;
    }
    /** Called after a game frame, never while the normal GUI is being extracted. */
    public static void afterFrame() {
        if(busy || REQUESTS.isEmpty()) return;
        Request request=REQUESTS.removeFirst(); busy=true;
        // SMU enlarges vanilla GUI slots (16 * GUI scale), not a high-resolution 256px render.
        final int baseSize=16*Math.max(1,(int)Minecraft.getInstance().getWindow().getGuiScale());
        GuiItemAtlas atlas=null;
        GpuBuffer buffer=null;
        var oldColor=RenderSystem.outputColorTextureOverride;
        var oldDepth=RenderSystem.outputDepthTextureOverride;
        var projection=RenderSystem.getProjectionMatrixBuffer();
        var projectionType=RenderSystem.getProjectionType();
        try {
            var mc=Minecraft.getInstance();
            var state=new CaptureState();
            // Same context as GuiGraphicsExtractor.fakeItem(new ItemStack(item), ...).
            mc.getItemModelResolver().updateForTopItem(state,request.stack,ItemDisplayContext.GUI,mc.level,null,0);
            if(state.isEmpty()) throw new IllegalStateException("Item has no GUI model");
            state.fit(baseSize);
            final int size=state.frame.pixels();
            atlas=new GuiItemAtlas(mc.gameRenderer.getSubmitNodeStorage(),mc.gameRenderer.getFeatureRenderDispatcher(),
                    mc.renderBuffers().bufferSource(),size,size);
            var slot=atlas.getOrUpdate(state);
            if(slot==null) throw new IllegalStateException("Could not render stamp item");
            var texture=slot.textureView().texture();
            buffer=RenderSystem.getDevice().createBuffer(()->"Postmark item readback",9,(long)size*size*4);
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            final GuiItemAtlas ownedAtlas=atlas;
            final GpuBuffer ownedBuffer=buffer;
            encoder.copyTextureToBuffer(texture,buffer,0L,()->{
                try(var read=encoder.mapBuffer(ownedBuffer,true,false)) {
                    var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
                    var bytes=read.data();
                    for(int y=0;y<size;y++) for(int x=0;x<size;x++) {
                        int i=(y*size+x)*4;
                        int r=Byte.toUnsignedInt(bytes.get(i)),g=Byte.toUnsignedInt(bytes.get(i+1)),b=Byte.toUnsignedInt(bytes.get(i+2)),a=Byte.toUnsignedInt(bytes.get(i+3));
                        // GuiItemAtlas uses premultiplied alpha. PNG and Java2D expect straight alpha.
                        if(a>0 && a<255) { r=Math.min(255,r*255/a); g=Math.min(255,g*255/a); b=Math.min(255,b*255/a); }
                        image.setRGB(x,size-y-1,(a<<24)|(r<<16)|(g<<8)|b);
                    }
                    request.result.complete(image);
                } catch(Throwable error) { request.result.completeExceptionally(error); }
                finally { ownedBuffer.close(); ownedAtlas.close(); busy=false; }
            },0);
            atlas=null; buffer=null; // The asynchronous readback callback now owns both resources.
        } catch(Throwable error) {
            if(buffer!=null) buffer.close(); if(atlas!=null) atlas.close();
            busy=false; request.result.completeExceptionally(error);
        } finally {
            RenderSystem.outputColorTextureOverride=oldColor;
            RenderSystem.outputDepthTextureOverride=oldDepth;
            RenderSystem.disableScissorForRenderTypeDraws();
            RenderSystem.setProjectionMatrix(projection,projectionType);
        }
    }
}