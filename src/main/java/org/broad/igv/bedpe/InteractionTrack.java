package org.broad.igv.bedpe;

import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.Range;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.jbrowse.CircularViewUtilities;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.renderer.GraphicUtils;
import org.broad.igv.track.AbstractTrack;
import org.broad.igv.track.RenderContext;
import org.broad.igv.track.TrackClickEvent;
import org.broad.igv.track.TrackMenuUtils;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.Downsampler;
import org.broad.igv.util.FeatureCache;
import org.broad.igv.util.ResourceLocator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;

import static org.broad.igv.bedpe.InteractionTrack.Direction.UP;

/**
 * Created by jrobinso on 6/29/18.
 */
public class InteractionTrack extends AbstractTrack {

    private static Logger log = LogManager.getLogger(InteractionTrack.class);
    public static final int MAX_WG_COUNT = 10000;
    private JCheckBoxMenuItem autoscaleCB;
    private JMenuItem maxScoreItem;
    private List<BedPE> wgFeatures;

    // TODO -- for jbrowse experiment
    private List<BedPE> allFeatures;

    enum Direction {UP, DOWN}

    enum GraphType {BLOCK, NESTED_ARC, PROPORTIONAL_ARC}

    enum ArcOption {ALL, ONE_END, BOTH_ENDS}

    private Genome genome;
    InteractionTrack.Direction direction = UP; //DOWN;
    GraphType graphType;  // GraphType.block; //
    private ArcOption arcOption = ArcOption.ALL;
    int thickness = 1;
    boolean autoscale = true;
    double maxScore = -1;
    int gap = 5;
    boolean showBlocks = false;


    //private Map<String, List<BedPE>> featureMap;
    private Map<GraphType, BedPERenderer> renderers;
    private FeatureCache<BedPE> featureCache;

    public InteractionTrack() {
    }

    public InteractionTrack(ResourceLocator locator, BedPEParser.Dataset dataset, Genome genome) {

        super(locator);
        init(dataset, genome);
        this.genome = genome;
        setHeight(250, true);
        setColor(new Color(180, 25, 137));

        renderers = new HashMap<>();
        renderers.put(GraphType.NESTED_ARC, new NestedArcRenderer(this));
        renderers.put(GraphType.PROPORTIONAL_ARC, new ProportionalArcRenderer(this));
        renderers.put(GraphType.BLOCK, new PEBlockRenderer(this));

        String typeString = PreferencesManager.getPreferences().get(Constants.ARC_TYPE);
        if (typeString != null) {
            try {
                graphType = GraphType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                log.error("Illegal graph type: " + typeString, e);
                graphType = GraphType.NESTED_ARC; // default
            }
        } else {
            graphType = dataset.type == BedPEParser.DatasetType.TENX ? GraphType.PROPORTIONAL_ARC : GraphType.NESTED_ARC;
        }


        String directionString = PreferencesManager.getPreferences().get(Constants.ARC_DIRECTION);
        if (directionString != null) {
            try {
                direction = Direction.valueOf(directionString);
            } catch (IllegalArgumentException e) {
                log.error("Illegal arc direction: " + directionString, e);
                direction = UP; // default
            }
        } else {
            direction = UP;
        }

        String blockString = PreferencesManager.getPreferences().get(Constants.ARC_BLOCKS);
        if (blockString != null) {
            try {
                showBlocks = Boolean.valueOf(blockString);
            } catch (IllegalArgumentException e) {
                log.error("Illegal arc blocks option: " + blockString, e);
            }
        }
    }

    private void init(BedPEParser.Dataset dataset, Genome genome) {

        List<BedPE> featureList = dataset.features;

        featureCache = new FeatureCache<>(featureList, 50);

        wgFeatures = createWGFeatures(featureList, genome);

        allFeatures = featureList;

        // Compute viz window based on feature density
        int vw = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : dataset.featureCounts.entrySet()) {
            String chr = entry.getKey();
            Chromosome chromosome = genome.getChromosome(chr);
            if (chromosome != null) {
                double f = 100000.0 / entry.getValue();
                vw = Math.min(vw, (int) (f * chromosome.getLength()));
            }
        }

