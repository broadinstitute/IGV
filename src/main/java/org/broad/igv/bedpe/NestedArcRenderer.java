package org.broad.igv.bedpe;

import org.broad.igv.track.RenderContext;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broad.igv.bedpe.InteractionTrack.Direction.UP;

public class NestedArcRenderer implements BedPERenderer{


    private Map<Color, Color> alphaColors = new HashMap<>();

    InteractionTrack track;
    double theta = Math.toRadians(45);
    double sinTheta = Math.sin(theta);
    double cosTheta = Math.cos(theta);
    boolean autoscale = true;

    public NestedArcRenderer(InteractionTrack track) {
        this.track = track;
    }

    public void render(List<BedPE> features, RenderContext context, Rectangle trackRectangle, InteractionTrack.ArcOption arcOption) {

        Graphics2D g = null;

        try {
            g = (Graphics2D) context.getGraphics().create();
            double origin = context.getOrigin();
            double locScale = context.getScale();
            Color trackColor = track.getColor();
            int gap = track.gap;

            if (track.thickness > 1) {
                g.setStroke(new BasicStroke(track.thickness));
            }

            if (autoscale) {
                double max = 0;
                for (BedPE feature : features) {
                    double pixelStart = ((feature.getStart() - origin) / locScale);
                    double pixelEnd = ((feature.getEnd() - origin) / locScale);
                    if (pixelEnd >= trackRectangle.getX() && pixelStart <= trackRectangle.getMaxX()) {
                        max = Math.max(max, pixelEnd - pixelStart);
                    }
                }
                double a = Math.min(trackRectangle.width, max) / 2;
                if (max > 0) {
                    double coa = (trackRectangle.height - gap) / a;
                    this.theta = SagittusEstimate.estimateTheta(coa);
                    this.sinTheta = Math.sin(this.theta);
                    this.cosTheta = Math.cos(this.theta);
                }
            }


            for (BedPE bedPE : features) {

                double p1 = (bedPE.getStart() - origin) / locScale;
                double p2 = (bedPE.getEnd() - origin) / locScale;

                if (p2 >= trackRectangle.getX() && p1 <= trackRectangle.getMaxX()) {

                    InteractionTrack.Direction direction = track.direction;

                    if (bedPE.isSameChr()) {

                        BedPE feature = bedPE;
                        Color fcolor = feature.getColor() == null ? trackColor : feature.getColor();

                        double pixelStart = (feature.getMidStart() - origin) / locScale;
                        double pixelEnd = (feature.getMidEnd() - origin) / locScale;

                        // Optionally filter arcs with one or both ends out of view
                        if(arcOption == InteractionTrack.ArcOption.ONE_END) {
                            if(pixelStart < trackRectangle.x && pixelEnd > trackRectangle.x + trackRectangle.width) continue;
                        } else if(arcOption == InteractionTrack.ArcOption.BOTH_ENDS) {
                            if(pixelStart < trackRectangle.x || pixelEnd > trackRectangle.x + trackRectangle.width) continue;
                        }

                        int w = (int) (pixelEnd - pixelStart);
                        if (w < 3) {
                            w = 3;
                            pixelStart--;
                        }


                        if (fcolor != null && w > trackRectangle.width) {
                            fcolor = getAlphaColor(fcolor, 0.1f);
                        }
                        if (fcolor != null) {
                            g.setColor(fcolor);
                        }
                        final int trackBaseLine = trackRectangle.y + trackRectangle.height;

                        double a = w / 2;
                        double r = a / sinTheta;
                        double b = cosTheta * r;
                        double xc = pixelStart + a;
                        double yc = direction == UP ? trackBaseLine + b : gap + trackRectangle.y - b;
                        double angleSt = direction == UP ? 90 - Math.toDegrees(theta) : 270 - Math.toDegrees(theta);
                        double ext = Math.toDegrees(2 * theta);

                        Arc2D.Double arcPath = new Arc2D.Double();
                        arcPath.setArcByCenter(xc, yc, r, angleSt, ext, Arc2D.OPEN);
                        g.draw(arcPath);

                        feature.setShape(new NAShape(xc, yc, r));

                    } else {
                        Color fcolor = bedPE.getColor() == null ? Color.black : bedPE.getColor();
                        g.setColor(fcolor);
                        int h = trackRectangle.height / 2;
                        double ps = ((bedPE.getStart() + bedPE.getEnd()) / 2 - origin) / locScale;
                        int yBase = direction == UP ? trackRectangle.y + trackRectangle.height - h : trackRectangle.y + gap;
                        g.drawLine((int) ps, yBase, (int) ps, yBase + h);

                    }
                } else {

                }
            }
        } finally {
            if(g != null) g.dispose();
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

    /**
     * Estimate theta given the ratio of track height to 1/2 the feature width (coa).  This relationship is approximately linear.
     */
    static class SagittusEstimate {

        static final double[] coa = {0.01570925532366355, 0.15838444032453644, 0.3249196962329063, 0.5095254494944288, 0.7265425280053609, 0.9999999999999999};
        static final double[] theta = {0.031415926535897934, 0.3141592653589793, 0.6283185307179586, 0.9424777960769379, 1.2566370614359172, 1.5707963267948966};

        static double estimateTheta(double x) {

            int idx;
            for (idx = 0; idx < coa.length; idx++) {
                if (coa[idx] > x) {
                    break;
                }
            }
            double left = idx == 0 ? 0 : coa[idx - 1];
            double right = idx < coa.length ? coa[idx] : 1;
            double r = (x - left) / (right - left);

            double thetaLeft = idx == 0 ? 0 : theta[idx - 1];
            double thetaRight = idx < theta.length ? theta[idx] : Math.PI / 2;

            return thetaLeft + r * (thetaRight - thetaLeft);

        }

        static void generateTable(String[] args) {

            for (double theta = Math.PI / 10; theta <= Math.PI / 2; theta += Math.PI / 10) {
                double num = 1 - Math.cos(theta);
                double denom = Math.sin(theta);
                double coa = num / denom;
            }
        }
    }


    public static class NAShape implements BedPEShape {

        double xc;
        double yc;
        double r;

        public NAShape(double xc, double yc, double r) {
            this.xc = xc;
            this.yc = yc;
            this.r = r;
        }

        public boolean contains(double x, double y) {

            double dx = x - xc;
            double dy = y - yc;
            double dist = Math.sqrt(dx*dx + dy*dy);
            return Math.abs(r - dist) <= 3;
        }
    }

}

