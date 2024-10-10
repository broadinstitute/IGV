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

/*
 * GenomeManager.java
 *
 * Created on November 9, 2007, 9:12 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.broad.igv.feature.genome;


import org.broad.igv.DirectoryManager;
import org.broad.igv.Globals;
import org.broad.igv.event.GenomeChangeEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.exceptions.DataLoadException;
import org.broad.igv.feature.FeatureDB;
import org.broad.igv.feature.genome.load.*;
import org.broad.igv.jbrowse.CircularViewUtilities;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.PanelName;
import org.broad.igv.ui.WaitCursorManager;
import org.broad.igv.ui.commandbar.GenomeListManager;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.util.*;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.ResourceLocator;

import java.io.*;
import java.net.SocketException;
import java.util.*;
import java.util.List;

import static org.broad.igv.prefs.Constants.SHOW_SINGLE_TRACK_PANE_KEY;

/**
 * @author jrobinso
 */
public class GenomeManager {

    private static Logger log = LogManager.getLogger(GenomeManager.class);

    private static GenomeManager theInstance;

    private static GenomeListManager genomeListManager;

    private Genome currentGenome;


    /**
     * Map from genomeID -> GenomeListItem
     * ID comparison will be case insensitive
     */

    public synchronized static GenomeManager getInstance() {
        if (theInstance == null) {
            theInstance = new GenomeManager();
        }
        return theInstance;
    }

    private GenomeManager() {
        genomeListManager = GenomeListManager.getInstance();
        GenomeLoader.loadSequenceMap();
    }

    /**
     * Delete the specified .genome files.
     *
     * @param removedValuesList
     */
    public void deleteDownloadedGenomes(List<GenomeListItem> removedValuesList) throws IOException {

        for (GenomeListItem item : removedValuesList) {
            String loc = item.getPath();
            File genFile = new File(loc);
            if (DirectoryManager.isChildOf(DirectoryManager.getGenomeCacheDirectory(), genFile)) {
                genFile.delete();
            }

            File localFasta = DotGenomeUtils.getLocalFasta(item.getId());
            if (localFasta != null) {
                // If fasta file is in the "igv/genomes" directory delete it
                DotGenomeUtils.removeLocalFasta(item.getId());
                if (DirectoryManager.isChildOf(DirectoryManager.getGenomeCacheDirectory(), localFasta)) {
                    if (MessageUtils.confirm("Delete fasta file: " + localFasta.getAbsolutePath() + "?")) {
                        localFasta.delete();
                        File indexFile = new File(localFasta.getAbsolutePath() + ".fai");
                        if (indexFile.exists()) {
                            indexFile.delete();
                        }
                    }
                }
            }
        }
        GenomeListManager.getInstance().removeAllItems(removedValuesList);
    }

    // Setter provided for unit tests
    public void setCurrentGenomeForTest(Genome genome) {
        this.currentGenome = genome;
    }

    /**
     * Load a genome by ID, which might be a file path or URL
     *
     * @param genomeId - ID for an IGV hosted genome, or file path or url
     * @return boolean flag indicating success
     * @throws IOException
     */
    public boolean loadGenomeById(String genomeId) throws IOException {

        final Genome currentGenome = getCurrentGenome();
        if (currentGenome != null && genomeId.equals(currentGenome.getId())) {
            return false;
        }

        String genomePath;
        if (org.broad.igv.util.ParsingUtils.fileExists(genomeId)) {
            genomePath = genomeId;
        } else {
            GenomeListItem item = genomeListManager.getGenomeListItem(genomeId);
            if (item == null) {
                MessageUtils.showMessage("Could not locate genome with ID: " + genomeId);
                return false;
            } else {
                genomePath = item.getPath();
            }
        }
        return loadGenome(genomePath) != null; // monitor[0]);
    }