        this.visibilityWindow = vw;
    }

    protected boolean isShowFeatures(ReferenceFrame frame) {

        if (frame.getChrName().equals(Globals.CHR_ALL)) {
            return true;
        } else {
            double windowSize = frame.getEnd() - frame.getOrigin();
            int vw = getVisibilityWindow();
            return (vw <= 0 || windowSize <= vw);
        }
    }


    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {
        return true;
    }

    @Override
    public void load(ReferenceFrame frame) {
        // Nothing to do, this track is pre-loaded
    }

    private List<BedPE> getFeaturesOverlapping(String chr, double start, double end) {

        if (chr.equals(Globals.CHR_ALL)) {
            return wgFeatures;
        } else {
            return featureCache.getFeatures(chr, (int) start, (int) end);
        }
    }

    @Override
    public void render(RenderContext context, Rectangle trackRectangle) {

        Graphics2D g2d = context.getGraphics();
        Rectangle clip = new Rectangle(g2d.getClip().getBounds());
        g2d.setClip(trackRectangle.intersection(clip.getBounds()));
        context.clearGraphicsCache();

        if (!isShowFeatures(context.getReferenceFrame())) {
            String message = "Zoom in to see features, or right-click to increase Feature Visibility Window.";
            GraphicUtils.drawCenteredText(message, trackRectangle, context.getGraphics());
            return;
        }

        try {
            String chr = context.getReferenceFrame().getChrName();
            List<BedPE> features = getFeaturesOverlapping(chr, context.getOrigin(), context.getEndLocation());
            if (features != null && features.size() > 0) {

                if (graphType == GraphType.PROPORTIONAL_ARC) {
                    if (autoscale || maxScore <= 0) {
                        maxScore = autoscale(features);
                    }
                    drawScale(context, trackRectangle);
                }

                renderers.get(graphType).render(features, context, trackRectangle, this.arcOption);
            }
            if (showBlocks) {
                renderers.get(GraphType.BLOCK).render(features, context, trackRectangle, this.arcOption);
            }

        } finally {
            context.clearGraphicsCache();
            g2d.setClip(clip);
        }

    }

    /**
     * Draw scale in top left of rectangle
     *
     * @param context
     * @param arect
     */
    public void drawScale(RenderContext context, Rectangle arect) {
        if (context.multiframe == false) {
            Graphics2D g = context.getGraphic2DForColor(Color.black);
            Font font = g.getFont();
            Font smallFont = FontManager.getFont(8);
            try {
                g.setFont(smallFont);
                String minString = "0";
                String fmtString = maxScore > 10 ? "%.0f" : "%.2f";
                String maxString = String.format(fmtString, maxScore);
                String scale = "[" + minString + " - " + maxString + "]";
                g.drawString(scale, arect.x + 5, arect.y + 10);

            } finally {
                g.setFont(font);
            }
        }
    }


    /**
     * Autoscale max height -- specific to proportional arc mode
     *
     * @param features
     */
    private double autoscale(List<BedPE> features) {
        double maxScore = 0;
        for (BedPE f : features) {
            maxScore = Math.max(maxScore, f.getScore());
        }
        return maxScore;
    }


    @Override
    public IGVPopupMenu getPopupMenu(TrackClickEvent te) {

        IGVPopupMenu menu = new IGVPopupMenu();


        // Experimental JBrowse.
        if (PreferencesManager.getPreferences().getAsBoolean(Constants.CIRC_VIEW_ENABLED) && CircularViewUtilities.ping()) {
            menu.addSeparator();
            JMenuItem item = new JMenuItem("Add Features to Circular View");
            item.addActionListener(e -> {
                List<ReferenceFrame> frames = te.getFrame() != null ?
                        Arrays.asList(te.getFrame()) :
                        FrameManager.getFrames();
                List<? extends BedPE> visibleFeatures = getVisibleFeatures(frames);
                CircularViewUtilities.sendBedpeToJBrowse(visibleFeatures, InteractionTrack.this.getName(), InteractionTrack.this.getColor());
            });
            menu.add(item);
            menu.addSeparator();
        }

        menu.add(TrackMenuUtils.getTrackRenameItem(Collections.singleton(InteractionTrack.this)));

        JMenuItem item = new JMenuItem("Set Track Height...");
        item.addActionListener(evt -> TrackMenuUtils.changeTrackHeight(Collections.singleton(InteractionTrack.this)));
        menu.add(item);

        item = new JMenuItem("Set Track Color...");
        item.addActionListener(evt -> TrackMenuUtils.changeTrackColor(Collections.singleton(InteractionTrack.this)));
        menu.add(item);


        menu.addSeparator();
        menu.add(new JLabel("<html><b>Graph Type</b>"));
        //enum GraphType {BLOCK, ARC, PROPORTIONAL_ARC}
        ButtonGroup group = new ButtonGroup();
        Map<String, GraphType> modes = new LinkedHashMap<>(4);
        modes.put("Nested Arcs", GraphType.NESTED_ARC);
        modes.put("Proportional Arcs", GraphType.PROPORTIONAL_ARC);
        //modes.put("Blocks", GraphType.BLOCK);

        for (final Map.Entry<String, GraphType> entry : modes.entrySet()) {
            JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
            mm.setSelected(InteractionTrack.this.graphType == entry.getValue());
            mm.addActionListener(evt -> {
                setGraphType(entry.getValue());
                PreferencesManager.getPreferences().put(Constants.ARC_TYPE, entry.getValue().toString());
                autoscaleCB.setEnabled(graphType == GraphType.PROPORTIONAL_ARC);
                maxScoreItem.setEnabled(graphType == GraphType.PROPORTIONAL_ARC);
                repaint();
            });
            group.add(mm);
            menu.add(mm);
        }

        menu.addSeparator();
        menu.add(new JLabel("<html><b>Arcs</b>"));
        ButtonGroup group2 = new ButtonGroup();
        Map<String, ArcOption> modes2 = new LinkedHashMap<>(4);
        modes2.put("All", ArcOption.ALL);
        modes2.put("One End In View", ArcOption.ONE_END);
        modes2.put("Both Ends In View", ArcOption.BOTH_ENDS);
        //modes.put("Blocks", GraphType.BLOCK);

        for (final Map.Entry<String, ArcOption> entry : modes2.entrySet()) {
            JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
            mm.setSelected(InteractionTrack.this.arcOption == entry.getValue());
            mm.addActionListener(evt -> {
                InteractionTrack.this.arcOption = (entry.getValue());
                repaint();
            });
            group2.add(mm);
            menu.add(mm);
        }

        menu.addSeparator();
        JCheckBoxMenuItem showBlocksCB = new JCheckBoxMenuItem("Show Blocks");
        showBlocksCB.setSelected(showBlocks);
        showBlocksCB.addActionListener(e -> {
            showBlocks = showBlocksCB.isSelected();
            PreferencesManager.getPreferences().put(Constants.ARC_BLOCKS, String.valueOf(showBlocksCB.isSelected()));
            repaint();
        });
        menu.add(showBlocksCB);

        menu.addSeparator();
        autoscaleCB = new JCheckBoxMenuItem("Autoscale");
        autoscaleCB.setSelected(autoscale);
        autoscaleCB.addActionListener(e -> {
            autoscale = autoscaleCB.isSelected();
            repaint();
        });
        menu.add(autoscaleCB);

        maxScoreItem = new JMenuItem("Set Max Score...");
        maxScoreItem.addActionListener(e -> {
            String maxScoreString = MessageUtils.showInputDialog("Enter maximum score:", String.valueOf(InteractionTrack.this.maxScore));
            if (maxScoreString != null) {
                try {
                    double ms = Double.parseDouble(maxScoreString);
                    if (ms > 0) {
                        maxScore = ms;
                        autoscale = false;
                        repaint();
                    } else {
                        MessageUtils.showMessage("maximum score must be > 0");
                    }
                } catch (NumberFormatException e1) {
                    MessageUtils.showMessage("maximum score must be a number");
                }
            }
        });
        menu.add(maxScoreItem);

        autoscaleCB.setEnabled(graphType == GraphType.PROPORTIONAL_ARC);
        maxScoreItem.setEnabled(graphType == GraphType.PROPORTIONAL_ARC);


        menu.addSeparator();
        item = new JMenuItem("Toggle Arc Orientation");
        item.addActionListener(evt -> {
            if (direction == UP) {
                direction = Direction.DOWN;
            } else {
                direction = UP;
            }
            repaint();
        });
        menu.add(item);

        item = new JMenuItem("Set Line Thickness...");
        item.addActionListener(e -> {
            String t = MessageUtils.showInputDialog("Line thickness", String.valueOf(thickness));
            if (t != null) {
                try {
                    thickness = Integer.parseInt(t);
                    repaint();
                } catch (NumberFormatException e1) {
                    MessageUtils.showErrorMessage("Line thickness must be an integer", e1);
                }
            }
        });
        menu.add(item);

        menu.addSeparator();
        menu.add(TrackMenuUtils.getChangeFeatureWindow(Arrays.asList(this)));

//        final JCheckBoxMenuItem cbItem = new JCheckBoxMenuItem("Hide Large Features");
//        cbItem.setSelected(hideLargeFeatures);
//        cbItem.addActionListener(e -> {
//            InteractionTrack.this.hideLargeFeatures = cbItem.isSelected();
//            IGV.getInstance().repaint();
//        });
//

        return menu;
    }

    private void setGraphType(GraphType value) {
        // TODO adjust height
        this.graphType = value;
    }

    @Override
    public String getValueStringAt(String chr, double position, int mouseX, int mouseY, ReferenceFrame frame) {

        // Expand range a little bit -- this should be done in pixels
        double tolerance = frame.getScale() * 3;
        List<BedPE> candidates = getFeaturesOverlapping(frame.getChrName(), (int) position - tolerance, (int) position + 1 + tolerance);

        // Sort candidate features smallest to largest
        Comparator<BedPE> sorter = graphType == GraphType.PROPORTIONAL_ARC ?
                (o1, o2) -> {
                    double score1 = o1.getScore();
                    double score2 = o2.getScore();
                    if (score1 > score2) return 1;
                    else if (score1 < score2) return -1;
                    else return 0;
                } :
                (o1, o2) -> {
                    double d1 = o1.getCenterDistance();
                    double d2 = o2.getCenterDistance();
                    if (d1 > d2) return 1;
                    else if (d1 < d2) return -1;
                    else return 0;
                };

        Collections.sort(candidates, sorter);

        for (BedPE f : candidates) {
            BedPEShape s = f.getShape();
            if (s == null || s.contains(mouseX, mouseY)) {
                return f.getValueString();
            }
        }

        return super.getValueStringAt(chr, position, mouseX, mouseY, frame);
    }

    @Override
    public void marshalXML(Document document, Element element) {

        super.marshalXML(document, element);

        element.setAttribute("direction", String.valueOf(direction));
        element.setAttribute("thickness", String.valueOf(thickness));
        element.setAttribute("graphType", String.valueOf(graphType));
        element.setAttribute("arcOption", String.valueOf(arcOption));
        element.setAttribute("showBlocks", String.valueOf(showBlocks));
        element.setAttribute("autoscale", String.valueOf(autoscale));
        if (!autoscale) {
            element.setAttribute("maxScore", String.valueOf(maxScore));
        }

    }

    @Override
    public void unmarshalXML(Element element, Integer version) {

        super.unmarshalXML(element, version);

        if (element.hasAttribute("arcOption")) {
            String typeString = element.getAttribute("arcOption").toUpperCase();
            this.arcOption = ArcOption.valueOf(typeString);
        }
        if (element.hasAttribute("direction"))
            this.direction = Direction.valueOf(element.getAttribute("direction"));
        if (element.hasAttribute("thickness"))
            this.thickness = Integer.parseInt(element.getAttribute("thickness"));
        if (element.hasAttribute("graphType")) {
            String typeString = element.getAttribute("graphType").toUpperCase();
            if (typeString.equals("ARC")) typeString = "NESTED_ARC";  // backward compatibility
            this.graphType = GraphType.valueOf(typeString);
        }
        if (element.hasAttribute("showBlocks"))
            this.showBlocks = Boolean.parseBoolean(element.getAttribute("showBlocks"));
        if (element.hasAttribute("autoscale")) {
            this.autoscale = Boolean.parseBoolean(element.getAttribute("autoscale"));
        }
        if (element.hasAttribute("maxScore")) {
            this.maxScore = Double.parseDouble(element.getAttribute("maxScore"));
        }
    }


    /**
     * Return features visible in the supplied frames
     */
    public List<? extends BedPE> getVisibleFeatures(List<ReferenceFrame> frames) {

        Function<ReferenceFrame, List<? extends BedPE>> frameFeatures = (f) -> {
            String chr = f.getChrName();
            if (chr.equals(Globals.CHR_ALL)) {
                return wgFeatures;
            } else {
                Range r = f.getCurrentRange();
                return getFeaturesOverlapping(chr, r.getStart(), r.getEnd());
            }
        };

        if (frames.size() == 0) {
            return Collections.emptyList();
        } else if (frames.size() == 1) {
            return frameFeatures.apply(frames.get(0));
        } else {
            List<BedPE> inView = new ArrayList<>();
            for (ReferenceFrame f : frames) {
                inView.addAll(frameFeatures.apply(f));
            }
            return inView;
        }
    }


    private List<BedPE> createWGFeatures(List<BedPE> features, Genome genome) {

        int size = Math.min(features.size(), MAX_WG_COUNT);
        List<BedPE> wgFeatures = new ArrayList<>(size);

        List<BedPE> sampledFeatures;
        if (features.size() < MAX_WG_COUNT) {
            sampledFeatures = features;
        } else {
            sampledFeatures = downsampleFeatures(features);
        }

        for (BedPE f : sampledFeatures) {

            BedPE wgFeature = new WGFeature(f, genome);

            wgFeatures.add(wgFeature);

        }
        return wgFeatures;
    }

    public static List<BedPE> downsampleFeatures(List<BedPE> features) {

        if (features.isEmpty()) {
            return Collections.EMPTY_LIST;
        }

        BedPE maxScoreFeature = features.stream()
                .max(Comparator.comparing(BedPE::getScore)).get();

        int nBins = maxScoreFeature.getScore() > 0 ? 5 : 1;  // TODO make a function of total # of features & maxCount?
        double binSize = nBins > 1 ? Math.log10(maxScoreFeature.getScore()) / nBins : Integer.MAX_VALUE;

        // Divide features into bins
        List<BedPE>[] binnedFeatures = new List[nBins];
        int counts[] = new int[nBins];
        for (int i = 0; i < nBins; i++) {
            binnedFeatures[i] = new ArrayList<>();
            counts[i] = 0;
        }
        for (BedPE f : features) {
            if (f.isComplement()) continue;
            int bin = f.getScore() <= 0 ? 0 : (int) Math.min(nBins - 1, Math.floor(Math.log10(f.getScore()) / binSize));
            binnedFeatures[bin].add(f);
            counts[bin]++;
        }

        // Add sampled features from each bin
        int featuresPerBin = MAX_WG_COUNT / nBins;
        List<BedPE> sampledFeatures = new ArrayList<>(MAX_WG_COUNT);
        for (int i = 0; i < nBins; i++) {
            List<BedPE> bfs = binnedFeatures[i];
            sampledFeatures.addAll(Arrays.asList(new Downsampler<BedPEFeature>().sample(bfs.toArray(BedPEFeature[]::new), featuresPerBin)));
        }

        // Be sure we keep the maximum feature
        if (maxScoreFeature != null) {
            sampledFeatures.add(maxScoreFeature);
        }

        return sampledFeatures;
    }
}

