package org.broad.igv.feature.genome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broad.igv.DirectoryManager;
import org.broad.igv.feature.genome.load.GenomeConfig;
import org.broad.igv.feature.genome.load.TrackConfig;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.commandbar.GenomeListManager;
import org.broad.igv.ui.util.download.Downloader;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.HttpUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Class of static functions for managing genome downloads
 */
public class GenomeDownloadUtils {

    private static Logger log = LogManager.getLogger(GenomeDownloadUtils.class);

    public static boolean isAnnotationsDownloadable(GenomeListItem item) {
        return item.getPath().endsWith(".json");
    }

    public static boolean isSequenceDownloadable(GenomeListItem item) {
        if (item.getPath().endsWith(".json")) {
            try {
                String jsonString = HttpUtils.getInstance().getContentsAsJSON(new URL(item.getPath()));
                return jsonString.contains("twoBitURL");
            } catch (IOException e) {
                log.error("Error fetching genome json " + item.getPath());
            }
        }
        return false;
    }

    public static File downloadGenome(GenomeConfig c, boolean downloadSequence, boolean downloadAnnotations) throws IOException {

        GenomeConfig config = c.copy();

        // Create a directory for the data files (sequence and annotations)
        final File genomeDirectory = DirectoryManager.getGenomeCacheDirectory();
        File dataDirectory = new File(genomeDirectory, config.getId());
        if (dataDirectory.exists()) {
            if (!dataDirectory.isDirectory()) {
                throw new RuntimeException("Error downloading genome. " + dataDirectory.getAbsolutePath() + " exists and is not a directory.");
            }
        } else {
            if (!dataDirectory.mkdir()) {
                throw new RuntimeException("Error downloading genome.  Could not create directory: " + dataDirectory.getAbsolutePath());
            }
        }

        String relativeDataDirectory = config.getId() + "/";

        if (config.getTwoBitURL() != null) {

            URL url = new URL(config.getTwoBitURL());
            File localFile;
            if (downloadSequence) {
                localFile = download(url, dataDirectory);
            } else {
                localFile = constructLocalFile(url, dataDirectory);  // It might be there from previous downloads
            }
            if (localFile.exists()) {
                config.setTwoBitURL(relativeDataDirectory + localFile.getName());
                // Null out urls not needed for .2bit sequences.
                config.setFastaURL(null);
                config.setIndexURL(null);
                config.setGziIndexURL(null);
                config.setTwoBitBptURL(null);  // Not needed for local .2bit files
            }
        } else if (config.getFastaURL() != null) {

            String[] fastaFields = {"fastaURL", "indexURL", "gziIndexURL", "compressedIndexURL"};
            downloadAndUpdateConfig(downloadSequence, fastaFields, config, dataDirectory, relativeDataDirectory);

        } else {
            throw new RuntimeException("Sequence for genome " + config.getName() + " is not downloadable.");
        }

        String[] annotationFields = {"chromAliasBbURL", "cytobandURL", "cytobandBbURL", "aliasURL", "chromSizesURL"};
        downloadAndUpdateConfig(downloadAnnotations, annotationFields, config, dataDirectory, relativeDataDirectory);

        List<TrackConfig> trackConfigs = config.getTrackConfigs();
        if (trackConfigs != null) {
            String[] trackFields = {"url", "indexURL", "trixURL"};
            for (TrackConfig trackConfig : trackConfigs) {
                downloadAndUpdateConfig(downloadAnnotations, trackFields, trackConfig, dataDirectory, relativeDataDirectory);
            }

        }

        File localGenomeFile = new File(genomeDirectory, config.getId() + ".json");
        saveLocalGenome(config, localGenomeFile);
        return localGenomeFile;

    }

    private static void downloadAndUpdateConfig(boolean downloadData, String[] fields, Object config, File dataDirectory, String relativeDataDirectory) {
        URL url;
        File localFile;
        for (String f : fields) {
            try {
                Field field = config.getClass().getDeclaredField(f);
                field.setAccessible(true);
                Object urlField = field.get(config);
                if (urlField != null) {
                    url = new URL(urlField.toString());
                    if (downloadData) {
                        localFile = download(url, dataDirectory);
                    } else {
                        localFile = constructLocalFile(url, dataDirectory);   // It might be there from previous downloads
                    }
                    if (localFile.exists()) {
                        field.set(config, relativeDataDirectory + localFile.getName());
                    }
                }
            } catch (Exception e) {
                log.error(e);
            }
        }
    }

    private static void saveLocalGenome(GenomeConfig genomeConfig, File localFile) throws IOException {
        log.info("Saving " + localFile.getAbsolutePath());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(localFile))) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(genomeConfig, writer);
        }
    }


    public static File constructLocalFile(URL url, File directory) {
        String path = url.getPath();
        int idx = path.lastIndexOf('/');
        String filename = idx < 0 ? path : path.substring(idx + 1);
        return new File(directory, filename);
    }

    private static File download(URL url, File directory) throws MalformedURLException {

        File localFile = constructLocalFile(url, directory);

        boolean success = Downloader.download(url, localFile, IGV.getInstance().getMainFrame());
        if (!success) {
            throw new RuntimeException("Download canceled");
        }
        return localFile;
    }

}
