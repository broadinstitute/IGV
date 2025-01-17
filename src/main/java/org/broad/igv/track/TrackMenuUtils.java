/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.track;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import htsjdk.tribble.Feature;
import org.apache.commons.math3.stat.StatUtils;
import org.broad.igv.bedpe.InteractionTrack;
import org.broad.igv.logging.*;
import org.broad.igv.Globals;
import org.broad.igv.data.AbstractDataSource;
import org.broad.igv.data.CombinedDataSource;
import org.broad.igv.feature.Range;
import org.broad.igv.feature.*;
import org.broad.igv.feature.basepair.BasePairTrack;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.IGVBEDCodec;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.renderer.*;
import org.broad.igv.sam.AlignmentDataManager;
import org.broad.igv.sam.AlignmentTrack;
import org.broad.igv.sam.CoverageTrack;
import org.broad.igv.sam.SAMWriter;
import org.broad.igv.ui.*;
import org.broad.igv.ui.action.OverlayTracksMenuAction;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.IGVPopupMenu;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.Pair;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.StringUtils;
import org.broad.igv.util.blat.BlatClient;
import org.broad.igv.util.extview.ExtendViewClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.List;
import java.util.*;

/**
 * @author jrobinso
 */
public class TrackMenuUtils {

    static Logger log = LogManager.getLogger(TrackMenuUtils.class);
    final static String LEADING_HEADING_SPACER = "  ";

    private static List<TrackMenuItemBuilder> trackMenuItems = new ArrayList<TrackMenuItemBuilder>();

    /**
     * Return a popup menu with items applicable to the collection of tracks.
     *
     * @param tracks
     * @return
     */
    public static IGVPopupMenu getPopupMenu(final Collection<Track> tracks, String title, TrackClickEvent te) {

        if (log.isDebugEnabled()) {
            log.debug("enter getPopupMenu");
        }

        IGVPopupMenu menu = new IGVPopupMenu();

        JLabel popupTitle = new JLabel(LEADING_HEADING_SPACER + title, JLabel.CENTER);
        popupTitle.setFont(FontManager.getFont(Font.BOLD, 12));
        if (popupTitle != null) {
            menu.add(popupTitle);
            menu.addSeparator();
        }

        addStandardItems(menu, tracks, te);

        return menu;

    }

    /**
     * Add menu items which have been added through the api, not known until runtime
     *
     * @param menu
     * @param tracks
     * @param te
     */
    public static void addPluginItems(JPopupMenu menu, Collection<Track> tracks, TrackClickEvent te) {
        List<JMenuItem> items = new ArrayList<JMenuItem>(0);
        for (TrackMenuItemBuilder builder : trackMenuItems) {
            JMenuItem item = builder.build(tracks, te);
            if (item != null) {
                items.add(item);
            }
        }

        if (items.size() > 0) {
            menu.addSeparator();
            for (JMenuItem item : items) {
                menu.add(item);
            }
        }
    }

    public static void addStandardItems(JPopupMenu menu, Collection<Track> tracks, TrackClickEvent te) {

        boolean hasDataTracks = false;
        boolean hasFeatureTracks = false;
        boolean hasOtherTracks = false;
        boolean hasCoverageTracks = false;
        for (Track track : tracks) {

            if (track instanceof DataTrack) {
                hasDataTracks = true;
            } else if (track instanceof CoverageTrack) {
                hasDataTracks = true;
                hasCoverageTracks = true;
            } else if (track instanceof FeatureTrack) {
                hasFeatureTracks = true;
            } else {
                hasOtherTracks = true;
            }
            if (hasDataTracks && hasFeatureTracks && hasOtherTracks) {
                break;
            }
        }

        boolean hasBasePairTracks = false;
        for (Track track : tracks) {
            if (track instanceof BasePairTrack) {
                hasBasePairTracks = true;
                break;
            }
        }
        if (hasBasePairTracks) {
            addBasePairItems(menu, tracks);
        }

        boolean featureTracksOnly = hasFeatureTracks && !hasDataTracks && !hasOtherTracks;
        boolean dataTracksOnly = !hasFeatureTracks && hasDataTracks && !hasOtherTracks;

        addSharedItems(menu, tracks, hasFeatureTracks, hasCoverageTracks);
        menu.addSeparator();
        if (dataTracksOnly) {
            addDataItems(menu, tracks, hasCoverageTracks);
        } else if (featureTracksOnly) {
            addFeatureItems(menu, tracks, te);
        }


    }

