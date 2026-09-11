package dev.postmark.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4f;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Capture resource-pack-aware Minecraft glyphs without reading or changing the screen. */
public final class NativeTextCapture {
    private NativeTextCapture() {}
    public record Run(String text,int x,int y,int color) {}
    private record Request(int size,List<Run> lines,CompletableFuture<BufferedImage> result) {}
    private static final ArrayDeque<Request> requests=new ArrayDeque<>();
    private static boolean busy;
    public static CompletableFuture<BufferedImage> request(int size,List<Run> lines) {
        if(size<1||size>512)throw new IllegalArgumentException("Invalid text capture size");
        var result=new CompletableFuture<BufferedImage>();requests.add(new Request(size,List.copyOf(lines),result));return result;
    }
    public static void afterFrame() {
        if(busy||requests.isEmpty())return;
        var request=requests.removeFirst();busy=true;int pixels=request.size*2; // Preserve 16-pixel CJK glyphs drawn at an 8-unit GUI height.
        var oldColor=RenderSystem.outputColorTextureOverride;var oldDepth=RenderSystem.outputDepthTextureOverride;
        var oldProjection=RenderSystem.getProjectionMatrixBuffer();var oldType=RenderSystem.getProjectionType();
        TextureTarget target=null;GpuBuffer buffer=null;ProjectionMatrixBuffer projection=null;
        try {
            var mc=Minecraft.getInstance();var source=mc.renderBuffers().bufferSource();source.endBatch();
            target=new TextureTarget("Postmark sticker text",pixels,pixels,true);
            var encoder=RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorAndDepthTextures(target.getColorTexture(),0,target.getDepthTexture(),1);
            RenderSystem.outputColorTextureOverride=target.getColorTextureView();RenderSystem.outputDepthTextureOverride=target.getDepthTextureView();
            projection=new ProjectionMatrixBuffer("Postmark sticker projection");
            RenderSystem.setProjectionMatrix(projection.getBuffer(new Matrix4f().setOrtho(0,request.size,request.size,0,-1000,1000)),ProjectionType.ORTHOGRAPHIC);
            for(var line:request.lines)mc.font.drawInBatch(line.text,line.x,line.y,line.color,false,new Matrix4f(),source,Font.DisplayMode.NORMAL,0,LightCoordsUtil.pack(15,15));
            source.endBatch();
            buffer=RenderSystem.getDevice().createBuffer(()->"Postmark sticker text readback",9,(long)pixels*pixels*4);
            final var ownedTarget=target;final var ownedBuffer=buffer;final var ownedProjection=projection;
            encoder.copyTextureToBuffer(target.getColorTexture(),buffer,0L,()->{
                try(var read=encoder.mapBuffer(ownedBuffer,true,false)) {
                    var image=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);var bytes=read.data();
                    for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++) {
                        int offset=(y*pixels+x)*4,r=Byte.toUnsignedInt(bytes.get(offset)),g=Byte.toUnsignedInt(bytes.get(offset+1)),b=Byte.toUnsignedInt(bytes.get(offset+2)),a=Byte.toUnsignedInt(bytes.get(offset+3));
                        if(a>0&&a<255){r=Math.min(255,r*255/a);g=Math.min(255,g*255/a);b=Math.min(255,b*255/a);}
                        image.setRGB(x,pixels-y-1,(a<<24)|(r<<16)|(g<<8)|b);
                    }
                    request.result.complete(image);
                }catch(Throwable e){request.result.completeExceptionally(e);}
                finally{ownedBuffer.close();ownedTarget.destroyBuffers();ownedProjection.close();busy=false;}
            },0);
            target=null;buffer=null;projection=null;
        }catch(Throwable e){if(buffer!=null)buffer.close();if(target!=null)target.destroyBuffers();if(projection!=null)projection.close();busy=false;request.result.completeExceptionally(e);}
        finally{RenderSystem.outputColorTextureOverride=oldColor;RenderSystem.outputDepthTextureOverride=oldDepth;RenderSystem.setProjectionMatrix(oldProjection,oldType);RenderSystem.disableScissorForRenderTypeDraws();}
    }
}
