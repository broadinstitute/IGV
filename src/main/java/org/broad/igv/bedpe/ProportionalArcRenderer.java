package org.broad.igv.bedpe;

import org.broad.igv.track.RenderContext;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.Pair;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broad.igv.bedpe.InteractionTrack.Direction.UP;

public class ProportionalArcRenderer implements BedPERenderer {


    public static final float PROP_ALPHA = 0.02f;
    private Map<Color, Color> alphaColors = new HashMap<>();

    InteractionTrack track;

    public ProportionalArcRenderer(InteractionTrack track) {
        this.track = track;
    }

    public void render(List<BedPE> features, RenderContext context, Rectangle trackRectangle, InteractionTrack.ArcOption arcOption) {

        Graphics2D g = null;

        try {
            g = (Graphics2D) context.getGraphics().create();
            double origin = context.getOrigin();
            double locScale = context.getScale();
            Color trackColor = track.getColor();

            if (track.thickness > 1) {
                g.setStroke(new BasicStroke(track.thickness));
            }

            // Filter features to in-view
            List<BedPE> filteredFeatures = new ArrayList<>();
            for (BedPE bedPE : features) {
                double p1 = (bedPE.getStart() - origin) / locScale;
                double p2 = (bedPE.getEnd() - origin) / locScale;
                if (p2 >= trackRectangle.getX() && p1 <= trackRectangle.getMaxX()) {
                    // Optionally filter arcs with one or both ends out of view
                    double pixelStart = (bedPE.getMidStart() - origin) / locScale;
                    double pixelEnd = (bedPE.getMidEnd() - origin) / locScale;
                    if (arcOption == InteractionTrack.ArcOption.ONE_END) {
                        if (pixelStart < trackRectangle.x && pixelEnd > trackRectangle.x + trackRectangle.width)
                            continue;
                    } else if (arcOption == InteractionTrack.ArcOption.BOTH_ENDS) {
                        if (pixelStart < trackRectangle.x || pixelEnd > trackRectangle.x + trackRectangle.width)
                            continue;
                    }
                    filteredFeatures.add(bedPE);
                }
            }

            for (BedPE bedPE : filteredFeatures) {

                double p1 = (bedPE.getStart() - origin) / locScale;
                double p2 = (bedPE.getEnd() - origin) / locScale;

                if (p2 >= trackRectangle.getX() && p1 <= trackRectangle.getMaxX()) {

                    InteractionTrack.Direction direction = track.direction;
                    int gap = track.gap;
                    int h = trackRectangle.height - gap;

                    if (track.maxScore > 0 && bedPE.getScore() > 0) {
                        double logMax = Math.log10(track.maxScore + 1);
                        h = (int) ((Math.log10(bedPE.getScore() + 1) / logMax) * h);
                    }

                    if (bedPE.isSameChr()) {

                        BedPE feature = bedPE;

                        Color fcolor = feature.getColor() == null ? trackColor : feature.getColor();
                        if (fcolor != null) {
                            g.setColor(fcolor);
                        }

                        double pixelStart = (feature.getMidStart() - origin) / locScale;
                        double pixelEnd = (feature.getMidEnd() - origin) / locScale;

                        int w = (int) (pixelEnd - pixelStart);
                        if (w < 3) {
                            w = 3;
                            pixelStart--;
                        }

                        double y = direction == UP ? gap + trackRectangle.y + trackRectangle.height - h : gap + trackRectangle.y - h;
                        int angleSt = direction == UP ? 0 : 180;
                        Arc2D.Double arcPath = new Arc2D.Double(
                                pixelStart,
                                y,
                                w,
                                2 * h,
                                angleSt,
                                180,
                                Arc2D.OPEN
                        );

                        g.draw(arcPath);
                        Color shadedColor = getAlphaColor(fcolor, PROP_ALPHA);
                        g.setColor(shadedColor);
                        g.fill(arcPath);

                        bedPE.setShape(new PAShape(pixelStart + w / 2, y + h, w / 2, h));

                    } else {
                        Color fcolor = bedPE.getColor() == null ? Color.black : bedPE.getColor();
                        g.setColor(fcolor);
                        double ps = ((bedPE.getStart() + bedPE.getEnd()) / 2 - origin) / locScale;
                        int yBase = direction == UP ? trackRectangle.y + trackRectangle.height - h : trackRectangle.y + gap;
                        g.drawLine((int) ps, yBase, (int) ps, yBase + h);
                    }
                } else {
                    bedPE.setShape(null);
                }
            }
        } finally {
            if (g != null) g.dispose();
        }
    }


    private Color getAlphaColor(Color fcolor, float alpha) {
        Color ac = alphaColors.get(fcolor);
        if (ac == null) {
            float[] rgb = new float[3];
            rgb = fcolor.getRGBColorComponents(rgb);
            ac = new Color(rgb[0], rgb[1], rgb[2], alpha);
            alphaColors.put(fcolor, ac);
        }
        return ac;
    }

    //(x-h)^2/a^2 + (y-k)^2/b^2 <= 1
    public static class PAShape implements BedPEShape {

        double h;   //xc
        double k;   //yc
        double a2;   // x axis
        double b2;   // y axis

        public PAShape(double h, double k, double a, double b) {
            this.h = h;
            this.k = k;
            this.a2 = a * a;
            this.b2 = b * b;
        }

        @Override
        public boolean contains(double x, double y) {

            double dx = x - h;
            double dy = y - k;
            double e = dx * dx / a2 + dy * dy / b2;
            return e < 1.0;
        }
    }
}

