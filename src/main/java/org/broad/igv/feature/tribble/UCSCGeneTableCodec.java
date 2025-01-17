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
import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.Exon;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.feature.Strand;
import org.broad.igv.feature.genome.Genome;

import java.util.List;

/**
 * @author Jim Robinson
 * @date 11/19/11
 */
public class UCSCGeneTableCodec extends UCSCCodec<IGVFeature> {

    private int nameColumn = 0;
    private int idColumn = 1;
    private int chrColumn = 2;
    private int strandColumn = 3;
    private int startColumn = 4;
    private int endColumn = 5;
    private int cdStartColumn = 6;
    private int cdEndColumn = 7;
    private int exonCountColumn = 8;
    private int startsBufferColumn = 9;
    private int endsBufferColumn = 10;
    private int frameBufferColumn = 15;
    private int scoreColumn = -1;

    public enum Type {

        REFFLAT, GENEPRED, UCSCGENE, GENEPRED_EXT
    }

    private Genome genome;
    private Type type;


    public UCSCGeneTableCodec(Type type, Genome genome) {
        super(BasicFeature.class);

        this.genome = genome;
        this.type = type;
        switch (type) {
            case REFFLAT:
                break;
            case UCSCGENE:
                idColumn = 0;
                chrColumn = 1;
                strandColumn = 2;
                startColumn = 3;
                endColumn = 4;
                cdStartColumn = 5;
                cdEndColumn = 6;
                exonCountColumn = 7;
                startsBufferColumn = 8;
                endsBufferColumn = 9;
                nameColumn = 10;
                break;
            case GENEPRED_EXT:
                idColumn = 0;
                chrColumn = 1;
                strandColumn = 2;
                startColumn = 3;
                endColumn = 4;
                cdStartColumn = 5;
                cdEndColumn = 6;
                exonCountColumn = 7;
                startsBufferColumn = 8;
                endsBufferColumn = 9;
                scoreColumn = 10;
                nameColumn = 11;
                frameBufferColumn = 14;
                break;
            case GENEPRED:
                nameColumn = 12;
        }

    }

    /**
     * Decode a line as a Feature.
     *
     * @param line the input line to decode
     * @return Return the Feature encoded by the line,  or null if the line does not represent a feature (e.g. is
     * a comment)
     */
    public BasicFeature decode(String line) {
        if (line.startsWith("#")) {
            //Header line
            readHeaderLine(line);
            return null;
        }

        line = line.replaceAll("\"", "");
        String[] tokens = Globals.tabPattern.split(line);
        BasicFeature gene = decode(tokens);
        gene.setRepresentation(line);
        return gene;
    }

    public BasicFeature decode(String[] tokens) {

        int tokenCount = tokens.length;

        if (tokenCount <= strandColumn) {
            return null;
        }

        String identifier = tokens[idColumn].trim();
        String name = null;
        if (tokenCount > nameColumn && tokens[nameColumn] != null) {
            name = tokens[nameColumn];
        }

        if (name == null || name.length() == nameColumn) {
            name = identifier;
        }

        String chrToken = tokens[chrColumn].trim();
        String chr = genome == null ? chrToken : genome.getCanonicalChrName(chrToken);

        int start = Integer.parseInt(tokens[startColumn]);
        int end = Integer.parseInt(tokens[endColumn]);
        String strandString = tokens[strandColumn];
        Strand strand = Strand.NONE;
        if (strandString != null) {
            if (strandString.trim().equals("+")) {
                strand = Strand.POSITIVE;
            } else if (strandString.trim().equals("-")) {
                strand = Strand.NEGATIVE;
            }
        }

        BasicFeature gene = new BasicFeature(chr, start, end, strand);


        gene.setName(name);
        gene.setIdentifier(identifier);

        if (tokenCount > 7) {
            gene.setThickStart(Integer.parseInt(tokens[6]));
            gene.setThickEnd(Integer.parseInt(tokens[7]));
        }

        if (scoreColumn > 0 && tokenCount > scoreColumn) {
            gene.setScore((float) Double.parseDouble(tokens[scoreColumn]));
        }

        // Coding information is optional
        if (tokenCount > 8) {
            createExons(tokens, tokenCount, gene, chr, strand);
        }

        // Optional standard name column
        if (tokenCount > 16) {
            gene.setAttribute("Standard Name", tokens[16]);
        }

        return gene;
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
        return true;
    }


    private void createExons(String[] tokens, int tokenCount, BasicFeature gene, String chr,
                             Strand strand)
            throws NumberFormatException {

        int cdStart = Integer.parseInt(tokens[cdStartColumn]);
        int cdEnd = Integer.parseInt(tokens[cdEndColumn]);

        int exonCount = Integer.parseInt(tokens[exonCountColumn]);
        String[] startsBuffer = Globals.commaPattern.split(tokens[startsBufferColumn]);
        String[] endsBuffer = Globals.commaPattern.split(tokens[endsBufferColumn]);

        if (startsBuffer.length == endsBuffer.length) {

            int exonNumber = (strand == Strand.NEGATIVE ? exonCount : 1);

            for (int i = 0; i < startsBuffer.length; i++) {
                int exonStart = Integer.parseInt(startsBuffer[i]);
                int exonEnd = Integer.parseInt(endsBuffer[i]);
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

        List<Exon> exons = gene.getExons();

        // Walk through exons setting mRNA start position
        int start = strand == Strand.POSITIVE ? 0 : exonCount - 1;
        int end = strand == Strand.POSITIVE ? exonCount : -1;
        int increment = strand == Strand.POSITIVE ? 1 : -1;

        int mRNABase = 0;
        for (int i = start; i != end; i += increment) {
            Exon exon = exons.get(i);
            exon.setMrnaBase(mRNABase);
            mRNABase += exon.getCodingLength();
        }


        if (type == Type.GENEPRED && tokenCount > 15) {
            String[] frameBuffer = Globals.commaPattern.split(tokens[frameBufferColumn]);
            for (int i = 0; i < frameBuffer.length; i++) {
                int exonFrame = Integer.parseInt(frameBuffer[i].trim());
                if (exonFrame == -1) {
                    exons.get(i).setNonCoding(true);
                } else {
                    exons.get(i).setReadingFrame(exonFrame);
                }
            }
        }
    }

}
