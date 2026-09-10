package dev.postmark;

import dev.postmark.render.GuideMarkerPainter;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideMarkerPainterTest {
    @Test void completionInteriorHasNoOuterRingLeaksBetweenScanlines() {
        var image=GuideMarkerPainter.paint();
        // Third 128px atlas cell: strictly inside its cream disc, outside the circular border.
        // The old per-scanline rotation leaked 17 outer-layer pixels into this region.
        for(int y=49;y<80;y++)for(int x=305;x<336;x++)if(Math.hypot(x+.5-320,y+.5-64)<15) {
            int color=image.getRGB(x,y);
            assertTrue(color==0xFFF6EBD0 || color==0xFF285638,"Unexpected outer color inside completion disc at "+x+","+y+": "+Integer.toHexString(color));
        }
    }
}