    /**
     * The main load method -- loads a genome from a file or url path.  Note this is a long running operation and
     * should not be done on the Swing event thread as it will block the UI.
     * <p>
     * NOTE: The member 'currentGenome' is set here as a side effect.
     *
     * @param genomePath
     * @return
     * @throws IOException
     */
    public Genome loadGenome(String genomePath) throws IOException {

        WaitCursorManager.CursorToken cursorToken = null;
        try {
            log.info("Loading genome: " + genomePath);
            if (IGV.hasInstance()) {
                IGV.getInstance().setStatusBarMessage("<html><font color=blue>Loading genome</font></html>");
                cursorToken = WaitCursorManager.showWaitCursor();
            }

            // Clear Feature DB
            FeatureDB.clearFeatures();

            Genome newGenome = GenomeLoader.getLoader(genomePath).loadGenome();

            // Load user-defined chr aliases, if any.  This is done last so they have priority
            final File genomeCacheDirectory = DirectoryManager.getGenomeCacheDirectory();
            try {
                String aliasPath = (new File(genomeCacheDirectory, newGenome.getId() + "_alias.tab")).getAbsolutePath();
                if (!(new File(aliasPath)).exists()) {
                    aliasPath = (new File(genomeCacheDirectory, newGenome.getId() + "_alias.tab.txt")).getAbsolutePath();
                }
                if ((new File(aliasPath)).exists()) {
                    newGenome.addChrAliases(ChromAliasParser.loadChrAliases(aliasPath));
                }
            } catch (Exception e) {
                log.error("Failed to load user defined alias", e);
            }


            if (IGV.hasInstance()) {
                IGV.getInstance().resetSession(null);
            }

            // Add an entry to the pulldown
            GenomeListItem genomeListItem = new GenomeListItem(newGenome.getDisplayName(), genomePath, newGenome.getId());
            GenomeListManager.getInstance().addGenomeItem(genomeListItem);

            setCurrentGenome(newGenome);

            return currentGenome;

        } catch (SocketException e) {
            throw new RuntimeException("Server connection error", e);
        } finally {
            if (IGV.hasInstance()) {
                IGV.getInstance().setStatusBarMessage("");
                WaitCursorManager.removeWaitCursor(cursorToken);
            }
        }
    }

    public void setCurrentGenome(Genome newGenome) {

        this.currentGenome = newGenome;

        // hasInstance() check to filters unit test
        if (IGV.hasInstance()) {
            IGV.getInstance().goToLocus(newGenome.getHomeChromosome()); //  newGenome.getDefaultPos());
            FrameManager.getDefaultFrame().setChromosomeName(newGenome.getHomeChromosome(), true);
            loadGenomeAnnotations(newGenome);
            IGV.getInstance().resetFrames();
            IGV.getInstance().getSession().clearHistory();

            if (newGenome != Genome.nullGenome()) {
                // This should only occur on startup failure
                PreferencesManager.getPreferences().setLastGenome(newGenome.getId());
            }

            if (PreferencesManager.getPreferences().getAsBoolean(Constants.CIRC_VIEW_ENABLED) && CircularViewUtilities.ping()) {
                CircularViewUtilities.changeGenome(newGenome);
            }

            IGVEventBus.getInstance().post(new GenomeChangeEvent(newGenome));
        }
    }

    /**
     * Load and initialize the track objects from the genome's track resource locators.  Does not add the tracks
     * to the IGV instance.
     *
     * @param genome
     */
    public void loadGenomeAnnotations(Genome genome) {
        restoreGenomeTracks(genome);
        IGV.getInstance().repaint();
    }

