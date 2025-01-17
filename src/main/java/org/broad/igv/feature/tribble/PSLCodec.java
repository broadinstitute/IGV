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

/**
 * @author jrobinso
 * @date Aug 5, 2010
 * <p/>
 * Columns:
 * <p/>
 * 1. matches - Number of bases that match that aren't repeats
 * 2. misMatches - Number of bases that don't match
 * 3. repMatches - Number of bases that match but are part of repeats
 * 4. nCount - Number of 'N' bases
 * 5. qNumInsert - Number of inserts in query
 * 6. qBaseInsert - Number of bases inserted in query
 * 7. tNumInsert - Number of inserts in target
 * 8. tBaseInsert - Number of bases inserted in target
 * 9. strand - '+' or '-' for query strand. For translated alignments, second '+'or '-' is for genomic strand
 * 10. qName - Query sequence name
 * 11. qSize - Query sequence size
 * 12. qStart - Alignment start position in query
 * 13. qEnd - Alignment end position in query
 * 14. tName - Target sequence name
 * 15. tSize - Target sequence size
 * 16. tStart - Alignment start position in target
 * 17. tEnd - Alignment end position in target
 * 18. blockCount - Number of blocks in the alignment (a block contains no gaps)
 * 19. blockSizes - Comma-separated list of sizes of each block
 * 20. qStarts - Comma-separated list of starting positions of each block in query
 * 21. tStarts - Comma-separated list of starting positions of each block in target
 */
public class PSLCodec extends UCSCCodec<IGVFeature> {

    Genome genome;
    boolean keepText;

    public PSLCodec() {
        this(null);
    }

    public PSLCodec(Genome genome) {
        this(genome, false);
    }

    public PSLCodec(Genome genome, boolean keepText) {
        super(BasicFeature.class);
        this.genome = genome;
        this.keepText = keepText;

    }

    public PSLRecord decode(String line) {

        try {
            if (line.trim().length() == 0 ||
                    line.startsWith("#") ||
                    line.startsWith("track") ||
                    line.startsWith("browser") ||
                    line.startsWith("psLayout") ||
                    line.startsWith("match") ||
                    line.startsWith("---")) {
                return null;
            }

            String[] tokens = Globals.tabPattern.split(line);
            return decode(tokens);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    public PSLRecord decode(String[] tokens) {
            return getPslRecord(tokens, genome);
    }



    /*
     "matches",
     "misMatches",
     "repMatches",
     "nCount",
     "qNumInsert",
     "qBaseInsert",
     "tNumInsert",
     "tBaseInsert",
     "strand",
     "qName",
     "qSize",
     "qStart",
     "qEnd",
     "tName",   => chr
     "tSize",
     "tEnd",
     "blockCount",
     "blockSizes",
     "qStarts",
     "tStarts"
     */
    public static PSLRecord getPslRecord(String[] tokens, Genome genome) {
        PSLRecord f;
        int nTokens = tokens.length;
        if (nTokens < 21) {
            // log.warn("Skipping line ")
            return null;
        }
        int tSize = Integer.parseInt(tokens[14]);
        String chrToken = tokens[13];
        String chr = genome == null ? chrToken : genome.getCanonicalChrName(chrToken);
        int start = Integer.parseInt(tokens[15]); // IS PSL 1 or ZERO based,  closed or open?

        String strandString = tokens[8];
        Strand strand = strandString.startsWith("+") ? Strand.POSITIVE : Strand.NEGATIVE;

        boolean gNeg = false;
        if (strandString.length() > 1) {
            gNeg = strandString.charAt(1) == '-';
        }

        f = new PSLRecord();
        f.setName(tokens[9]);
        f.setChr(chr);
        f.setStart(start);
        f.setEnd(Integer.parseInt(tokens[16]));
        f.setStrand(strand);

        int exonCount = Integer.parseInt(tokens[17]);
        String[] exonSizes = tokens[18].split(",");
        String[] startsBuffer = tokens[20].split(",");

        if (startsBuffer.length == exonSizes.length && exonCount == startsBuffer.length) {
            for (int i = 0; i < startsBuffer.length; i++) {
                int exonSize = Integer.parseInt(exonSizes[i]);
                int exonStart = Integer.parseInt(startsBuffer[i]);
                int exonEnd = exonStart + exonSize;
                Exon exon = new Exon(chr, exonStart, exonEnd, strand);
                f.addExon(exon);
            }
        } else {
            // TODO -- warn
        }

        //score = percentId = 100.0 * (match + repMatch)  / (misMatch + match + repMatch + qGapCount + tGapCount)
        int match = Integer.parseInt(tokens[0]);
        int misMatch = Integer.parseInt(tokens[1]);
        int repMatch = Integer.parseInt(tokens[2]);
        int ns = Integer.parseInt(tokens[3]);
        int qGapCount = Integer.parseInt(tokens[4]);
        int qGapBases = Integer.parseInt(tokens[5]);
        int tGapCount = Integer.parseInt(tokens[6]);
        int tGapBases = Integer.parseInt(tokens[7]);
        int qSize = Integer.parseInt(tokens[10]);
        int qStart = Integer.parseInt(tokens[11]);
        int qEnd = Integer.parseInt(tokens[12]);

        float score = (1000.0f * (match + repMatch - misMatch - qGapCount - tGapCount)) / qSize;

        f.setMatch(match);
        f.setMisMatch(misMatch);
        f.setRepMatch(repMatch);
        f.setNs(ns);
        f.setQGapCount(qGapCount);
        f.setQGapBases(qGapBases);
        f.setTGapCount(tGapCount);
        f.setTGapBases(tGapBases);
        f.setQSize(qSize);
        f.setqStart(qStart);
        f.setqEnd(qEnd);
        f.setBlockQueryStarts(tokens[19]);

        f.setScore(score);

        // Build description
        StringBuffer desc = new StringBuffer();
        desc.append("matches = " + tokens[0]);
        desc.append("<br>");
        desc.append("mismatches = " + tokens[1]);
        desc.append("<br>");
        desc.append("repeat matches = " + tokens[2]);
        desc.append("<br>");
        desc.append("# inserts in query = " + tokens[4]);
        desc.append("<br>");
        desc.append("# inserts in target = " + tokens[6]);
        f.setDescription(desc.toString());
        return f;
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
    public boolean canDecode(String path) {
        return path.toLowerCase().endsWith(".psl") || path.toLowerCase().endsWith(".psl.gz");
    }
}
