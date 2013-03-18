/**
 * Copyright (c) 2010-2011 by Fred Hutchinson Cancer Research Center.  All Rights Reserved.

 * This software is licensed under the terms of the GNU Lesser General
 * Public License (LGPL), Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.

 * THE SOFTWARE IS PROVIDED "AS IS." FRED HUTCHINSON CANCER RESEARCH CENTER MAKES NO
 * REPRESENTATIONS OR WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED,
 * INCLUDING, WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS,
 * WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL FRED HUTCHINSON CANCER RESEARCH
 * CENTER OR ITS TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR
 * ANY DAMAGES OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR
 * CONSEQUENTIAL DAMAGES, ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS,
 * REGARDLESS OF  WHETHER FRED HUTCHINSON CANCER RESEARCH CENTER SHALL BE ADVISED,
 * SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */
package org.broad.igv.renderer;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.log4j.Logger;
import org.broad.igv.feature.IExon;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.feature.SpliceJunctionFeature;
import org.broad.igv.sam.AlignmentDataManager;
import org.broad.igv.sam.AlignmentInterval;
import org.broad.igv.sam.CoverageTrack;
import org.broad.igv.track.RenderContext;
import org.broad.igv.track.Track;
import org.broad.igv.ui.FontManager;
import org.broad.tribble.Feature;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

/**
 * Renderer for splice junctions. Draws a filled-in arc for each junction, with the width of the
 * arc representing the depth of coverage. If coverage information is present for the flanking
 * regions, draws that, too; otherwise indicates flanking regions with rectangles
 *
 * @author dhmay
 */
public class SashimiJunctionRenderer extends IGVFeatureRenderer {

    private static Logger log = Logger.getLogger(SashimiJunctionRenderer.class);

    Color ARC_COLOR_POS = new Color(150, 50, 50, 140); //transparent dull red

    private Color color = ARC_COLOR_POS;

    //central horizontal line color
    Color COLOR_CENTERLINE = new Color(0, 0, 0, 100);

    //maximum depth that can be displayed, due to track height limitations. Junctions with
    //this depth and deeper will all look the same
    protected int DEFAULT_MAX_DEPTH = 50;
    protected int maxDepth = DEFAULT_MAX_DEPTH;
    private ShapeType shapeType = ShapeType.TEXT;
    private Set<IExon> selectedExons;

    private CoverageTrack coverageTrack = null;
    private AlignmentDataManager dataManager = null;

    private Color background;

    /**
     * We want the features to alternate above and below, but don't want
     * the arcs to switch around when zooming /panning. So we store the above/below
     * status from the first rendering, and keep using that. This won't necessarily persist
     * between window openings, don't care.
     */
    private Map<Feature, Boolean> drawFeatureAbove = null;
    private Map<Integer, Integer> yOffsetMap = new HashMap<Integer, Integer>();

    public void setSelectedExons(Set<IExon> selectedExons) {
        this.selectedExons = selectedExons;
    }

    /**
     * Set the data manager and coverage track. This is so we can render
     * coverage
     * @param dataManager
     */
    public void setDataManager(AlignmentDataManager dataManager) {
        this.dataManager = dataManager;
        this.setCoverageTrack(dataManager.getCoverageTrack());
    }

    public AlignmentDataManager getDataManager(){
        return this.dataManager;
    }

    public void setBackground(Color background) {
        this.background = background;
    }

    public enum ShapeType{
        CIRCLE,
        ELLIPSE,
        TEXT
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public void setShapeType(ShapeType shapeType){
        this.shapeType = shapeType;
    }

    public ShapeType getShapeType() {
        return shapeType;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
        if(this.coverageTrack != null) this.coverageTrack.setColor(color);
    }

    private void setCoverageTrack(CoverageTrack covTrack) {
        this.coverageTrack = new CoverageTrack(covTrack);
        this.coverageTrack.setColor(color);
        //Don't want to color SNPs
        coverageTrack.setSnpThreshold(2.0f);
        coverageTrack.setAutoScale(true);
        coverageTrack.rescale();
    }