    /**
     * Add a genomes tracks to the IGV instance.
     *
     * @param genome
     */
    public void restoreGenomeTracks(Genome genome) {

        IGV.getInstance().setSequenceTrack();

        // Fetch the gene track, defined by .genome files.  In this format the genome data is encoded in the .genome file
        FeatureTrack geneFeatureTrack = genome.getGeneTrack();   // Can be null
        if (geneFeatureTrack != null) {
            PanelName panelName = PreferencesManager.getPreferences().getAsBoolean(SHOW_SINGLE_TRACK_PANE_KEY) ?
                    PanelName.DATA_PANEL : PanelName.FEATURE_PANEL;
            geneFeatureTrack.setAttributeValue(Globals.TRACK_NAME_ATTRIBUTE, geneFeatureTrack.getName());
            geneFeatureTrack.setAttributeValue(Globals.TRACK_DATA_FILE_ATTRIBUTE, "");
            geneFeatureTrack.setAttributeValue(Globals.TRACK_DATA_TYPE_ATTRIBUTE, geneFeatureTrack.getTrackType().toString());
            IGV.getInstance().addTracks(Arrays.asList(geneFeatureTrack), panelName);
        }

        List<ResourceLocator> resources = genome.getAnnotationResources();
        List<Track> annotationTracks = new ArrayList<>();
        if (resources != null) {
            for (ResourceLocator locator : resources) {
                try {
                    List<Track> tracks = IGV.getInstance().load(locator);
                    annotationTracks.addAll(tracks);
                } catch (DataLoadException e) {
                    log.error("Error loading genome annotations", e);
                }
            }
        }

        if (annotationTracks.size() > 0) {
            IGV.getInstance().addTracks(annotationTracks);
            for (Track track : annotationTracks) {
                ResourceLocator locator = track.getResourceLocator();
                String fn = "";
                if (locator != null) {
                    fn = locator.getPath();
                    int lastSlashIdx = fn.lastIndexOf("/");
                    if (lastSlashIdx < 0) {
                        lastSlashIdx = fn.lastIndexOf("\\");
                    }
                    if (lastSlashIdx > 0) {
                        fn = fn.substring(lastSlashIdx + 1);
                    }
                }
                track.setAttributeValue(Globals.TRACK_NAME_ATTRIBUTE, track.getName());
                track.setAttributeValue(Globals.TRACK_DATA_FILE_ATTRIBUTE, fn);
                track.setAttributeValue(Globals.TRACK_DATA_TYPE_ATTRIBUTE, track.getTrackType().toString());
            }
        }

        IGV.getInstance().revalidateTrackPanels();
    }


    /**
     * Delete .genome files from the cache directory
     */
    public void clearGenomeCache() {
        File[] files = DirectoryManager.getGenomeCacheDirectory().listFiles();
        for (File file : files) {
            if (file.getName().toLowerCase().endsWith(Globals.GENOME_FILE_EXTENSION)) {
                file.delete();
            }
        }
    }

    public String getGenomeId() {
        return currentGenome == null ? null : currentGenome.getId();
    }

    /**
     * IGV always has exactly 1 genome loaded at a time.
     * This returns the currently loaded genome
     *
     * @return
     * @api
     */
    public Genome getCurrentGenome() {
        return currentGenome;
    }


    public GenomeListItem downloadGenome(GenomeListItem item, boolean downloadSequence, boolean downloadAnnotations) {

        try {

            if (item.getPath().endsWith(".genome")) {
                File genomeFile = DotGenomeUtils.getDotGenomeFile(item.getPath());  // Will be downloaded if remote -- neccessary to unzip
                item.setPath(genomeFile.getAbsolutePath());
            } else if (downloadSequence || downloadAnnotations) {
                JsonGenomeLoader loader = new JsonGenomeLoader(item.getPath());
                GenomeConfig config = loader.loadGenomeConfig();
                File downloadedGenome = GenomeDownloadUtils.downloadGenome(config, downloadSequence, downloadAnnotations);
                item.setPath(downloadedGenome.getAbsolutePath());
            }

        } catch (Exception e) {
            MessageUtils.showMessage("Error downloading genome: " + e.getMessage());
            log.error("Error downloading genome " + item.getDisplayableName());
        }

        return item;
    }


}
