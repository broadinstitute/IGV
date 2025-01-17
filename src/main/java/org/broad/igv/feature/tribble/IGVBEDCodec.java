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

package org.broad.igv.feature.tribble;

import org.broad.igv.logging.*;
import org.broad.igv.Globals;
import org.broad.igv.feature.*;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.StringUtils;
import htsjdk.tribble.Feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Dec 20, 2009
 * Time: 10:15:49 PM
 */
public class IGVBEDCodec extends UCSCCodec<BasicFeature> {

    private static final Logger log = LogManager.getLogger(IGVBEDCodec.class);

    static final Pattern BR_PATTERN = Pattern.compile("<br>");
    static final Pattern EQ_PATTERN = Pattern.compile("=");


    private Genome genome;
    private int maxColumnCount = Integer.MAX_VALUE;

    public IGVBEDCodec() {
        this(null);
    }

    public IGVBEDCodec(Genome genome) {
        this(genome, FeatureType.BED);
    }

    public IGVBEDCodec(Genome genome, FeatureType featureType) {
        super(BasicFeature.class, featureType);
        this.genome = genome;
    }

    @Override
    public BasicFeature decode(String nextLine) {
        BasicFeature feature = super.decode(nextLine);
        feature.setRepresentation(nextLine);
        return feature;
    }