    /**
     * Note:  assumption is that featureList is sorted by pStart position.
     *
     * @param featureList
     * @param context
     * @param trackRectangle
     * @param track
     */
    @Override
    public void render(List<IGVFeature> featureList,
                       RenderContext context,
                       Rectangle trackRectangle,
                       Track track) {

        this.setColor(track.getColor());

        Rectangle coverageRectangle = new Rectangle(trackRectangle);

        if(coverageTrack != null){
            //Only want the coverage track to go so high so that the arcs still have room
            int newHeight = coverageRectangle.height / 2;
            int newY = coverageRectangle.y + coverageRectangle.height / 2 - newHeight;
            coverageRectangle.setBounds(coverageRectangle.x, newY, coverageRectangle.width, newHeight);

            coverageTrack.render(context, coverageRectangle);
        }

        double origin = context.getOrigin();
        double locScale = context.getScale();

        if ((featureList != null) && !featureList.isEmpty()) {

            // Create a graphics object to draw feature names.  Graphics are not cached
            // by font, only by color, so its necessary to create a new one to prevent
            // affecting other tracks.
            Font font = FontManager.getFont(track.getFontSize());
            Graphics2D fontGraphics = (Graphics2D) context.getGraphic2DForColor(Color.BLACK).create();
            fontGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            fontGraphics.setFont(font);

            // Track coordinates
            double trackRectangleX = trackRectangle.getX();
            double trackRectangleMaxX = trackRectangle.getMaxX();

            //Draw track name
            fontGraphics.drawString(track.getName(), (int) (trackRectangleMaxX * 0.85), (int) (trackRectangle.getY() + font.getSize()));

            // Draw the lines that represent the bounds of
            // a feature's region
            Set<IExon> locselectedExons = selectedExons;

            if(drawFeatureAbove == null) drawFeatureAbove = new HashMap<Feature, Boolean>(featureList.size());
            boolean lastDrawAbove = true;
            for (IGVFeature feature : featureList) {
                SpliceJunctionFeature junctionFeature = (SpliceJunctionFeature) feature;
                Boolean drawAbove = drawFeatureAbove.get(junctionFeature);
                if(drawAbove == null) {
                    drawAbove = !lastDrawAbove;
                    drawFeatureAbove.put(junctionFeature, drawAbove);
                }

                // Get the pStart and pEnd of the entire feature.  at extreme zoom levels the
                // virtual pixel value can be too large for an int, so the computation is
                // done in double precision and cast to an int only when its confirmed its
                // within the field of view.
                //int flankingStart = junctionFeature.getStart();
                //int flankingEnd = junctionFeature.getEnd();

                int junctionStart = junctionFeature.getJunctionStart();
                int junctionEnd = junctionFeature.getJunctionEnd();

                //Only show arcs for the selected feature, if applicable
                if (locselectedExons != null && locselectedExons.size() > 0) {
                    boolean inSelected = false;
                    for(IExon selectedExon: locselectedExons){
                        if((junctionStart >= selectedExon.getStart() && junctionStart <= selectedExon.getEnd())
                                || (junctionEnd >= selectedExon.getStart() && junctionEnd <= selectedExon.getEnd())){
                            inSelected = true;
                            break;
                        }
                    }

                    if(!inSelected) continue;
                }

                //double virtualPixelStart = Math.round((flankingStart - origin) / locScale);
                //double virtualPixelEnd = Math.round((flankingEnd - origin) / locScale);

                double virtualPixelJunctionStart = Math.round((junctionStart - origin) / locScale);
                double virtualPixelJunctionEnd = Math.round((junctionEnd - origin) / locScale);

                // If the any part of the feature fits in the track rectangle draw it
                if ((virtualPixelJunctionEnd >= trackRectangleX) && (virtualPixelJunctionStart <= trackRectangleMaxX)) {

                    //int displayPixelEnd = (int) Math.min(trackRectangleMaxX, virtualPixelEnd);
                    //int displayPixelStart = (int) Math.max(trackRectangleX, virtualPixelStart);

                    int depth = junctionFeature.getJunctionDepth();
                    Color color = feature.getColor();

                    //Calculate the height of the coverage track. This doesn't need to be exact,
                    //but we want the arcs to start at roughly the top of the coverage
                    int pixelYstart = 0;
                    int pixelYend = 0;

                    if(coverageTrack != null){
                        pixelYstart = getYOffset(coverageRectangle, coverageTrack.getDataRange() ,getCoverage(junctionStart));
                        pixelYend = getYOffset(coverageRectangle, coverageTrack.getDataRange() ,getCoverage(junctionEnd));
                    }


                    drawFeature(pixelYstart, pixelYend,
                            (int) virtualPixelJunctionStart, (int) virtualPixelJunctionEnd, depth,
                            trackRectangle, context, color, drawAbove);
                    lastDrawAbove = drawAbove;
                }
            }

            //draw a central horizontal line
            Graphics2D g2D = context.getGraphic2DForColor(COLOR_CENTERLINE);
            g2D.drawLine((int) trackRectangleX, (int) trackRectangle.getCenterY(),
                    (int) trackRectangleMaxX, (int) trackRectangle.getCenterY());


        }
    }

