package dev.postmark.compat.mixin;

import dev.postmark.compat.CounterCollectionState;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="org.teacon.exhibition_portal.client.interaction.StampingCounterBlockEntityRenderer$RenderState",remap=false)
public abstract class CounterRenderStateMixin implements CounterCollectionState {
    @Shadow private boolean visible;
    @Shadow private long visibleSinceNs;
    @Unique private boolean postmark$collected;
    public boolean postmark$collected(){return postmark$collected;}
    public void postmark$collected(boolean collected){postmark$collected=collected;}
    public boolean postmark$visible(){return visible;}
    public long postmark$visibleSince(){return visibleSinceNs;}
}
