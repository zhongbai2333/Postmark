package dev.postmark;

import dev.postmark.model.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideCanvasTest {
    @Test void entireCatalogSharesStableBoundedCanvas() {
        var ids=IntStream.range(0,4096).mapToObj(i->new UUID(0,i)).toList();
        var layout=GuideCanvasLayout.scatter(ids);assertEquals(layout,GuideCanvasLayout.scatter(ids));
        assertEquals(ids.size(),layout.nodes().size());
        assertEquals(4096,layout.nodes().stream().map(GuideCanvasLayout.Node::index).distinct().count());
        for(var n:layout.nodes()){assertTrue(n.x()>15&&n.y()>15);assertTrue(n.x()+n.size()+30<layout.width());assertTrue(n.y()+n.size()+40<layout.height());}
    }
    @Test void emptyCanvasAndAddedVenuesDoNotReshuffleExistingScraps() {
        assertTrue(GuideCanvasLayout.scatter(List.of()).nodes().isEmpty());
        var ids=IntStream.range(0,20).mapToObj(i->new UUID(0,i)).toList();
        var a=GuideCanvasLayout.scatter(ids.subList(0,10));var b=GuideCanvasLayout.scatter(ids);
        for(int i=0;i<10;i++){var x=a.nodes().get(i);var y=b.nodes().get(i);assertEquals(x.x()-a.nodes().getFirst().x(),y.x()-b.nodes().getFirst().x());assertEquals(x.y()-a.nodes().getFirst().y(),y.y()-b.nodes().getFirst().y());assertEquals(x.angle(),y.angle());}
    }
    @Test void animatedZoomKeepsCursorAnchoredAtEveryFrame() {
        var v=new GuideViewport();v.resize(600,340,900,700,true);
        double wx=v.worldX(330),wy=v.worldY(160),initial=v.zoom();v.zoomAt(330,160,1.7);
        assertEquals(initial,v.zoom());
        for(int i=0;i<50;i++){v.step(.016);assertEquals(wx,v.worldX(330),1e-7);assertEquals(wy,v.worldY(160),1e-7);}
        assertTrue(v.zoom()>initial*1.6);
    }
    @Test void dragZoomLimitsAndFitKeepPaperRecoverable() {
        var v=new GuideViewport();v.resize(600,340,900,700,true);double x=v.x(),y=v.y();v.pan(30,-20);assertEquals(x+30,v.x());assertEquals(y-20,v.y());
        v.zoomAt(300,170,1000);for(int i=0;i<100;i++)v.step(.05);assertEquals(2.4,v.zoom(),1e-6);
        v.pan(1e9,-1e9);assertTrue(v.x()<600);assertTrue(v.y()>-700*2.4);
        v.fit(true);assertTrue(v.x()>=0&&v.y()>=0);assertTrue(v.x()+900*v.zoom()<=600);assertTrue(v.y()+700*v.zoom()<=340);
    }
    @Test void zoomAnimationIsIndependentOfFrameRate() {
        var a=new GuideViewport();var b=new GuideViewport();for(var v:List.of(a,b)){v.resize(600,340,900,700,true);v.zoomAt(300,170,2);}
        for(int i=0;i<60;i++)a.step(1.0/60);for(int i=0;i<30;i++)b.step(1.0/30);
        assertEquals(a.zoom(),b.zoom(),1e-9);assertEquals(a.x(),b.x(),1e-9);
    }
}
