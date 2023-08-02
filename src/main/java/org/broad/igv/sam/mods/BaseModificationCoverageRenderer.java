package org.broad.igv.sam.mods;

import htsjdk.samtools.util.SequenceUtil;
import org.broad.igv.sam.AlignmentCounts;
import org.broad.igv.sam.AlignmentTrack.ColorOption;
import org.broad.igv.track.RenderContext;

import java.awt.*;
import java.util.*;
import java.util.List;

public class BaseModificationCoverageRenderer {

    public static void drawModifications(RenderContext context,
                                         int pX,
                                         int pBottom,
                                         int dX,
                                         int barHeight,
                                         int pos,
                                         AlignmentCounts alignmentCounts,
                                         ColorOption colorOption,
                                         BaseModficationFilter filter,
                                         float threshold,
                                         Set<String> simplexModifications) {


        BaseModificationCounts modificationCounts = alignmentCounts.getModifiedBaseCounts();

        if (modificationCounts != null) {

            float modThreshold = threshold;
            float nomodThreshold = threshold < 0.5 ? 1 - threshold : threshold;

            Set<BaseModificationKey> allModificationKeys =  modificationCounts.getAllModificationKeys();
            List<BaseModificationKey> sortedKeys = new ArrayList<>(allModificationKeys);
            Collections.sort(sortedKeys);

            // Color bar modification counts (# of modifications > threshold)
            int total = alignmentCounts.getTotalCount(pos);

            for (BaseModificationKey key : sortedKeys) {

                if (filter != null && !filter.pass(key.modification, key.getCanonicalBase())) continue;
                if(key.modification.startsWith("NONE_") && colorOption != ColorOption.BASE_MODIFICATION_2COLOR) continue;

                byte base = (byte) key.getBase();
                final byte compl = SequenceUtil.complement(base);
                int modifiable = alignmentCounts.getCount(pos, base) + alignmentCounts.getCount(pos, compl);
                int detectable = simplexModifications.contains(key.modification) ?
                        alignmentCounts.getPosCount(pos, base) + alignmentCounts.getNegCount(pos, compl) :
                        modifiable;

                if (detectable == 0) continue;  //No informative reads

                float t = key.modification.startsWith("NONE_") ? nomodThreshold : threshold;
                int count = modificationCounts.getCount(pos, key, t);
                float modFraction = (((float) modifiable) / total) * (((float) count) / detectable);
                int modHeight = Math.round(modFraction * barHeight);

                int baseY = pBottom - modHeight;
                Color modColor = BaseModificationColors.getModColor(key.modification, 255, colorOption);
                Graphics2D graphics = context.getGraphics();
                graphics.setColor(modColor);
                graphics.fillRect(pX, baseY, dX, modHeight);
                pBottom = baseY;

            }
        }
    }

    static Map<String, Integer> modRankOrder;



}
