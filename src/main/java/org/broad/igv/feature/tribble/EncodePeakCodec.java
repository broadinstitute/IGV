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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Codec for UCSC / ENCDOE "broad and narrow peak" files (http://genome.ucsc.edu/FAQ/FAQformat.html#format13)
 * <p/>
 * This format is used to provide called peaks of signal enrichment based on pooled, normalized (interpreted) data. It is a BED6+4 format.
 * <p/>
 * 0 chrom - Name of the chromosome (or contig, scaffold, etc.).
 * 1 chromStart - The starting position of the feature in the chromosome or scaffold. The first base in a chromosome is numbered 0.
 * 2 chromEnd - The ending position of the feature in the chromosome or scaffold. The chromEnd base is not included in the display of the feature. For example, the first 100 bases of a chromosome are defined as chromStart=0, chromEnd=100, and span the bases numbered 0-99.
 * 3 name - Name given to a region (preferably unique). Use '.' if no name is assigned.
 * 4 score - Indicates how dark the peak will be displayed in the browser (0-1000). If all scores were '0' when the data were submitted to the DCC, the DCC assigned scores 1-1000 based on signal value. Ideally the average signalValue per base spread is between 100-1000.
 * 5 strand - +/- to denote strand or orientation (whenever applicable). Use '.' if no orientation is assigned.
 * 6 signalValue - Measurement of overall (usually, average) enrichment for the region.
 * 7 pValue - Measurement of statistical significance (-log10). Use -1 if no pValue is assigned.
 * 8 qValue - Measurement of statistical significance using false discovery rate (-log10). Use -1 if no qValue is assigned.
 * 9 peak (Narrow peak only) - Point-source called for this peak; 0-based offset from chromStart. Use -1 if no point-source called.
 *
 * @author jrobinso
 * Date: 10/16/12
 * Time: 11:35 AM
 */
public class EncodePeakCodec extends UCSCCodec {

    Genome genome;

    public EncodePeakCodec() {
        this(null);
    }

    public EncodePeakCodec(Genome genome) {
        super(BasicFeature.class);
        this.genome = genome;
    }

    @Override
    public BasicFeature decode(String [] tokens) {

        int tokenCount = tokens.length;


        if (tokenCount < 9) {
            return null;
        }

        String c = tokens[0];
        String chr = genome == null ? c : genome.getCanonicalChrName(c);

        //BED format, and IGV, use starting element as 0.
        int start = Integer.parseInt(tokens[1]);
        int end = Integer.parseInt(tokens[2]);
        BasicFeature feature = new BasicFeature(chr, start, end);

        feature.setName(tokens[3]);
        feature.setScore(Float.parseFloat(tokens[4]));


        Strand strand;
        String strandString = tokens[5].trim();
        char strandChar = (strandString.length() == 0) ? ' ' : strandString.charAt(0);

        if (strandChar == '-') {
            strand = Strand.NEGATIVE;
        } else if (strandChar == '+') {
            strand = Strand.POSITIVE;
        } else {
            strand = Strand.NONE;
        }
        feature.setStrand(strand);

        // Store the remaining features in description string */
        Map<String, String> attributes = new LinkedHashMap<>();
        if (tokens.length > 6) attributes.put("signalValue", tokens[6]);
        if (tokens.length > 7) attributes.put("pValue", tokens[7]);
        if (tokens.length > 8) attributes.put("qValue", tokens[8]);
        if (tokens.length > 9) attributes.put("peak", tokens[9]);
        feature.setAttributes(attributes);

        return feature;
    }

    @Override
    public boolean canDecode(String s) {
        return s.toLowerCase().endsWith("peak");
    }
}