    //@Override
    public BasicFeature decode(String[] tokens) {

        // The first 3 columns are non optional for BED.  We will relax this
        // and only require 2.
        int tokenCount = Math.min(tokens.length, maxColumnCount);

        if (tokenCount < 2) {
            return null;
        }

        String c = tokens[0];
        String chr = genome == null ? c : genome.getCanonicalChrName(c);

        //BED format, and IGV, use starting element as 0.
        int start = Integer.parseInt(tokens[1]);

        int end = start + 1;
        if (tokenCount > 2) {
            end = Integer.parseInt(tokens[2]);
        }

        BasicFeature feature = featureType == FeatureType.SPLICE_JUNCTION ?
                new SpliceJunctionFeature(chr, start, end) :
                new BasicFeature(chr, start, end);

        // The rest of the columns are optional.  Stop parsing upon encountering
        // a non-expected value

        // Name
        if (tokenCount > 3) {
            if (isGffTags()) {
                Map<String, String> atts = new LinkedHashMap<>();
                tagHelper.parseAttributes(tokens[3], atts);
                String name = tagHelper.getName(atts);
                feature.setName(name);

                String id = atts.get("ID");
                if (id != null) {
                    FeatureDB.addFeature(id, feature, genome);
                    feature.setIdentifier(id);
                } else {
                    feature.setIdentifier(name);
                }
                String alias = atts.get("Alias");
                if (alias != null) {
                    FeatureDB.addFeature(alias, feature, genome);
                }
                String geneSymbols = atts.get("Symbol");
                if (geneSymbols != null) {
                    String[] symbols = geneSymbols.split(",");
                    for (String sym : symbols) {
                        FeatureDB.addFeature(sym.trim(), feature, genome);
                    }
                }

                feature.setAttributes(atts);


            } else {
                String name = tokens[3].replaceAll("\"", "");
                if (name.equals(".")) name = "";   // Convention
                feature.setName(name);
                feature.setIdentifier(name);
            }
        }

        // Bed files are not always to-spec after the name field.  Stop parsing when we find an unexpected column.
        // Score

        if (tokenCount > 4) {
            try {
                float score = tokens[4].equals(".") ? 1000 : Float.parseFloat(tokens[4]);
                feature.setScore(score);
                if (featureType == FeatureType.SPLICE_JUNCTION) {
                    ((SpliceJunctionFeature) feature).setJunctionDepth((int) score);
                }
            } catch (NumberFormatException numberFormatException) {
                // Interpret as missing score value, set to 1000 => max value for score according to BED spec.
                feature.setScore(1000);
            }
        }

        // Strand
        if (tokenCount > 5) {
            String strandString = tokens[5].trim();
            char strand = (strandString.length() == 0)
                    ? ' ' : strandString.charAt(0);

            if (strand == '-') {
                feature.setStrand(Strand.NEGATIVE);
            } else if (strand == '+') {
                feature.setStrand(Strand.POSITIVE);
            } else {
                feature.setStrand(Strand.NONE);
            }
        }

        // Thick ends
        if (tokenCount > 7) {
            try {
                int thickStart = Integer.parseInt(tokens[6]);
                int thickEnd = Integer.parseInt(tokens[7]);
                feature.setThickStart(Math.max(start, thickStart));
                feature.setThickEnd(Math.min(end, thickEnd));
            } catch (NumberFormatException e) {
                return feature;
            }
        }

        // Color
        if (tokenCount > 8 && featureType != FeatureType.GAPPED_PEAK) {
            String colorString = tokens[8];
            if (colorString.trim().length() > 0 && !colorString.equals(".")) {
                feature.setColor(ColorUtilities.stringToColor(colorString));
            }
        }

        if (featureType == FeatureType.BED_METHYL && tokenCount > 10) {
            // Description from https://www.encodeproject.org/data-standards/wgbs/
            // 10.  Coverage, or number of reads
            // 11.  Percentage of reads that show methylation at this position in the genome
            String[] extraColumnHeadings = {"Coverage", "% Showing Methylation", "N-mod", "N-canonical", "N-other mod",
                    "N-delete", "N-fail", "N-dff", "N-nocall"};

            // Bed methyl files sometimes use space delimiters for columns > 9.

            Map<String, String> attributes = new LinkedHashMap<>();
            for (int i = 9; i < tokens.length; i++) {
                String heading = extraColumnHeadings[i - 9];
                attributes.put(heading, tokens[i]);
            }
            feature.setAttributes(attributes);

        } else {
            // Exons
            try {
                if (tokenCount > 11) {
                    createExons(start, tokens, feature, chr, feature.getStrand());
                    //todo: some refactoring that allows this hack to be removed
                    if (featureType == FeatureType.SPLICE_JUNCTION) {
                        SpliceJunctionFeature junctionFeature = (SpliceJunctionFeature) feature;

                        List<Exon> exons = feature.getExons();

                        junctionFeature.setJunctionStart(start + exons.get(0).getLength());
                        junctionFeature.setJunctionEnd(end - exons.get(1).getLength());

                    }
                }
            } catch (NumberFormatException e) {
                final String message = "Unexpected data in columns 10-12, expected exon count, exon starts, and exon sizes.  Found: " +
                        tokens[9] + "  " + tokens[10] + "  " + tokens[11] + "<br>Columns > 9 will be ignored";
                this.maxColumnCount = 9;
                MessageUtils.showMessage(message);
                log.warn(message, e);
                return feature;
            }

            if (tokenCount > 14 && featureType == FeatureType.GAPPED_PEAK) {
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("Signal Value", tokens[12]);
                attributes.put("pValue (-log10)", tokens[13]);
                attributes.put("qValue (-log10)", tokens[14]);
                feature.setAttributes(attributes);
            } else if (tokenCount > 13 && featureType == FeatureType.SPLICE_JUNCTION) {
                try {
                    String[] startFlanking = tokens[12].split(",");
                    int[] startFlankingDeptyArray = new int[startFlanking.length];
                    for (int i = 0; i < startFlanking.length; i++) {
                        startFlankingDeptyArray[i] = Integer.parseInt(startFlanking[i]);
                    }
                    String[] endFlanking = tokens[13].split(",");
                    int[] endFlankingDeptyArray = new int[endFlanking.length];
                    for (int i = 0; i < endFlanking.length; i++) {
                        endFlankingDeptyArray[i] = Integer.parseInt(endFlanking[i]);
                    }
                    ((SpliceJunctionFeature) feature).setStartFlankingRegionDepthArray(startFlankingDeptyArray);
                    ((SpliceJunctionFeature) feature).setEndFlankingRegionDepthArray(endFlankingDeptyArray);
                } catch (NumberFormatException e) {
                    log.error("Error parsing flanking array", e);
                }
            }
        }

        if (isCoding(feature)) {
            FeatureUtils.computeReadingFrames(feature);
        }

        return feature;
    }

