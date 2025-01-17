package org.broad.igv.bedpe;

import org.broad.igv.track.RenderContext;

import java.awt.*;
import java.util.List;

public interface BedPERenderer {
     void render(List<BedPE> features, RenderContext context, Rectangle trackRectangle, InteractionTrack.ArcOption arcOption);

}