    public static void addZoomItems(JPopupMenu menu, final ReferenceFrame frame) {

        if (FrameManager.isGeneListMode()) {
            JMenuItem item = new JMenuItem("Reset panel to '" + frame.getName() + "'");
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    frame.reset();
                    // TODO -- paint only panels for this frame
                }
            });
            menu.add(item);
        }


        JMenuItem zoomOutItem = new JMenuItem("Zoom out");
        zoomOutItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                frame.doZoomIncrement(-1);
            }
        });
        menu.add(zoomOutItem);

        JMenuItem zoomInItem = new JMenuItem("Zoom in");
        zoomInItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                frame.doZoomIncrement(1);
            }
        });
        menu.add(zoomInItem);


    }


    /**
     * Return popup menu with items applicable to data tracks
     *
     * @return
     */
    public static void addDataItems(JPopupMenu menu, final Collection<Track> tracks, boolean hasCoverageTracks) {

        if (log.isTraceEnabled()) {
            log.trace("enter getDataPopupMenu");
        }

        if (!hasCoverageTracks) {


            // The "Points" renderer cannot be used with

            final String[] labels = {"Heatmap", "Bar Chart", "Points", "Line Plot"};
            final Class[] renderers = {HeatmapRenderer.class, BarChartRenderer.class,
                    PointsRenderer.class, LineplotRenderer.class
            };

            JLabel rendererHeading = new JLabel(LEADING_HEADING_SPACER + "Type of Graph", JLabel.LEFT);
            rendererHeading.setFont(FontManager.getFont(Font.BOLD, 12));

            menu.add(rendererHeading);

            // Get existing selections
            Set<Class> currentRenderers = new HashSet<Class>();
            for (Track track : tracks) {
                if (track.getRenderer() != null) {
                    currentRenderers.add(track.getRenderer().getClass());
                }
            }

            // Create renderer menu items
            for (int i = 0; i < labels.length; i++) {
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(labels[i]);
                final Class rendererClass = renderers[i];
                if (currentRenderers.contains(rendererClass)) {
                    item.setSelected(true);
                }
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        changeRendererClass(tracks, rendererClass);
                    }
                });
                menu.add(item);
            }
            menu.addSeparator();


            // Get intersection of all valid window functions for selected tracks
            Set<WindowFunction> avaibleWindowFunctions = new LinkedHashSet<>();
            avaibleWindowFunctions.addAll(AbstractDataSource.ORDERED_WINDOW_FUNCTIONS);
            for (Track track : tracks) {
                avaibleWindowFunctions.retainAll(track.getAvailableWindowFunctions());
            }

            // dataPopupMenu.addSeparator();
            // Collection all window functions for selected tracks
            WindowFunction currentWindowFunction = null;
            for (Track track : tracks) {
                final WindowFunction twf = track.getWindowFunction();
                if (currentWindowFunction == null) {
                    currentWindowFunction = twf;
                } else {
                    if (twf != currentWindowFunction) {
                        currentWindowFunction = null;     // Multiple window functions
                        break;
                    }
                }
            }

            if (avaibleWindowFunctions.size() > 0) {
                JLabel statisticsHeading = new JLabel(LEADING_HEADING_SPACER + "Windowing Function", JLabel.LEFT);
                statisticsHeading.setFont(FontManager.getFont(Font.BOLD, 12));

                menu.add(statisticsHeading);

                for (final WindowFunction wf : avaibleWindowFunctions) {
                    JCheckBoxMenuItem item = new JCheckBoxMenuItem(wf.getValue());

                    item.setSelected(currentWindowFunction == wf);

                    item.addActionListener(new ActionListener() {

                        public void actionPerformed(ActionEvent evt) {
                            changeStatType(wf.toString(), tracks);
                        }
                    });
                    menu.add(item);
                }

                menu.addSeparator();
            }
        }


        menu.add(getDataRangeItem(tracks));

        if (!hasCoverageTracks) menu.add(getHeatmapScaleItem(tracks));

        if (tracks.size() > 0) {
            menu.add(getLogScaleItem(tracks));
        }

        menu.add(getAutoscaleItem(tracks));

        if (tracks.size() > 1 || (tracks.size() == 1 && tracks.iterator().next() instanceof MergedTracks)) {
            menu.add(getGroupAutoscaleItem(tracks));
        }

        menu.add(getShowDataRangeItem(tracks));

        //Optionally add overlay track options
        Track firstTrack = tracks.iterator().next();
        boolean merged = (tracks.size() == 1 && firstTrack instanceof MergedTracks);

        if (tracks.size() > 1 || merged) {
            menu.addSeparator();

            final List<DataTrack> dataTrackList = Lists.newArrayList(Iterables.filter(tracks, DataTrack.class));
            final JMenuItem overlayGroups = new JMenuItem("Overlay Tracks");
            overlayGroups.addActionListener(e -> {
                OverlayTracksMenuAction.merge(dataTrackList, "Overlay");
                IGV.getInstance().repaint();
            });

            int numDataTracks = dataTrackList.size();
            overlayGroups.setEnabled(numDataTracks >= 2 && numDataTracks == tracks.size());
            menu.add(overlayGroups);

            // Enable "separateTracks" menu if selection is a single track, and that track is merged.

            JMenuItem unmergeItem = new JMenuItem("Separate Tracks");
            menu.add(unmergeItem);
            if (merged) {
                unmergeItem.addActionListener(e -> {
                    OverlayTracksMenuAction.unmerge(tracks);
                    IGV.getInstance().repaint();
                });
            } else {
                unmergeItem.setEnabled(false);
            }

            // Add color transparency item if merged
            if (merged) {
                Track firstTrack1 = tracks.iterator().next();
                final MergedTracks mergedTracks = (MergedTracks) firstTrack1;

                JMenuItem alphaItem = new JMenuItem("Adjust Transparency");
                alphaItem.addActionListener(e -> {
                    JDialog alphaDialog = mergedTracks.getAlphaDialog();
                    alphaDialog.setVisible(true);
                });
                menu.add(alphaItem);

            }
        }
    }

    /**
     * Return popup menu with items applicable to feature tracks
     *
     * @return
     */
    private static void addFeatureItems(JPopupMenu featurePopupMenu, final Collection<Track> tracks, TrackClickEvent te) {

        TrackMenuUtils.addGroupByStrandItem(tracks, featurePopupMenu);
        featurePopupMenu.addSeparator();

        addDisplayModeItems(tracks, featurePopupMenu);

        if (tracks.size() == 1) {
            Track t = tracks.iterator().next();
            Feature f = t.getFeatureAtMousePosition(te);

            ReferenceFrame frame = te.getFrame();
            if (frame == null && !FrameManager.isGeneListMode()) {
                frame = FrameManager.getDefaultFrame();
            }

            String featureName = "";
            if (f != null) {
                featurePopupMenu.addSeparator();
                featurePopupMenu.add(getCopyDetailsItem(f, te));

                // If we are over an exon, copy its sequence instead of the entire feature.
                Feature sequenceFeature = f;
                if (sequenceFeature instanceof IGVFeature) {
                    featureName = ((IGVFeature) sequenceFeature).getName();
                    double position = te.getChromosomePosition();
                    Collection<Exon> exons = ((IGVFeature) sequenceFeature).getExons();
                    if (exons != null) {
                        for (Exon exon : exons) {
                            if (position > exon.getStart() && position < exon.getEnd()) {
                                sequenceFeature = exon;
                                break;
                            }
                        }
                    }
                }

                featurePopupMenu.add(getCopySequenceItem(sequenceFeature));

                if (frame != null) {
                    Range r = frame.getCurrentRange();
                    featurePopupMenu.add(getExtendViewItem(featureName, sequenceFeature, r));
                }

                final JMenuItem blatItem = getBlatItem(sequenceFeature);
                featurePopupMenu.add(blatItem);
            }
        }

        featurePopupMenu.addSeparator();
        featurePopupMenu.add(getChangeFeatureWindow(tracks));

        featurePopupMenu.addSeparator();
        featurePopupMenu.add(getShowFeatureNames(tracks));
        featurePopupMenu.add(getFeatureNameAttribute(tracks));
    }

    /**
     * Return popup menu with items applicable to BasePairTrack(s)
     *
     * @author stevenbusan
     */
    public static void addBasePairItems(JPopupMenu menu, final Collection<Track> tracks) {

        final ArrayList<BasePairTrack> bpTracks = new ArrayList<BasePairTrack>();
        for (Track track : tracks) {
            if (track instanceof BasePairTrack) {
                bpTracks.add((BasePairTrack) track);
            }
        }

        JLabel arcColorHeading = new JLabel(LEADING_HEADING_SPACER + "Arc colors (click to change)", JLabel.LEFT);
        arcColorHeading.setFont(FontManager.getFont(Font.BOLD, 12));

        menu.add(arcColorHeading);

        // aggregate arc color selector/legends for multiple selected tracks
        ArrayList<Pair<Color, String>> legendList = new ArrayList<Pair<Color, String>>();
        HashSet<String> keys = new HashSet<String>();
        for (BasePairTrack track : bpTracks) {
            List<String> colors = track.getRenderOptions().getColors();
            List<String> colorLabels = track.getRenderOptions().getColorLabels();
            // iterate in reverse order so colors appearing first in list are the ones rendered on top
            for (int i = colors.size() - 1; i >= 0; --i) {
                String key = colors.get(i) + ' ' + colorLabels.get(i);
                if (!keys.contains(key)) {
                    keys.add(key);
                    legendList.add(new Pair<Color, String>(ColorUtilities.stringToColor(colors.get(i)), colorLabels.get(i)));
                }
            }
        }

        for (Pair<Color, String> pair : legendList) {
            final Color color = pair.getFirst();
            final String label = pair.getSecond();

            JLabel colorBox = new JLabel(LEADING_HEADING_SPACER);
            colorBox.setFont(FontManager.getFont(Font.BOLD, 12));
            colorBox.setForeground(color);

            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
            p.add(colorBox);
            p.add(Box.createHorizontalStrut(1));
            p.add(new JLabel(" " + label));
            p.add(Box.createGlue());
            p.setAlignmentX(Component.LEFT_ALIGNMENT);

            JMenuItem item = new JMenuItem();
            item.add(p);
            double w = p.getPreferredSize().getWidth();
            double h = p.getPreferredSize().getHeight();
            Dimension size = new Dimension();
            size.setSize(w, h + 8);
            item.setPreferredSize(size);

            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    changeBasePairTrackColor(bpTracks, color, label);
                }
            });

            menu.add(item);
        }

        menu.addSeparator();

        JLabel arcDirectionHeading = new JLabel(LEADING_HEADING_SPACER + "Arc direction", JLabel.LEFT);
        arcDirectionHeading.setFont(FontManager.getFont(Font.BOLD, 12));

        menu.add(arcDirectionHeading);

        // preselect up or down if all selected tracks have the same arc direction
        int upCount = 0;
        int downCount = 0;
        BasePairTrack.ArcDirection currentArcDirection = null; // mixed up and down
        for (BasePairTrack track : bpTracks) {
            if (track.getRenderOptions().getArcDirection() == BasePairTrack.ArcDirection.UP) ++upCount;
            if (track.getRenderOptions().getArcDirection() == BasePairTrack.ArcDirection.DOWN) ++downCount;
        }
        if (upCount == 0) currentArcDirection = BasePairTrack.ArcDirection.DOWN;
        if (downCount == 0) currentArcDirection = BasePairTrack.ArcDirection.UP;

        ButtonGroup group = new ButtonGroup();
        Map<String, BasePairTrack.ArcDirection> arcDirections = new LinkedHashMap<String, BasePairTrack.ArcDirection>(3);
        arcDirections.put("Up", BasePairTrack.ArcDirection.UP);
        arcDirections.put("Down", BasePairTrack.ArcDirection.DOWN);

        for (final Map.Entry<String, BasePairTrack.ArcDirection> entry : arcDirections.entrySet()) {
            JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
            mm.setSelected(currentArcDirection == entry.getValue());
            mm.addActionListener(evt -> {
                for (Track track : tracks) {
                    if (track instanceof BasePairTrack) {
                        ((BasePairTrack) track).getRenderOptions().setArcDirection(entry.getValue());
                    }
                }
                IGV.getInstance().repaint(tracks);
            });
            group.add(mm);
            menu.add(mm);
        }
        menu.addSeparator();
    }

    /**
     * Return a menu item which will export visible features
     * If {@code tracks} is not a single {@code FeatureTrack}, {@code null}
     * is returned (there should be no menu entry)
     *
     * @param tracks
     * @return
     */
    public static JMenuItem getExportFeatures(final Collection<Track> tracks, final ReferenceFrame frame) {
        Track ft = tracks.iterator().next();
        if (tracks.size() != 1) {
            return null;
        }
        JMenuItem exportData = null;

        if (ft instanceof FeatureTrack) {
            exportData = new JMenuItem("Export Features...");
            exportData.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File outFile = FileDialogUtils.chooseFile("Save Visible Data",
                            PreferencesManager.getPreferences().getLastTrackDirectory(),
                            new File("visibleData.bed"),
                            FileDialogUtils.SAVE);

                    exportVisibleFeatures(outFile.getAbsolutePath(), tracks, frame);
                }
            });
        } else if (ft instanceof AlignmentTrack) {
            exportData = new JMenuItem("Export Alignments...");
            exportData.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    File outFile = FileDialogUtils.chooseFile("Save Visible Data",
                            PreferencesManager.getPreferences().getLastTrackDirectory(),
                            new File("visibleData.sam"),
                            FileDialogUtils.SAVE);

                    int countExp = exportVisibleAlignments(outFile.getAbsolutePath(), tracks, frame);
                    String msg = String.format("%d reads written", countExp);
                    MessageUtils.setStatusBarMessage(msg);
                }
            });
        }

        return exportData;
    }

    static int exportVisibleAlignments(String outPath, Collection<Track> tracks, ReferenceFrame frame) {
        AlignmentTrack alignmentTrack = null;
        for (Track track : tracks) {
            if (track instanceof AlignmentTrack) {
                alignmentTrack = (AlignmentTrack) track;
                break;
            }
        }

        if (alignmentTrack == null) return -1;


        File outFile = new File(outPath);
        try {
            AlignmentDataManager dataManager = alignmentTrack.getDataManager();
            ResourceLocator inlocator = dataManager.getLocator();
            Range range = frame.getCurrentRange();

            //Read directly from file
            //return SAMWriter.writeAlignmentFilePicard(inlocator, outPath, range.getChr(), range.getStart(), range.getEnd());

            //Export those in memory, overlapping current view
            return SAMWriter.writeAlignmentFilePicard(dataManager, outFile, frame, range.getChr(), range.getStart(), range.getEnd());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }

    }

    /**
     * Write features in {@code track} found in {@code range} to {@code outPath},
     * BED format
     * TODO Move somewhere else? run on separate thread?  Probably shouldn't be here
     *
     * @param outPath
     * @param tracks
     * @param frame
     */
    static void exportVisibleFeatures(String outPath, Collection<Track> tracks, ReferenceFrame frame) {
        PrintWriter writer;
        try {
            writer = new PrintWriter(outPath);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        for (Track track : tracks) {
            if (track instanceof FeatureTrack) {
                FeatureTrack fTrack = (FeatureTrack) track;

                String trackLine = fTrack.getExportTrackLine();
                if (trackLine != null) {
                    writer.println(trackLine);
                }

                //Can't trust FeatureTrack.getFeatures to limit itself, so we filter
                List<Feature> features = fTrack.getVisibleFeatures(frame);
                IGVBEDCodec codec = new IGVBEDCodec();
                for (Feature feat : features) {
                    String featString = codec.encode(feat);
                    writer.println(featString);
                }
            }
        }
        writer.flush();
        writer.close();
    }


    /**
     * Popup menu with items applicable to both feature and data tracks
     *
     * @return
     */
    public static void addSharedItems(JPopupMenu menu, final Collection<Track> tracks, boolean hasFeatureTracks, boolean hasCoverageTracks) {

        //JLabel trackSettingsHeading = new JLabel(LEADING_HEADING_SPACER + "Track Settings", JLabel.LEFT);
        //trackSettingsHeading.setFont(boldFont);
        //menu.add(trackSettingsHeading);

        menu.add(getTrackRenameItem(tracks));

        String colorLabel = "Change Track Color...";
        JMenuItem item = new JMenuItem(colorLabel);
        item.addActionListener(evt -> changeTrackColor(tracks));
        menu.add(item);

        // Change track color by attribute
        item = new JMenuItem("Change Track Color (Negative Values or Strand)...");
        item.setToolTipText(
                "Change the alternate track color.  This color is used when drawing features with negative values or on the negative strand.");
        item.addActionListener(evt -> changeAltTrackColor(tracks));
        menu.add(item);

        menu.add(getChangeTrackHeightItem(tracks));
        menu.add(getChangeFontSizeItem(tracks));
    }


    private static void changeStatType(String statType, Collection<Track> selectedTracks) {
        for (Track track : selectedTracks) {
            track.setWindowFunction(WindowFunction.valueOf(statType));
        }
        IGV.getInstance().repaint(selectedTracks);
    }


    public static JMenuItem getTrackRenameItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Rename Track...");
        item.addActionListener(evt -> UIUtilities.invokeOnEventThread(() -> renameTrack(selectedTracks)));
        if (selectedTracks.size() > 1) {
            item.setEnabled(false);
        }
        return item;
    }

    private static JMenuItem getHeatmapScaleItem(final Collection<Track> selectedTracks) {

        JMenuItem item = new JMenuItem("Set Heatmap Scale...");

        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                if (selectedTracks.size() > 0) {

                    ContinuousColorScale colorScale = selectedTracks.iterator().next().getColorScale();
                    HeatmapScaleDialog dlg = new HeatmapScaleDialog(IGV.getInstance().getMainFrame(), colorScale);

                    dlg.setVisible(true);
                    if (!dlg.isCanceled()) {
                        colorScale = dlg.getColorScale();

                        // dlg.isFlipAxis());
                        for (Track track : selectedTracks) {
                            track.setColorScale(colorScale);
                        }
                        IGV.getInstance().repaint();
                    }

                }

            }
        });
        return item;
    }

    public static JMenuItem getDataRangeItem(final Collection<Track> selectedTracks) {
        JMenuItem item = new JMenuItem("Set Data Range...");

        item.addActionListener(evt -> {
            if (selectedTracks.size() > 0) {

                // Create a datarange that spans the extent of prev tracks range
                DataRange prevAxisDefinition = DataRange.getFromTracks(selectedTracks);
                DataRangeDialog dlg = new DataRangeDialog(IGV.getInstance().getMainFrame(), prevAxisDefinition);
                dlg.setVisible(true);
                if (!dlg.isCanceled()) {
                    float min = Math.min(dlg.getMax(), dlg.getMin());
                    float max = Math.max(dlg.getMin(), dlg.getMax());
                    float mid = dlg.getBase();
                    mid = Math.max(min, Math.min(mid, max));

                    DataRange axisDefinition = new DataRange(dlg.getMin(), mid, dlg.getMax(),
                            prevAxisDefinition.isDrawBaseline(), dlg.isLog());

                    for (Track track : selectedTracks) {
                        track.setDataRange(axisDefinition);
                        track.setAutoScale(false);
                        track.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
                    }
                    IGV.getInstance().repaint(selectedTracks);
                }

            }
        });

        return item;
    }


    public static JMenuItem getLogScaleItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute


        final JCheckBoxMenuItem logScaleItem = new JCheckBoxMenuItem("Log scale");
        final boolean logScale = selectedTracks.iterator().next().getDataRange().isLog();
        logScaleItem.setSelected(logScale);
        logScaleItem.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                DataRange.Type scaleType = logScaleItem.isSelected() ?
                        DataRange.Type.LOG :
                        DataRange.Type.LINEAR;
                for (Track t : selectedTracks) {
                    t.getDataRange().setType(scaleType);
                }
                IGV.getInstance().repaint(selectedTracks);
            }
        });

        return logScaleItem;
    }

    private static JMenuItem getAutoscaleItem(final Collection<Track> selectedTracks) {

        final JCheckBoxMenuItem autoscaleItem = new JCheckBoxMenuItem("Autoscale");
        if (selectedTracks.size() == 0) {
            autoscaleItem.setEnabled(false);

        } else {
            boolean autoScale = checkAutoscale(selectedTracks);

            autoscaleItem.setSelected(autoScale);
            autoscaleItem.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent evt) {

                    boolean autoScale = autoscaleItem.isSelected();
                    for (Track t : selectedTracks) {
                        t.setAutoScale(autoScale);
                        if (autoScale) {
                            t.removeAttribute(AttributeManager.GROUP_AUTOSCALE);
                        }
                    }
                    IGV.getInstance().repaint(selectedTracks);
                }
            });
        }
        return autoscaleItem;
    }

    private static JMenuItem getGroupAutoscaleItem(final Collection<Track> selectedTracks) {

        final JMenuItem autoscaleItem = new JMenuItem("Group Autoscale");

        autoscaleItem.addActionListener(evt -> {

            int nextAutoscaleGroup = IGV.getInstance().getSession().getNextAutoscaleGroup();
            for (Track t : selectedTracks) {
                t.setAttributeValue(AttributeManager.GROUP_AUTOSCALE, "" + nextAutoscaleGroup);
                t.setAutoScale(false);
            }

            PreferencesManager.getPreferences().setShowAttributeView(true);
            IGV.getInstance().revalidateTrackPanels();
            IGV.getInstance().repaint(selectedTracks);

        });

        return autoscaleItem;
    }

    private static boolean checkAutoscale(Collection<Track> selectedTracks) {
        boolean autoScale = false;
        for (Track t : selectedTracks) {
            if (t.getAutoScale()) {
                autoScale = true;
                break;
            }
        }
        return autoScale;
    }

    public static JMenuItem getShowDataRangeItem(final Collection<Track> selectedTracks) {

        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Show Data Range");
        if (selectedTracks.size() == 0) {
            item.setEnabled(false);

        } else {
            boolean showDataRange = true;
            for (Track t : selectedTracks) {
                if (!t.isShowDataRange()) {
                    showDataRange = false;
                    break;
                }
            }

            item.setSelected(showDataRange);
            item.addActionListener(evt -> {
                boolean showDataRange1 = item.isSelected();
                for (Track t : selectedTracks) {
                    if (t instanceof DataTrack) {
                        ((DataTrack) t).setShowDataRange(showDataRange1);
                    }
                }
                IGV.getInstance().repaint(selectedTracks);
            });
        }
        return item;
    }


    public static void addDisplayModeItems(final Collection<Track> tracks, JPopupMenu menu) {

        // Find "most representative" state from track collection
        Map<Track.DisplayMode, Integer> counts = new HashMap<Track.DisplayMode, Integer>(Track.DisplayMode.values().length);
        Track.DisplayMode currentMode = null;

        for (Track t : tracks) {
            Track.DisplayMode mode = t.getDisplayMode();
            if (counts.containsKey(mode)) {
                counts.put(mode, counts.get(mode) + 1);
            } else {
                counts.put(mode, 1);
            }
        }

        int maxCount = -1;
        for (Map.Entry<Track.DisplayMode, Integer> count : counts.entrySet()) {
            if (count.getValue() > maxCount) {
                currentMode = count.getKey();
                maxCount = count.getValue();
            }
        }

        ButtonGroup group = new ButtonGroup();
        Map<String, Track.DisplayMode> modes = new LinkedHashMap<String, Track.DisplayMode>(4);
        modes.put("Collapsed", Track.DisplayMode.COLLAPSED);
        modes.put("Expanded", Track.DisplayMode.EXPANDED);
        modes.put("Squished", Track.DisplayMode.SQUISHED);

        if(tracks.stream().allMatch(t -> t.isAlignment())) {
            modes.put("Full", Track.DisplayMode.FULL);
        }

        for (final Map.Entry<String, Track.DisplayMode> entry : modes.entrySet()) {
            JRadioButtonMenuItem mm = new JRadioButtonMenuItem(entry.getKey());
            mm.setSelected(currentMode == entry.getValue());
            mm.addActionListener(evt -> {
                for (Track t : tracks) {
                    t.setDisplayMode(entry.getValue());

                }
                IGV.getInstance().repaint(tracks);
            });
            group.add(mm);
            menu.add(mm);
        }
    }

    public static void addGroupByStrandItem(final Collection<Track> tracks, JPopupMenu menu) {

        // Find "most representative" state from track collection
        boolean allGrouped = tracks.stream().allMatch(track -> {
            return track instanceof FeatureTrack && ((FeatureTrack) track).isGroupByStrand();
        });

        final JRadioButtonMenuItem groupByItem = new JRadioButtonMenuItem("Group by strand");
        groupByItem.setSelected(allGrouped);
        groupByItem.addActionListener(evt -> {
            tracks.stream().forEach(track -> {
                if (track instanceof FeatureTrack) {
                    ((FeatureTrack) track).setGroupByStrand(groupByItem.isSelected());
                }
            });
            IGV.getInstance().repaint(tracks);
        });

        menu.add(groupByItem);
    }

    public static JMenuItem getRemoveMenuItem(final Collection<Track> selectedTracks) {

        boolean multiple = selectedTracks.size() > 1;

        JMenuItem item = new JMenuItem("Remove Track" + (multiple ? "s" : ""));
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                removeTracksAction(selectedTracks);
            }
        });
        return item;
    }

    /**
     * Display a dialog to the user asking to confirm if they want to remove the
     * selected tracks
     *
     * @param selectedTracks
     */
    public static void removeTracksAction(final Collection<Track> selectedTracks) {
        if (selectedTracks.isEmpty()) {
            return;
        }
        IGV.getInstance().deleteTracks(selectedTracks);
        IGV.getInstance().repaint();
    }


    public static void changeRendererClass(final Collection<Track> selectedTracks, Class rendererClass) {
        for (Track track : selectedTracks) {
            track.setRendererClass(rendererClass);
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    public static void renameTrack(final Collection<Track> selectedTracks) {

        if (selectedTracks.isEmpty()) {
            return;
        }
        Track t = selectedTracks.iterator().next();
        String newName = JOptionPane.showInputDialog(IGV.getInstance().getMainFrame(), "Enter new name: ", t.getName());

        if (newName == null || newName.trim() == "") {
            return;
        }

        t.setName(newName);
        IGV.getInstance().repaintNamePanels();
    }

    public static void changeTrackHeight(final Collection<Track> selectedTracks) {
        if (selectedTracks.isEmpty()) {
            return;
        }

        final String parameter = "Track height";
        Integer value = getIntegerInput(parameter, getRepresentativeTrackHeight(selectedTracks));
        if (value == null) {
            return;
        }

        value = Math.max(0, value);
        for (Track track : selectedTracks) {
            track.setHeight(value, true);
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    public static void changeFeatureVisibilityWindow(final Collection<Track> selectedTracks) {

        Collection<Track> featureTracks = new ArrayList(selectedTracks.size());
        for (Track t : selectedTracks) {
            if (t instanceof FeatureTrack || t instanceof InteractionTrack) {
                featureTracks.add(t);
            }
        }

        if (featureTracks.isEmpty()) {
            return;
        }


        int origValue = featureTracks.iterator().next().getVisibilityWindow();
        double origValueKB = (origValue / 1000.0);
        Double value = getDoubleInput("Enter visibility window in kilo-bases.  To load all data enter zero.", origValueKB);
        if (value == null) {
            return;
        }

        for (Track track : featureTracks) {
            track.setVisibilityWindow((int) (value * 1000));
        }

        IGV.getInstance().repaint(featureTracks);
    }

    public static void changeFontSize(final Collection<Track> selectedTracks) {


        if (selectedTracks.isEmpty()) {
            return;
        }

        final String parameter = "Font size";
        int defaultValue = selectedTracks.iterator().next().getFontSize();
        Integer value = getIntegerInput(parameter, defaultValue);
        if (value == null) {
            return;
        }

        for (Track track : selectedTracks) {
            track.setFontSize(value);
        }

        IGV.getInstance().repaintNamePanels();
        IGV.getInstance().repaint(selectedTracks);
    }

    public static Integer getIntegerInput(String parameter, int value) {

        while (true) {

            String strValue = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(), parameter + ": ",
                    String.valueOf(value));

            //strValue will be null if dialog cancelled
            if ((strValue == null) || strValue.trim().equals("")) {
                return null;
            }

            try {
                value = Integer.parseInt(strValue);
                return value;
            } catch (NumberFormatException numberFormatException) {
                JOptionPane.showMessageDialog(IGV.getInstance().getMainFrame(),
                        parameter + " must be an integer number.");
            }
        }
    }

    public static Double getDoubleInput(String parameter, double value) {

        while (true) {

            String strValue = JOptionPane.showInputDialog(
                    IGV.getInstance().getMainFrame(), parameter + ": ",
                    String.valueOf(value));

            //strValue will be null if dialog cancelled
            if ((strValue == null) || strValue.trim().equals("")) {
                return null;
            }

            try {
                value = Double.parseDouble(strValue);
                return value;
            } catch (NumberFormatException numberFormatException) {
                MessageUtils.showMessage(parameter + " must be a number.");
            }
        }
    }

    public static void changeTrackColor(final Collection<Track> selectedTracks) {

        if (selectedTracks.isEmpty()) {
            return;
        }

        Color currentSelection = selectedTracks.iterator().next().getColor();

        Color color = UIUtilities.showColorChooserDialog(
                "Select Track Color",
                currentSelection);

        if (color == null) {
            return;
        }

        for (Track track : selectedTracks) {
            //We preserve the alpha value. This is motivated by MergedTracks
            track.setColor(ColorUtilities.modifyAlpha(color, currentSelection.getAlpha()));
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    public static void changeAltTrackColor(final Collection<Track> selectedTracks) {

        if (selectedTracks.isEmpty()) {
            return;
        }

        Color currentSelection = selectedTracks.iterator().next().getAltColor();

        Color color = UIUtilities.showColorChooserDialog(
                "Select Track Color (Negative Values)",
                currentSelection);

        if (color == null) {
            return;
        }

        for (Track track : selectedTracks) {
            track.setAltColor(ColorUtilities.modifyAlpha(color, currentSelection.getAlpha()));
        }
        IGV.getInstance().repaint(selectedTracks);
    }

    /**
     * @author stevenbusan
     */
    public static void changeBasePairTrackColor(final List<BasePairTrack> tracks,
                                                final Color currentColor,
                                                final String currentLabel) {

        if (tracks.isEmpty()) {
            return;
        }

        Color newColor = UIUtilities.showColorChooserDialog(
                "Select Arc Color (" + currentLabel + ")",
                currentColor);

        if (newColor == null) {
            return;
        }

        for (BasePairTrack t : tracks) {
            t.getRenderOptions().changeColor(currentColor, currentLabel, newColor);
        }
        IGV.getInstance().repaint(tracks);

    }

    public static JMenuItem getCopyDetailsItem(final Feature f, final TrackClickEvent evt) {
        JMenuItem item = new JMenuItem("Copy Details to Clipboard");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {

                ReferenceFrame frame = evt.getFrame();
                int mouseX = evt.getMouseEvent().getX();

                double location = frame.getChromosomePosition(mouseX);
                if (f instanceof IGVFeature) {
                    String details = f.getChr() + ":" + (f.getStart() + 1) + "-" + f.getEnd() +
                            System.getProperty("line.separator") + System.getProperty("line.separator");
                    String valueString = ((IGVFeature) f).getValueString(location, mouseX, null);
                    if (details != null) {
                        details += valueString;
                        details = details.replace("<br>", System.getProperty("line.separator"));
                        details = details.replace("<br/>", System.getProperty("line.separator"));
                        details = details.replace("<b>", "");
                        details = details.replace("</b>", "");
                        details = details.replace("&nbsp;", " ");
                        details = details.replace("<hr>",
                                System.getProperty("line.separator") + "--------------------------" + System.getProperty("line.separator"));
                        StringUtils.copyTextToClipboard(details);
                    }
                }
            }
        });
        return item;
    }

    public static JMenuItem getCopySequenceItem(final Feature f) {

        final Strand strand;
        if (f instanceof IGVFeature) {
            strand = ((IGVFeature) f).getStrand();
        } else {
            strand = Strand.NONE;
        }

        JMenuItem item = new JMenuItem("Copy Sequence");
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                Genome genome = GenomeManager.getInstance().getCurrentGenome();
                IGV.copySequenceToClipboard(genome, f.getChr(), f.getStart(), f.getEnd(), strand);
            }
        });
        return item;
    }

    public static JMenuItem getExtendViewItem(final String featureName, final Feature f, final Range r) {
        JMenuItem item = new JMenuItem("ExtView");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                ExtendViewClient.postExtendView(featureName, f.getStart(), f.getEnd(), r.getChr(), r.getStart(), r.getEnd());
            }
        });
        return item;
    }

    public static JMenuItem getBlatItem(final Feature f) {
        JMenuItem item = new JMenuItem("BLAT Sequence");
        final int start = f.getStart();
        final int end = f.getEnd();

        if ((end - start) > 20 && (end - start) < 8000) {

            item.addActionListener(evt -> {

                final Strand strand;
                if (f instanceof IGVFeature) {
                    strand = ((IGVFeature) f).getStrand();
                } else {
                    strand = Strand.NONE;
                }
                BlatClient.doBlatQueryFromRegion(f.getChr(), start, end, strand);
            });
        } else {
            item.setEnabled(false);
        }
        return item;
    }


    /**
     * Return a representative track height to use as the default.  For now
     * using the median track height.
     *
     * @return
     */
    public static int getRepresentativeTrackHeight(Collection<Track> tracks) {

        double[] heights = new double[tracks.size()];
        int i = 0;
        for (Track track : tracks) {
            heights[i] = track.getHeight();
            i++;
        }
        int medianTrackHeight = (int) Math.round(StatUtils.percentile(heights, 50));
        if (medianTrackHeight > 0) {
            return medianTrackHeight;
        }

        return PreferencesManager.getPreferences().getAsInt(Constants.INITIAL_TRACK_HEIGHT);

    }

    public static JMenuItem getChangeTrackHeightItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Change Track Height...");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent evt) {
                changeTrackHeight(selectedTracks);
            }
        });
        return item;
    }


    public static JMenuItem getChangeFeatureWindow(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Set Feature Visibility Window...");
        item.addActionListener(evt -> changeFeatureVisibilityWindow(selectedTracks));
        return item;
    }

    public static JMenuItem getShowFeatureNames(final Collection<Track> selectedTracks) {
        boolean currentValue = selectedTracks.stream().allMatch(t -> t.isShowFeatureNames());
        String label = currentValue ? "Hide Feature Names" : "Show Feature Names";
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(evt -> selectedTracks.stream().forEach(t -> t.setShowFeatureNames(!currentValue)));
        return item;
    }

    public static JMenuItem getFeatureNameAttribute(final Collection<Track> selectedTracks) {

        JMenuItem item = new JMenuItem("Set Feature Label Field...");
        item.addActionListener(evt -> {
            String currentVal = selectedTracks.iterator().next().getLabelField();
            if (currentVal == null) currentVal = "";
            final String newVal = JOptionPane.showInputDialog(IGV.getInstance().getMainFrame(), "Feature Label Field: ", currentVal);
            if (newVal == null) {
                return; // Dialog canceled
            }
            selectedTracks.stream().forEach(t -> {
                if (t instanceof FeatureTrack) {
                    ((FeatureTrack) t).setLabelField(newVal);
                }
            });
            IGV.getInstance().repaint(selectedTracks);
        });

        return item;
    }

    public static JMenuItem getChangeFontSizeItem(final Collection<Track> selectedTracks) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Change Font Size...");
        item.addActionListener(evt -> changeFontSize(selectedTracks));
        return item;
    }


    // Experimental methods follow

    public static JMenuItem getShowSortFramesItem(final Track track) {

        final JCheckBoxMenuItem item = new JCheckBoxMenuItem("Sort frames");

        item.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        FrameManager.sortFrames(track);
                        IGV.getInstance().resetFrames();
                    }
                };
                LongRunningTask.submit(runnable);
            }

        });
        return item;
    }

}