    /**
     * Approximate test for a coding feature (transcript).  BED format does not specify this explicitly.
     * Rules applied are
     * <p>
     * (1) feature has exons
     * (2) feature has possible UTRs at both ends
     * (3) feature has strand
     *
     * @param feature
     * @return
     */
    public static boolean isCoding(BasicFeature feature) {
        return feature.hasExons() && feature.getThickStart() > feature.getStart() && feature.getThickEnd() < feature.getEnd()
                && feature.getStrand() != Strand.NONE;
    }


    /**
     * This function returns true iff the File potentialInput can be parsed by this
     * codec.
     * <p/>
     * There is an assumption that there's never a situation where two different Codecs
     * return true for the same file.  If this occurs, the recommendation would be to error out.
     * <p/>
     * Note this function must never throw an error.  All errors should be trapped
     * and false returned.
     *
     * @param path the file to test for parsability with this codec
     * @return true if potentialInput can be parsed, false otherwise
     */
    @Override
    public boolean canDecode(String path) {
        return path.toLowerCase().endsWith(".bed") || path.toLowerCase().endsWith(".bed.gz");
    }


    public static void createExons(int start, String[] tokens, BasicFeature gene, String chr,
                                   Strand strand) throws NumberFormatException {

        int cdStart = Integer.parseInt(tokens[6]);
        int cdEnd = Integer.parseInt(tokens[7]);

        int exonCount = Integer.parseInt(tokens[9]);
        String[] exonSizes = Globals.commaPattern.split(tokens[10]);
        String[] startsBuffer = Globals.commaPattern.split(tokens[11]);

        if (exonCount == exonSizes.length && exonCount == startsBuffer.length) {
            int exonNumber = (strand == Strand.NEGATIVE ? exonCount : 1);
            if (startsBuffer.length == exonSizes.length) {
                for (int i = 0; i < startsBuffer.length; i++) {
                    int exonStart = start + Integer.parseInt(startsBuffer[i]);
                    int exonEnd = exonStart + Integer.parseInt(exonSizes[i]);
                    Exon exon = new Exon(chr, exonStart, exonEnd, strand);
                    exon.setCodingStart(cdStart);
                    exon.setCodingEnd(cdEnd);
                    gene.addExon(exon);

                    exon.setNumber(exonNumber);
                    if (strand == Strand.NEGATIVE) {
                        exonNumber--;
                    } else {
                        exonNumber++;
                    }
                }
            }
        }
    }


