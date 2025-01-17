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

import org.broad.igv.Globals;
import org.broad.igv.feature.*;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.util.StringUtils;
import htsjdk.tribble.Feature;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
/**
 * Codec for UCSC DGV Table
 *
 * @author jrobinso
 *         Date: 10/30/13
 *         Time: 8:23 AM
 *
field	example	SQL type	info	description
1 bin	73	smallint(6)	range	Indexing field to speed chromosome range queries.
2 chrom	chr1	varchar(255)	values	Reference sequence chromosome or scaffold
3 chromStart	10000	int(10) unsigned	range	Start position in chromosome
4 chromEnd	846808	int(10) unsigned	range	End position in chromosome
5 name	dgv2n71	varchar(255)	values	ID of merged variant or supporting variant
6 score	0	int(10) unsigned	range	Score from 0-1000 (placeholder for BED 9+ format)
7 strand	+	char(1)	values	+ or - (placeholder for BED 9+ format)
8 thickStart	10000	int(10) unsigned	range	Same as chromStart (placeholder for BED 9+ format)
9 thickEnd	10000	int(10) unsigned	range	Same as chromStart (placeholder for BED 9+ format)
10 itemRgb	200	int(10) unsigned	range	Item R,G,B color.
11 varType	Gain	varchar(255)	values	Type of variation
12 reference	Xu et al 2011	varchar(255)	values	Literature reference for the study that included this variant
13 pubMedId	21882294	int(10) unsigned	range	For linking to pubMed abstract of reference
14 method	SNP array	longblob	 	Brief description of method
15 platform	 	longblob	 	Sequencing platform (if specified)
16 mergedVariants	 	varchar(255)	values	If this is a supporting variant, ID of merged variant
17 supportingVariants	nsv871664,nsv871113	longblob	 	If this is a merged variant, IDs of supporting variants
18 sampleSize	6533	int(10) unsigned	range	Number of samples in study
19 observedGains	3	int(10) unsigned	range	Number of samples with copy number gains
20 observedLosses	0	int(10) unsigned	range	Number of samples with copy number losses
21 cohortDescription	 	longblob	 	Description of sample population for the study
22 genes	FAM138A, FAM138F, FAM41C, L...	longblob	 	Genes overlapping this variant
23 samples	IS30771,IS39243,IS41043	longblob	 	Sample IDs if available
 */


/**
 *
 */
public class DGVCodec extends UCSCCodec<IGVFeature>  {

    static final Pattern BR_PATTERN = Pattern.compile("<br>");
    static final Pattern EQ_PATTERN = Pattern.compile("=");

    Genome genome;

    public DGVCodec() {
        this(null);
    }

    public DGVCodec(Genome genome) {
        super(BasicFeature.class);
        this.genome = genome;
    }


    static String[] attributeLabels = {"Type", "Reference", "PubMed ID", "Method", "Platform", "Merged variants",
            "Supporting variants", "Sample size", "Observed gains", "Observed losses", "Cohort description",
            "Genes", "Samples"};

    public BasicFeature decode(String[] tokens) {
        int tokenCount = tokens.length;


        String c = tokens[0];
        String chr = genome == null ? c : genome.getCanonicalChrName(c);

        //BED format, and IGV, use starting element as 0.
        int start = Integer.parseInt(tokens[1]);
        int end = Integer.parseInt(tokens[2]);

        BasicFeature feature = new BasicFeature(chr, start, end);

        String name = tokens[3].replaceAll("\"", "");
        feature.setName(name);
        feature.setIdentifier(name);

        String colorString = tokens[4];
        if (colorString.trim().length() > 0 && !colorString.equals(".")) {
            feature.setColor(ColorUtilities.stringToColor(colorString));
        }

        for (int i = 5; i < tokens.length; i++) {
            if (tokens[i].length() > 0)
                feature.setAttribute(attributeLabels[i - 5], tokens[i]);
        }

        /*
        varType	Gain	varchar(255)	values	Type of variation
reference	Xu et al 2011	varchar(255)	values	Literature reference for the study that included this variant
pubMedId	21882294	int(10) unsigned	range	For linking to pubMed abstract of reference
method	SNP array	longblob	 	Brief description of method
platform	 	longblob	 	Sequencing platform (if specified)
mergedVariants	 	varchar(255)	values	If this is a supporting variant, ID of merged variant
supportingVariants	nsv871664,nsv871113	longblob	 	If this is a merged variant, IDs of supporting variants
sampleSize	6533	int(10) unsigned	range	Number of samples in study
observedGains	3	int(10) unsigned	range	Number of samples with copy number gains
observedLosses	0	int(10) unsigned	range	Number of samples with copy number losses
cohortDescription	 	longblob	 	Description of sample population for the study
genes	FAM138A, FAM138F, FAM41C, L...	longblob	 	Genes overlapping this variant
samples	IS30771,IS39243,IS41043	longblob	 	Sample IDs if available
         */

        return feature;
    }

    @Override
    public BasicFeature decode(String nextLine) {

        if (nextLine.trim().length() == 0) {
            return null;
        }

        if (nextLine.startsWith("#") || nextLine.startsWith("track") || nextLine.startsWith("browser")) {
            this.readHeaderLine(nextLine);
            return null;
        }

        String[] tokens = Globals.tabPattern.split(nextLine);

        return decode(tokens);
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
        return path.toLowerCase().endsWith(".bed");
    }


    /**
     * Encode a feature as a BED string.
     *
     * @param feature - feature to encode
     * @return the encoded string
     */
    public String encode(Feature feature) {

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

        if (basicFeature.getName() != null || (isGffTags() && basicFeature.getDescription() != null)) {

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

            boolean more = !Float.isNaN(basicFeature.getScore()) || basicFeature.getStrand() != Strand.NONE ||
                    basicFeature.getColor() != null || basicFeature.getExonCount() > 0;

            if (more) {
                buffer.append("\t");
                // UCSC scores are integers between 0 and 1000, but
                float score = basicFeature.getScore();
                if (Float.isNaN(score)) {
                    buffer.append("1000");

                } else {
                    boolean isInt = (Math.floor(score) == score);
                    buffer.append(String.valueOf(isInt ? (int) score : score));
                }


                more = basicFeature.getStrand() != Strand.NONE || basicFeature.getColor() != null || basicFeature.getExonCount() > 0;
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
                        if (basicFeature.getColor() != null || exons != null) {
                            buffer.append("\t");
                            buffer.append(String.valueOf(basicFeature.getThickStart()));
                            buffer.append("\t");
                            buffer.append(String.valueOf(basicFeature.getThickEnd()));
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
                    }
                }
            }
        }

        return buffer.toString();
    }

}


