package dev.postmark;

import dev.postmark.model.StampCaptureFrame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StampCaptureFrameTest {
    @Test void regularModelsKeepTheirOriginalCanvasAndTransform() {
        assertEquals(new StampCaptureFrame(0,0,1,32),StampCaptureFrame.around(-.4,-.5,.45,.5,32));
        assertEquals(1,StampCaptureFrame.around(-.5000001,-.3,.5,.4,48).units());
    }
    @Test void oversizedAndOffCenterBoundsFitWithTransparentEdges() {
        for(var b:new double[][]{{-1.2,-.7,1.2,1.1},{.4,.6,1.2,1.3},{-.8,-1.9,-.2,-1.1}}) {
            var f=StampCaptureFrame.around(b[0],b[1],b[2],b[3],48);
            assertTrue(((b[0]-f.centerX())/f.units()+.5)*f.pixels()>=1-1e-6);
            assertTrue(((b[1]-f.centerY())/f.units()+.5)*f.pixels()>=1-1e-6);
            assertTrue(((b[2]-f.centerX())/f.units()+.5)*f.pixels()<=f.pixels()-1+1e-6);
            assertTrue(((b[3]-f.centerY())/f.units()+.5)*f.pixels()<=f.pixels()-1+1e-6);
        }
    }
    @Test void hugeModelsHaveABoundedCaptureAndStillFit() {
        var f=StampCaptureFrame.around(-100,-100,100,100,64);
        assertEquals(2048,f.pixels());assertTrue(f.units()>200);
        assertThrows(IllegalArgumentException.class,()->StampCaptureFrame.around(Double.NaN,0,1,1,32));
    }
}
