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
import org.broad.igv.event.GenomeResetEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.exceptions.DataLoadException;
import org.broad.igv.feature.FeatureDB;
import org.broad.igv.feature.genome.load.ChromAliasParser;
import org.broad.igv.feature.genome.load.GenomeDescriptor;
import org.broad.igv.feature.genome.load.GenomeLoader;
import org.broad.igv.feature.genome.load.JsonGenomeLoader;
import org.broad.igv.jbrowse.CircularViewUtilities;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.PanelName;
import org.broad.igv.ui.commandbar.GenomeListManager;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.ProgressBar;
import org.broad.igv.ui.util.ProgressMonitor;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.ui.util.download.Downloader;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.HttpUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.Utilities;

import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.List;

import static org.broad.igv.prefs.Constants.SHOW_SINGLE_TRACK_PANE_KEY;
import static org.broad.igv.ui.IGV.DATA_PANEL_NAME;
import static org.broad.igv.ui.IGV.FEATURE_PANEL_NAME;

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
        GenomeLoader.localSequenceMap = GenomeLoader.loadSequenceMap();
    }

    /**
     * Returns a File of the provided genomePath. If the genomePath is a URL, it will be downloaded
     * and saved in the genome cache directory.
     *
     * @param genomePath
     * @return
     * @throws MalformedURLException
     * @throws UnsupportedEncodingException
     */
    public static File getGenomeFile(String genomePath) throws MalformedURLException, UnsupportedEncodingException {
        File archiveFile;
        if (HttpUtils.isRemoteURL(genomePath.toLowerCase())) {
            // We need a local copy, as there is no http zip file reader
            URL genomeArchiveURL = HttpUtils.createURL(genomePath);
            final String tmp = URLDecoder.decode(genomeArchiveURL.getFile(), "UTF-8");
            String cachedFilename = Utilities.getFileNameFromURL(tmp);
            if (!DirectoryManager.getGenomeCacheDirectory().exists()) {
                DirectoryManager.getGenomeCacheDirectory().mkdir();
            }
            archiveFile = new File(DirectoryManager.getGenomeCacheDirectory(), cachedFilename);
            Frame parent = IGV.hasInstance() ? IGV.getInstance().getMainFrame() : null;
            Downloader.download(genomeArchiveURL, archiveFile, parent);
        } else {
            archiveFile = new File(genomePath);
        }
        return archiveFile;
    }

    public void setCurrentGenome(Genome genome) {
        if (genome != null) {
            PreferencesManager.getPreferences().setLastGenome(genome.getId());
        }
        this.currentGenome = genome;
        if (genome != null) {
            if (IGV.hasInstance()) {
                IGV.getInstance().getSession().clearHistory();
                FrameManager.getDefaultFrame().setChromosomeName(genome.getHomeChromosome(), true);
                IGVEventBus.getInstance().post(new GenomeChangeEvent(genome));
            }
        }
    }


    public void loadGenomeById(String genomeId) throws IOException {

        final Genome currentGenome = getCurrentGenome();
        if (currentGenome != null && genomeId.equals(currentGenome.getId())) {
            return; // Already loaded
        }

        // If genomeId is a file path load it
        if (org.broad.igv.util.ParsingUtils.fileExists(genomeId)) {
            loadGenome(genomeId, null);

        } else {
            final ProgressMonitor[] monitor = {new ProgressMonitor()};
            final ProgressBar.ProgressDialog[] progressDialog = new ProgressBar.ProgressDialog[1];
            UIUtilities.invokeAndWaitOnEventThread(() -> {
                progressDialog[0] = ProgressBar.showProgressDialog(IGV.getInstance().getMainFrame(), "Loading Genome...", monitor[0], false);
            });

            try {
                GenomeListItem item = genomeListManager.getGenomeListItem(genomeId);
                if (item == null) {
                    MessageUtils.showMessage("Could not locate genome with ID: " + genomeId);
                } else {
                    loadGenome(item.getPath(), monitor[0]);
                }
            } finally {
                UIUtilities.invokeOnEventThread(() -> {
                    progressDialog[0].setVisible(false);
                });
            }
        }
    }

    public Genome loadGenome(String genomePath, ProgressMonitor monitor) throws IOException {

        try {
            log.info("Loading genome: " + genomePath);

            if (monitor != null) {
                UIUtilities.invokeAndWaitOnEventThread(() -> monitor.fireProgress(25));
            }

            // Clear Feature DB
            FeatureDB.clearFeatures();

            Genome newGenome = GenomeLoader.getLoader(genomePath).loadGenome();

            // Load user-defined chr aliases, if any.  This is done last so they have priority
            try {
                String aliasPath = (new File(DirectoryManager.getGenomeCacheDirectory(), newGenome.getId() + "_alias.tab")).getAbsolutePath();
                if (!(new File(aliasPath)).exists()) {
                    aliasPath = (new File(DirectoryManager.getGenomeCacheDirectory(), newGenome.getId() + "_alias.tab.txt")).getAbsolutePath();
                }
                if ((new File(aliasPath)).exists()) {
                    newGenome.addChrAliases(ChromAliasParser.loadChrAliases(aliasPath));
                }
            } catch (Exception e) {
                log.error("Failed to load user defined alias", e);
            }

            if (monitor != null) {
                monitor.fireProgress(25);
            }

            if (IGV.hasInstance()) {
                IGV.getInstance().resetSession(null);
            }

            GenomeListItem genomeListItem = new GenomeListItem(newGenome.getDisplayName(), genomePath, newGenome.getId());
            final Set<String> serverGenomeIDs = genomeListManager.getServerGenomeIDs();

            boolean userDefined = !serverGenomeIDs.contains(newGenome.getId());
            genomeListManager.addGenomeItem(genomeListItem, userDefined);

            setCurrentGenome(newGenome);

            // hasInstance() test needed for unit tests
            if (IGV.hasInstance()) {
                IGV.getInstance().goToLocus(newGenome.getDefaultPos());
                loadGenomeAnnotations(newGenome);
            }

            if (PreferencesManager.getPreferences().getAsBoolean(Constants.CIRC_VIEW_ENABLED) && CircularViewUtilities.ping()) {
                CircularViewUtilities.changeGenome(newGenome);
            }

            // log.warn("Genome loaded.  id= " + newGenome.getId());
            return currentGenome;

        } catch (SocketException e) {
            throw new RuntimeException("Server connection error", e);
        }
    }

    /**
     * Load and initialize the track objects from the genome's track resource locators.  Does not add the tracks
     * to the IGV instance.
     *
     * @param genome
     */
    public void loadGenomeAnnotations(Genome genome) {
        List<ResourceLocator> resources = genome.getAnnotationResources();
        if (resources != null) {
            Map<ResourceLocator, List<Track>> annotationTracks = new LinkedHashMap<>();
            for (ResourceLocator locator : resources) {
                try {
                    List<Track> tracks = IGV.getInstance().load(locator);
                    annotationTracks.put(locator, tracks);
                } catch (DataLoadException e) {
                    log.error("Error loading genome annotations", e);
                }
            }//
            genome.setAnnotationTracks(annotationTracks);
        }
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

        FeatureTrack geneFeatureTrack = genome.getGeneTrack();   // Can be null
        if (geneFeatureTrack != null) {
            PanelName panelName = PreferencesManager.getPreferences().getAsBoolean(SHOW_SINGLE_TRACK_PANE_KEY) ?
                    PanelName.DATA_PANEL : PanelName.FEATURE_PANEL;
            geneFeatureTrack.setAttributeValue(Globals.TRACK_NAME_ATTRIBUTE, geneFeatureTrack.getName());
            geneFeatureTrack.setAttributeValue(Globals.TRACK_DATA_FILE_ATTRIBUTE, "");
            geneFeatureTrack.setAttributeValue(Globals.TRACK_DATA_TYPE_ATTRIBUTE, geneFeatureTrack.getTrackType().toString());
            IGV.getInstance().addTracks(Arrays.asList(geneFeatureTrack), panelName);
        }

        Map<ResourceLocator, List<Track>> annotationTracks = currentGenome.getAnnotationTracks();
        if (annotationTracks != null) {
            for (Map.Entry<ResourceLocator, List<Track>> entry : annotationTracks.entrySet()) {
                IGV.getInstance().addTracks(entry.getValue(), entry.getKey());
                for (Track track : entry.getValue()) {
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

    /**
     * Create a genome archive (.genome) file.
     *
     * @param genomeFile
     * @param cytobandFileName  A File path to a file that contains cytoband data.
     * @param refFlatFileName   A File path to a gene file.
     * @param fastaFileName     A File path to a FASTA file, a .gz file containing a
     *                          single FASTA file, or a directory containing ONLY FASTA files.
     *                          (relative to the .genome file to be created) where the sequence data for
     *                          the new genome will be written.
     * @param genomeDisplayName The unique user-readable name of the new genome.
     * @param genomeId          The id to be assigned to the genome.
     * @param monitor           A ProgressMonitor used to track progress - null,
     *                          if no progress updating is required.
     * @return GenomeListItem
     * @throws FileNotFoundException
     */
    public GenomeListItem defineGenome(File genomeFile,
                                       String cytobandFileName,
                                       String refFlatFileName,
                                       String fastaFileName,
                                       String chrAliasFileName,
                                       String genomeDisplayName,
                                       String genomeId,
                                       javax.swing.ProgressMonitor monitor)
            throws IOException {

        File refFlatFile = null;
        File cytobandFile = null;
        File chrAliasFile = null;

        if (genomeFile != null) {
            PreferencesManager.getPreferences().setLastGenomeImportDirectory(genomeFile.getParentFile());
        }

        if ((cytobandFileName != null) && (cytobandFileName.trim().length() != 0)) {
            cytobandFile = new File(cytobandFileName);
        }

        if ((refFlatFileName != null) && (refFlatFileName.trim().length() != 0)) {
            refFlatFile = new File(refFlatFileName);
        }

        if ((chrAliasFileName != null) && (chrAliasFileName.trim().length() != 0)) {
            chrAliasFile = new File(chrAliasFileName);
        }

        if (monitor != null) monitor.setProgress(25);

        (new GenomeImporter()).createGenomeArchive(genomeFile, genomeId,
                genomeDisplayName, fastaFileName, refFlatFile, cytobandFile, chrAliasFile);

        if (monitor != null) monitor.setProgress(75);

        GenomeListItem newItem = new GenomeListItem(genomeDisplayName, genomeFile.getAbsolutePath(), genomeId);
        genomeListManager.addGenomeItem(newItem, true);

        if (monitor != null) monitor.setProgress(100);

        return newItem;

    }

    /**
     * Specific to Broad Amazon servers -- use S3 downwload rather than cloudfront
     *
     * @param path
     * @return
     */
    private String convertToS3(String path) {
        if (path.startsWith("http://igvdata") || path.startsWith("https://igvdata")) {
            return path.replaceFirst("igvdata", "igv");
        } else {
            return path;
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

    /**
     * Given a directory, looks for all .genome files,
     * and outputs a list of these genomes suitable for parsing by IGV.
     * Intended to be run on server periodically.
     *
     * @param inDir    Directory in which all genome files live
     * @param rootPath The path to be prepended to file names (e.g. http://igvdata.broadinstitute.org)
     * @param outPath  Path to output file, where we will write the results
     */
    public void generateGenomeList(File inDir, String rootPath, String outPath) {
        File[] genomeFiles = inDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name == null) return false;
                return name.toLowerCase().endsWith(".genome");
            }
        });

        PrintWriter writer;
        try {
            writer = new PrintWriter(outPath);
        } catch (FileNotFoundException e) {
            log.error("Error opening " + outPath);
            e.printStackTrace();
            return;
        }


        GenomeDescriptor descriptor;
        for (File f : genomeFiles) {
            String curLine = "";
            try {
                descriptor = GenomeDescriptor.parseGenomeArchiveFile(f);
                curLine += descriptor.getName();
                curLine += "\t" + rootPath + "/" + f.getName();
                curLine += "\t" + descriptor.getId();
            } catch (IOException e) {
                log.error("Error parsing genome file. Skipping " + f.getAbsolutePath());
                log.error(e);
                continue;
            }
            writer.println(curLine);
        }

        writer.close();

    }


    public boolean downloadGenome(GenomeListItem item, boolean downloadSequence) {

        boolean success;
        try {
            File genomeFile = getGenomeFile(item.getPath());                  // Has side affect of downloading .genome file
            if (downloadSequence) {
                String fastaPath = null;
                if (item.getPath().endsWith(".genome")) {
                    GenomeDescriptor genomeDescriptor = GenomeDescriptor.parseGenomeArchiveFile(genomeFile);
                    fastaPath = genomeDescriptor.getSequencePath();
                } else if (item.getPath().endsWith(".json")) {
                    JsonGenomeLoader.GenomeDescriptor desc = (new JsonGenomeLoader(item.getPath())).loadDescriptor();
                    fastaPath = desc.getFastaURL();
                }
                if (fastaPath != null && FileUtils.isRemote(fastaPath)) {
                    File localFile = downloadFasta(fastaPath);
                    if (localFile != null) {
                        addLocalFasta(item.getId(), localFile);
                    }
                }
            }

            success = true;

        } catch (Exception e) {
            success = false;
            MessageUtils.showErrorMessage("Error downloading genome", e);
            log.error("Error downloading genome " + item.getDisplayableName());
        }


        if (success) {
            genomeListManager.addGenomeItem(item, false);
            IGVEventBus.getInstance().post(new GenomeResetEvent());
        }

        return success;

    }


    /**
     * Download a fasta file and associated index files.
     *
     * @throws IOException
     */
    File downloadFasta(String fastaPath) throws IOException {

        File defaultDir = DirectoryManager.getFastaCacheDirectory();
        File targetDir = defaultDir;
        //File targetDir = FileDialogUtils.chooseDirectory("Select directory for sequence", defaultDir);
        if (targetDir == null) {
            targetDir = defaultDir;
        }

        String filename = Utilities.getFileNameFromURL(fastaPath);

        File localFile = new File(targetDir, filename);
        boolean downloaded = Downloader.download(HttpUtils.createURL(fastaPath), localFile, IGV.getInstance().getMainFrame());

        if (downloaded) {
            URL indexUrl = HttpUtils.createURL(fastaPath + ".fai");
            File localIndexFile = new File(targetDir, filename + ".fai");
            downloaded = Downloader.download(indexUrl, localIndexFile, IGV.getInstance().getMainFrame());
        }

        if (downloaded) {

            if (fastaPath.endsWith(".gz")) {
                URL gziUrl = HttpUtils.createURL(fastaPath + ".gzi");
                File localGziPath = new File(targetDir, filename + ".gzi");
                downloaded = Downloader.download(gziUrl, localGziPath, IGV.getInstance().getMainFrame());
            }
        }

        return downloaded ? localFile : null;
    }


    public static File getLocalFasta(String id) {
        return GenomeLoader.localSequenceMap.get(id);
    }

    public static void removeLocalFasta(String id) {
        GenomeLoader.localSequenceMap.remove(id);
        updateSequenceMapFile();
    }

    private static void addLocalFasta(String id, File localFile) {
        GenomeLoader.localSequenceMap.put(id, localFile);
        updateSequenceMapFile();
    }


    private static void updateSequenceMapFile() {

        PrintWriter pw = null;

        try {
            File sequenceFile = new File(DirectoryManager.getGenomeCacheDirectory(), GenomeDescriptor.SEQUENCE_MAP_FILE);
            pw = new PrintWriter(new BufferedWriter(new FileWriter(sequenceFile)));

            for (Map.Entry<String, File> entry : GenomeLoader.localSequenceMap.entrySet()) {
                pw.println(entry.getKey() + "\t" + entry.getValue());
            }
        } catch (IOException e) {
            log.error("Error writing sequence map", e);
        } finally {
            if (pw != null) pw.close();
        }
    }

}