    /**
     * Encode a feature as a BED string.
     *
     * @param feature - feature to encode
     * @return the encoded string
     */
    public String encode(Feature feature) {

        if (feature instanceof BasicFeature) {
            String rep = ((BasicFeature) feature).getRepresentation();
            if (rep != null) return rep;
        }

        StringBuffer buffer = new StringBuffer();

        buffer.append(feature.getChr());
        buffer.append("\t");
        final int featureStart = feature.getStart();
        buffer.append(String.valueOf(featureStart));
        buffer.append("\t");
        buffer.append(String.valueOf(feature.getEnd()));

        BasicFeature basicFeature = null;

        if (!(feature instanceof BasicFeature)) {
            return buffer.toString();
        } else {
            basicFeature = (BasicFeature) feature;
        }

        boolean hasName = (basicFeature.getName() != null && basicFeature.getName().length() > 0) ||
                (isGffTags() && basicFeature.getDescription() != null && basicFeature.getDescription().length() > 0);

        if (hasName) {
            buffer.append("\t");

            if (isGffTags() && basicFeature.getDescription() != null) {
                // mRNA<br>ID = LOC_Os01g01010.2<br>Name = LOC_Os01g01010.2<br>Parent = LOC_Os01g01010<br>
                //ID=LOC_Os01g01010.1:exon_1;Parent=LOC_Os01g01010.1
                String[] attrs = BR_PATTERN.split(basicFeature.getDescription());
                buffer.append("\"");
                for (String att : attrs) {
                    String[] kv = EQ_PATTERN.split(att, 2);
                    if (kv.length > 1) {
                        buffer.append(kv[0].trim());
                        buffer.append("=");
                        String value = kv[1].trim();
                        buffer.append(StringUtils.encodeURL(value));
                        buffer.append(";");
                    }
                }
                buffer.append("\"");
            } else {
                buffer.append(basicFeature.getName());
            }
        }

        boolean more = !Float.isNaN(basicFeature.getScore()) || basicFeature.getStrand() != Strand.NONE ||
                basicFeature.getColor() != null || basicFeature.getExonCount() > 0;

        if (more) {

            // Must have a non-whitespace name column to proceed
            if (!hasName) {
                buffer.append("\t.");
            }

            buffer.append("\t");
            // UCSC scores are integers between 0 and 1000, but
            float score = basicFeature.getScore();
            if (Float.isNaN(score)) {
                buffer.append("1000");

            } else {
                boolean isInt = (Math.floor(score) == score);
                buffer.append(String.valueOf(isInt ? (int) score : score));
            }


            more = basicFeature.getStrand() != Strand.NONE || basicFeature.getColor() != null ||
                    (basicFeature.getThickStart() != basicFeature.getStart()) || basicFeature.getExonCount() > 0;

            if (more) {

                buffer.append("\t");
                Strand strand = basicFeature.getStrand();
                if (strand == Strand.NONE) buffer.append(" ");
                else if (strand == Strand.POSITIVE) buffer.append("+");
                else if (strand == Strand.NEGATIVE) buffer.append("-");

                more = basicFeature.getColor() != null || basicFeature.getExonCount() > 0;

                if (more) {
                    // Must continue if basicFeature has color or exons
                    java.util.List<Exon> exons = basicFeature.getExons();


                    int thickStart, thickEnd;
                    if (basicFeature.getColor() != null || exons != null) {

                        // Correct "thickStart" and "thickEnd"
                        if (exons != null && exons.size() > 0) {
                            thickStart = basicFeature.getEnd();   // This is not a typo
                            for (Exon ex : exons) {
                                if (!ex.isNonCoding()) {
                                    thickStart = ex.getCdStart();
                                    break;
                                }
                            }
                            thickEnd = basicFeature.getStart();    // Not a typo
                            for (int i = exons.size() - 1; i >= 0; i--) {
                                Exon ex = exons.get(i);
                                if (!ex.isNonCoding()) {
                                    thickEnd = ex.getCdEnd();
                                    break;
                                }
                            }
                        } else {
                            thickStart = ((BasicFeature) feature).getThickStart();
                            thickEnd = ((BasicFeature) feature).getThickEnd();
                        }


                        buffer.append("\t");
                        buffer.append(String.valueOf(thickStart));
                        buffer.append("\t");
                        buffer.append(String.valueOf(thickEnd));
                        buffer.append("\t");

                        java.awt.Color c = basicFeature.getColor();
                        buffer.append(c == null ? "." : ColorUtilities.colorToString(c));
                        buffer.append("\t");

                        if (exons != null && exons.size() > 0) {
                            buffer.append(String.valueOf(exons.size()));
                            buffer.append("\t");

                            for (Exon exon : exons) {
                                buffer.append(String.valueOf(exon.getLength()));
                                buffer.append(",");
                            }
                            buffer.append("\t");
                            for (Exon exon : exons) {
                                int exonStart = exon.getStart() - featureStart;
                                buffer.append(String.valueOf(exonStart));
                                buffer.append(",");
                            }

                        }
                    }

                    if (basicFeature instanceof SpliceJunctionFeature) {
                        SpliceJunctionFeature spliceJunctionFeature = (SpliceJunctionFeature) basicFeature;
                        int[] startFlanking = spliceJunctionFeature.getStartFlankingRegionDepthArray();
                        int[] endFlanking = spliceJunctionFeature.getEndFlankingRegionDepthArray();
                        if (startFlanking != null && startFlanking.length > 0 && endFlanking != null && endFlanking.length > 0) {
                            buffer.append("\t" + startFlanking[0]);
                            for (int i = 1; i < startFlanking.length; i++) {
                                buffer.append("," + startFlanking[i]);
                            }
                            buffer.append("\t" + endFlanking[0]);
                            for (int i = 1; i < endFlanking.length; i++) {
                                buffer.append("," + endFlanking[i]);
                            }
                        }
                    }
                }
            }
        }

        return buffer.toString();
    }

}


