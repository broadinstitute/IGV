package org.broad.igv.bedpe;

import org.broad.igv.logging.*;
import org.broad.igv.Globals;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.TrackProperties;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Created by jrobinso on 6/29/18.
 */
public class BedPEParser {

    private static Logger log = LogManager.getLogger(BedPEParser.class);

    enum DatasetType {TENX, CLUSTER, UNKNOWN}

    public static Dataset parse(ResourceLocator locator, Genome genome) throws IOException {

        int colorColumn = -1;
        int thicknessColumn = -1;
        DatasetType type = DatasetType.UNKNOWN;
        boolean parsedHeader = true;

        // Default column headers from BedPE spec.  Can be overriden
        String[] columns = {"chrom1", "start1", "stop1", "chrom2", "start2", "stop2", "name", "score", "strand1", "strand2"};
        boolean col7isNumeric = true;   // Until proven otherwise

        Map<String, Color> colorCache = new HashMap<>();
        List<BedPE> features = new ArrayList<>();
        BufferedReader br = null;

        Map<String, Integer> featureCounts = new HashMap<>();

        try {
            br = ParsingUtils.openBufferedReader(locator.getPath());
            String nextLine;
            boolean firstLine = true;
            int skippedLineCount = 0;
            while ((nextLine = br.readLine()) != null) {

                if (nextLine.startsWith("#columns")) {
                    // An IGV hack, not sure anyone is using this
                    try {
                        String[] t1 = ParsingUtils.WHITESPACE_PATTERN.split(nextLine);
                        if (t1.length == 2) {
                            String[] t2 = ParsingUtils.SEMI_COLON_PATTERN.split(t1[1]);
                            for (String keyValue : t2) {
                                String[] t = keyValue.split("=");
                                if (t[0].equals("color")) {
                                    colorColumn = Integer.parseInt(t[1]) - 1;
                                } else if (t[0].equals("thickness")) {
                                    thicknessColumn = Integer.parseInt(t[1]) - 1;
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        log.error("Error parsing #column line.", e);
                    }
                } else if (nextLine.trim().equals("#chrom1\tstart1\tstop1\tchrom2\tstart2\tstop2\tname\tqual\tstrand1\tstrand2\tfilters\tinfo")) {
                    type = DatasetType.TENX;
                }

                if (nextLine.startsWith("#") || nextLine.startsWith("chr1\tx1\tx2")) {

                    String[] tokens = Globals.tabPattern.split(nextLine);
                    if (tokens.length >= 6) {
                        columns = tokens;
                        for (int i = 6; i < columns.length; i++) {
                            if (columns[i].equalsIgnoreCase("color")) {
                                colorColumn = i;
                            } else if (columns[i].toLowerCase().equalsIgnoreCase("thickness")) {
                                thicknessColumn = i;
                            }
                        }
                    }
                } else if (nextLine.startsWith("track") || nextLine.startsWith("##track")) {
                    TrackProperties trackProperties = new TrackProperties();
                    ParsingUtils.parseTrackLine(nextLine, trackProperties);
                } else if (firstLine && nextLine.startsWith("chromosome1\tx1\tx2") || nextLine.startsWith("chr1\tx1\tx2")) {
                    columns = Globals.tabPattern.split(nextLine);
                    for (int i = 6; i < columns.length; i++) {
                        if (columns[i].equalsIgnoreCase("color")) {
                            colorColumn = i;
                        }
                    }
                } else {
                    String[] tokens = Globals.tabPattern.split(nextLine);

                    if (tokens.length < 6) {
                        if (skippedLineCount < 5) {
                            skippedLineCount++;
                            if(skippedLineCount == 5) {
                                log.warn("Skipping line: " + nextLine + (skippedLineCount < 5 ? "" : " Further skipped lines will not be logged"));
                            }
                        }
                        continue;
                    }

                    String chr1 = genome == null ? tokens[0] : genome.getCanonicalChrName(tokens[0]);
                    String chr2 = genome == null ? tokens[3] : genome.getCanonicalChrName(tokens[3]);
                    int start1 = Integer.parseInt(tokens[1]);
                    int end1 = Integer.parseInt(tokens[2]);
                    int start2 = Integer.parseInt(tokens[4]);
                    int end2 = Integer.parseInt(tokens[5]);

                    BedPEFeature feature = new BedPEFeature(chr1, start1, end1, chr2, start2, end2);

                    if(chr1.equals(chr2)) {
                        Integer counts = featureCounts.containsKey(chr1) ? featureCounts.get(chr1) : 0;
                        featureCounts.put(chr1, counts + 1);
                    }

                    if (tokens.length > 6) {
                        feature.name = tokens[6];
                        col7isNumeric = col7isNumeric && isNumeric(tokens[6]);

                    } else {
                        col7isNumeric = false;
                    }

                    if (tokens.length > 7) {
                        feature.scoreString = tokens[7];
                        try {
                            feature.score = Float.parseFloat(tokens[7]);
                        } catch (NumberFormatException e) {
                            feature.score = 0;
                        }
                    }

                    if (tokens.length > 8) {
                        Map<String, String> attributes = new LinkedHashMap<>();

                        for (int i = 8; i < tokens.length; i++) {

                            String t = tokens[i];
                            String c = columns != null && columns.length > i ? columns[i] : String.valueOf(i);

                            if (c.equals("info") && t.contains("=")) {
                                String[] kvPairs = Globals.semicolonPattern.split(tokens[11]);
                                for (String kvPair : kvPairs) {
                                    String[] kv = Globals.equalPattern.split(kvPair);
                                    if (kv.length > 1) {
                                        attributes.put(kv[0], kv[1]);
                                    }
                                }
                            } else {
                                attributes.put(c, t);
                            }
                        }
                        feature.attributes = attributes;
                        feature.type = attributes.get("TYPE");
                    }

                    if (colorColumn > 0 && tokens.length > colorColumn) {
                        String colorString = tokens[colorColumn];
                        Color c = colorCache.get(colorString);
                        if (c == null) {
                            c = ColorUtilities.stringToColor(colorString);
                            colorCache.put(colorString, c);
                        }
                        feature.setColor(c);
                    }

                    if (thicknessColumn > 0 && tokens.length > thicknessColumn) {
                        feature.thickness = Integer.parseInt(tokens[thicknessColumn]);
                    }

                    // Skipping remaining fields for now


                    if (!feature.getChr1().equals(feature.getChr2())) {
                        // Add complement feature
                        features.add(feature.getComplement());
                    }

                    features.add(feature);
                }
                firstLine = false;
            }


            // A hack to detect "interaction" bedpe files, which are not spec compliant.  Interaction score is column 7
            if (col7isNumeric) {
                for (BedPE bedpe : features) {
                    if(bedpe instanceof BedPEFeature) {
                        BedPEFeature f = (BedPEFeature) bedpe;
                        f.score = Float.parseFloat(f.name);
                        f.scoreString = f.name;
                        f.name = null;
                    }
                }
                if (type == DatasetType.UNKNOWN) {
                    type = DatasetType.CLUSTER;   // A guess
                }
            }




            return new Dataset(type, features, featureCounts);
        } finally {
            br.close();
        }


    }


    public static boolean isNumeric(String strNum) {
        return strNum.matches("-?\\d+(\\.\\d+)?");
    }

    public static class Dataset {

        public DatasetType type;
        public List<BedPE> features;
        public Map<String, Integer> featureCounts;

        public Dataset(DatasetType type, List<BedPE> features, Map<String, Integer> featureCounts) {
            this.type = type;
            this.features = features;
            this.featureCounts = featureCounts;
        }
    }

}