    /**
     * Get the coverage around this approximate genome position. We actually look
     * for a maximum around a certain window. This is intended for plotting, we just
     * want the arc to look like it's coming from the top
     * @param genomePos
     * @return
     */
    private int getCoverage(int genomePos) {
//        Integer yOffset = yOffsetMap.get(genomePos);
//        if(yOffset != null) return yOffset;

        if(dataManager == null) return 0;
        Collection<AlignmentInterval> intervals = dataManager.getLoadedIntervals();
        if(intervals == null) return 0;

        int buffer = 4;
        int coverage = 0;
        for(AlignmentInterval interval: intervals){
            if(interval.contains(interval.getChr(), genomePos - buffer, genomePos + buffer)){
                for(int loc= genomePos - buffer; loc < genomePos + buffer; loc++){
                    coverage = Math.max(coverage, interval.getTotalCount(loc));
                }
                return coverage;
            }
        }

        return 0;
    }

    private int getYOffset(Rectangle rect, DataRange range, int totalCount){
        //int pY = (int) rect.getMaxY() - 1;
        double maxRange = range.isLog() ? Math.log10(range.getMaximum()) : range.getMaximum();
        double tmp = range.isLog() ? Math.log10(totalCount) / maxRange : totalCount / maxRange;
        int height = (int) (tmp * rect.height);

        height = Math.min(height, rect.height - 1);
        return -height;
    }

    /**
     * Draw a filled arc representing a single feature. The thickness and height of the arc are proportional to the
     * depth of coverage.  Some of this gets a bit arcane -- the result of lots of visual tweaking.
     *
     * @param pixelYStartOffset    the y offset from center line that the arc should start at
     * @param pixelYEndOffset      thy y offset from center line that the arc should end at
     * @param pixelJunctionStart the starting position of the junction, whether on-screen or not
     * @param pixelJunctionEnd   the ending position of the junction, whether on-screen or not
     * @param depth              coverage depth
     * @param trackRectangle
     * @param context
     * @param featureColor       the color specified for this feature.  May be null.
     * @param drawAbove         Whether to draw the arc above or below the centerline
     */
    protected void drawFeature(int pixelYStartOffset, int pixelYEndOffset,
                               int pixelJunctionStart, int pixelJunctionEnd, int depth,
                               Rectangle trackRectangle, RenderContext context, Color featureColor,
                               boolean drawAbove) {

        //If the feature color is specified, use it, except that we set our own alpha depending on whether
        //the feature is highlighted.  Otherwise default based on strand and highlight.
        Color color;
        if (featureColor != null) {
            int r = featureColor.getRed();
            int g = featureColor.getGreen();
            int b = featureColor.getBlue();
            int alpha = 140;
            color = new Color(r, g, b, alpha);
        } else {
            color = this.color;
        }

        //Height of top of an arc of maximum depth
        int maxPossibleArcHeight = (trackRectangle.height - 1) / 8;
        int arcHeight = maxPossibleArcHeight;
        int minY = (int) trackRectangle.getCenterY() + Math.min(pixelYStartOffset - arcHeight, pixelYEndOffset - arcHeight);
        //Check if arc goes too high. All arcs going below have the same height,
        //so no need to check case-by-case
        if (drawAbove && minY < trackRectangle.getMinY()) {
            drawAbove = false;
        }

        //float depthProportionOfMax = Math.min(1, depth / maxDepth);
        int effDepth = Math.min(maxDepth, depth);

        //We adjust up or down depending on whether drawing up or down
        int yPosModifier = drawAbove ? -1 : 1;

        int arcBeginY = (int) trackRectangle.getCenterY() + yPosModifier + (drawAbove ? pixelYStartOffset - 2 : 0);
        int arcEndY = (int) trackRectangle.getCenterY() + yPosModifier + (drawAbove ? pixelYEndOffset - 2 : 0);


        //We use corners of a square as control points because why not
        //The control point is never actually reached
        int arcControlPeakY = arcBeginY + yPosModifier * arcHeight;


        GeneralPath arcPath = new GeneralPath();
        arcPath.moveTo(pixelJunctionStart, arcBeginY);
        arcPath.curveTo(pixelJunctionStart, arcControlPeakY,
                        pixelJunctionEnd, arcControlPeakY,
                pixelJunctionEnd, arcEndY);

        Graphics2D g2D = context.getGraphic2DForColor(color);
        g2D.setBackground(background);

        double minStrokeSize = 0.1f;
        double maxStrokeSize = 3.0f;

        double strokeSize = maxStrokeSize;

        //Setting maxDepth to 1 should just max everything out, but 1/0 tends
        //to make things crash
        if(maxDepth >= 2){
            double scale = (maxStrokeSize - minStrokeSize) / Math.log(maxDepth);
            strokeSize = scale * Math.log(effDepth) + minStrokeSize;
        }

        Stroke stroke = new BasicStroke((float) strokeSize);
        g2D.setStroke(stroke);
        g2D.draw(arcPath);

        float midX = ((float) pixelJunctionStart + (float) pixelJunctionEnd) / 2;

//        //TODO Format number
//        Graphics2D strGraphics = context.getGraphic2DForColor(Color.black);
//        strGraphics.drawString("" + depth, midX, arcPeakY);

        double actArcPeakY = arcBeginY + yPosModifier * Math.pow(0.5, 3) * (6) * arcHeight;

        //Draw shape to indicate depth
        float maxPossibleShapeHeight = maxPossibleArcHeight / 2;

        Shape shape = null;
        switch(shapeType){
//            case CIRCLE:
//                shape = createDepthCircle(maxPossibleShapeHeight, depthProportionOfMax, midX, actArcPeakY);
//                break;
//            case ELLIPSE:
//                shape = createDepthEllipse(maxPossibleShapeHeight, depthProportionOfMax, midX, actArcPeakY);
//                break;
            case TEXT:
                String text = "" + depth;
                Rectangle2D textBounds = g2D.getFontMetrics().getStringBounds(text, g2D);

                float floatX = (float) (midX - textBounds.getWidth() / 2);
                float floatY = (float) actArcPeakY + (float) textBounds.getHeight() / 2;

                //Clear background so we aren't drawing numbers over arcs
                int rectHeight = (int) textBounds.getHeight();
                g2D.clearRect((int) floatX,(int) floatY - rectHeight,(int) textBounds.getWidth(), rectHeight);

                g2D.drawString(text, floatX , floatY);

                break;
        }

        if(shape != null){
            g2D.draw(shape);
            g2D.fill(shape);
        }
    }

    private Shape createDepthEllipse(double maxPossibleShapeHeight, double depthProportionOfMax, double arcMidX, double actArcPeakY){
        double w = 5f;
        double x = arcMidX - w/2;

        double h = maxPossibleShapeHeight * depthProportionOfMax;

        //The ellipse is always specified from the top left corner
        double y = actArcPeakY - h / 2;

        return new Ellipse2D.Double(x, y, w, h);
    }

    private Shape createDepthCircle(double maxPossibleShapeHeight, double depthProportionOfMax, double arcMidX, double actArcPeakY){

        double h = maxPossibleShapeHeight * Math.sqrt(depthProportionOfMax);
        double w = h;
        double x = arcMidX - w/2;

        //The ellipse is always specified from the top left corner
        double y = actArcPeakY - h / 2;

        return new Ellipse2D.Double(x, y, w, h);
    }


}
