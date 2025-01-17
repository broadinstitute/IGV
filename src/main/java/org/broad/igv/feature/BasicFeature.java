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

package org.broad.igv.feature;


import org.broad.igv.logging.*;
import org.broad.igv.feature.aa.*;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.WindowFunction;

import java.util.*;

/**
 * A convenience class providing default implementation for many IGVFeature
 * methods.
 *
 * @author jrobinso
 */
public class BasicFeature extends AbstractFeature {

    private static Logger log = LogManager.getLogger(BasicFeature.class);

    String representation;

    protected List<Exon> exons;

    protected float score = Float.NaN;
    protected float confidence;
    String identifier;
    private int thickEnd;
    private int thickStart;

    String[] parentIds;
    String link;


    public BasicFeature() {
    }


    public BasicFeature(String chr, int start, int end) {

        this(chr, start, end, Strand.NONE);
        this.thickStart = start;
        this.thickEnd = end;
    }


    public BasicFeature(String chr, int start, int end, Strand strand) {
        super(chr, start, end, strand);
        this.thickStart = start;
        this.thickEnd = end;

    }

    public BasicFeature(BasicFeature feature) {
        super(feature.getChr(), feature.getStart(), feature.getEnd(), feature.getStrand());
        super.setName(feature.getName());
        this.confidence = feature.confidence;
        this.color = feature.color;
        this.description = feature.description;
        this.exons = feature.exons;
        this.score = feature.score;
        this.identifier = feature.identifier;
        this.type = feature.type;
        this.link = feature.link;
        this.thickStart = feature.thickStart;
        this.thickEnd = feature.thickEnd;
        this.attributes = feature.attributes;
    }

    public String getRepresentation() {
        return representation;
    }

    public void setRepresentation(String representation) {
        this.representation = representation;
    }

    /**
     * @param identifier
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public void setParentIds(String[] parentIds) {
        this.parentIds = parentIds;
    }

    public String[] getParentIds() {
        return parentIds;
    }

    @Override
    public void setStart(int start) {
        super.setStart(start);
        if(start > thickStart) {
            this.thickStart = start;
        }
    }

    @Override
    public void setEnd(int end) {
        super.setEnd(end);
        if(end < thickEnd || thickEnd == 0) {
            this.thickEnd = end;
        }
    }

    /**
     * Defined in interface {@linkplain LocusScore}
     */
    public String getValueString(double position, int mouseX, WindowFunction ignored) {

        StringBuffer valueString = new StringBuffer();

        String name = getName();
        if (name != null && name.length() > 0) {
            valueString.append("<b>name</b>:&nbsp;" + name);
        }

        valueString.append("<br><b>location</b>:&nbsp;");
        valueString.append(getLocusString());
        if (strand == Strand.POSITIVE) {
            valueString.append(" (+)");
        } else if (strand == Strand.NEGATIVE) {
            valueString.append(" (-)");
        }

        if (type != null && type.length() > 0) {
            valueString.append("<br><b>type</b>:&nbsp;" + type);
        }
        if ((identifier != null && identifier.length() > 0) && ((name == null || name.length() == 0) || !name.equals(identifier))) {
            valueString.append("<br><b>id</b>:&nbsp;" + identifier);
        }

        if (!Float.isNaN(score)) {
            valueString.append("<br><b>score</b>:&nbsp;" + score);
        }
        if (description != null) {
            valueString.append("<br><b>score</b>:&nbsp;" + description);
        }
        if (attributes != null) {
            valueString.append(getAttributeString());
        }


        // Get exon number, if over an exon
        int posZero = (int) position;
        if (this.exons != null) {
            for (Exon exon : exons) {
                if (posZero >= exon.getStart() && posZero < exon.getEnd()) {
                    String exonString = exon.getValueString(position, mouseX, ignored);
                    if (exonString != null && exonString.length() > 0) {
                        valueString.append("<br>--------------<br>");
                        valueString.append(exonString);
                    }
                }
            }
        }

        return valueString.toString();
    }

    public void setScore(float score) {
        this.score = score;
    }

    @Override
    public float getScore() {
        return score;
    }

    @Override
    public List<Exon> getExons() {
        return exons;
    }

    @Override
    public boolean hasExons() {
        return exons != null && exons.size() > 0;
    }


    /**
     * Sort the exon collection, if any, by start position.
     */
    public void sortExons() {
        if (exons != null) {
            Collections.sort(exons, new Comparator<IGVFeature>() {
                public int compare(IGVFeature arg0, IGVFeature arg1) {
                    return arg0.getStart() - arg1.getStart();
                }
            });
        }
    }

    public void addExon(Exon region) {
        if (exons == null) {
            exons = new ArrayList();
        }
        setStart(Math.min(getStart(), region.getStart()));
        setEnd(Math.max(getEnd(), region.getEnd()));
        exons.add(region);
    }

    /**
     * Add a UTR or CDS feature from a GFF or EMBL/NCBI type format.  If the feature overlaps an existing exon merge
     * the two, otherwise create a new one.
     *
     * @param bf
     */
    public void addUTRorCDS(BasicFeature bf) {
        boolean found = false;
        if (exons == null) {
            exons = new ArrayList();
        }

        final String exonType = bf.getType();
        for (Exon exon : exons) {
            if (exon.contains(bf)) {
                // Replace exon attributes with coding features.  Perhaps in the future we will merge them.
                exon.setAttributes(bf.getAttributes());
                if (SequenceOntology.cdsTypes.contains(exonType)) {
                    exon.setNonCoding(false);
                    exon.setCodingStart(bf.getStart());
                    exon.setCodingEnd(bf.getEnd());
                    exon.setReadingFrame(bf.getReadingFrame());

                } else if (SequenceOntology.utrTypes.contains(exonType)) {
                    exon.setNonCoding(true);
                    boolean rhs =
                            (SequenceOntology.fivePrimeUTRTypes.contains(exonType) && bf.getStrand() == Strand.POSITIVE) ||
                                    (SequenceOntology.threePrimeUTRTypes.contains(exonType) && bf.getStrand() == Strand.NEGATIVE);
                    if (rhs) {
                        exon.setCodingStart(bf.getEnd());
                        exon.setCodingEnd(bf.getEnd());
                    } else {
                        exon.setCodingEnd(bf.getStart());
                        exon.setCodingStart(bf.getStart());
                    }
                }

                found = true;
                break;
            }
        }

        if (!found) {
            // No match
            final Exon exon = new Exon(bf);
            exon.setNonCoding(!SequenceOntology.cdsTypes.contains(bf.getType()));
            addExon(exon);
        }

    }


    public String getIdentifier() {
        return identifier;
    }

    public int getExonCount() {
        return (exons == null) ? 0 : exons.size();
    }

    public void setURL(String link) {
        this.link = link;
    }

    @Override
    public String getDisplayName(String property) {
        if (property.equals("id")) {
            return identifier;
        } else {
            return super.getDisplayName(property);
        }
    }

    public String getURL() {
        return link;
    }

    public int getThickEnd() {
        return thickEnd;
    }

    public void setThickEnd(int thickEnd) {
        this.thickEnd = thickEnd;
    }

    public int getThickStart() {
        return thickStart;
    }

    public void setThickStart(int thickStart) {
        this.thickStart = thickStart;
    }

    /**
     * @param genome
     * @param proteinPosition 1-Indexed position of protein
     * @return
     */
    public Codon getCodon(Genome genome, String chr, int proteinPosition) {
        // Nucleotide position on the coding portion of the transcript (the untranslated messenger RNA)
        int startTranscriptPosition = (proteinPosition - 1) * 3;
        int[] featurePositions = new int[]{startTranscriptPosition, startTranscriptPosition + 1,
                startTranscriptPosition + 2};
        int[] genomePositions = featureToGenomePosition(featurePositions);
        Codon codonInfo = new Codon(getChr(), proteinPosition, getStrand());
        for (int gp : genomePositions) {
            codonInfo.setNextGenomePosition(gp);
        }
        if (!codonInfo.isGenomePositionsSet()) {
            //Protein position invalid, could not find genomic sequence
            return null;
        }
        codonInfo.calcSequence(genome);

        CodonTable codonTable = CodonTableManager.getInstance().getCodonTableForChromosome(chr);
        AminoAcid aa = codonTable.getAminoAcid(codonInfo.getSequence());
        if (aa != null) {
            codonInfo.setAminoAcid(aa);
            return codonInfo;
        } else {
            return null;
        }
    }

    /**
     * Convert a series of feature positions into genomic positions.
     *
     * @param featurePositions Must be 0-based.
     * @return Positions relative to genome (0-based). Will contain "-1"s for
     * positions not found. Sorted ascending for positive strand,
     * descending for negative strand.
     */
    int[] featureToGenomePosition(int[] featurePositions) {
        List<Exon> exons = getExons();
        int[] genomePositions = new int[featurePositions.length];
        Arrays.fill(genomePositions, -1);

        if (exons != null) {

            if (getStrand() == Strand.NONE) {
                throw new IllegalStateException("Exon not on a strand");
            }
            boolean positive = getStrand() == Strand.POSITIVE;

            /*
             We loop over all exons, either from the beginning or the end.
             Increment position only on coding regions.
             */

            int genomePosition, posIndex = 0, all_exon_counter = 0;
            int current_exon_end = 0;
            Exon exon;
            for (int exnum = 0; exnum < exons.size(); exnum++) {

                if (positive) {
                    exon = exons.get(exnum);
                } else {
                    exon = exons.get(exons.size() - 1 - exnum);
                }

                int exon_length = exon.getCodingLength();
                genomePosition = positive ? exon.getCdStart() : exon.getCdEnd() - 1;
                current_exon_end += exon_length;
                int incr = positive ? 1 : -1;

                int interval;
                while (featurePositions[posIndex] < current_exon_end) {
                    //Position of interest is on this exon
                    //Can happen up to exon_length times
                    interval = featurePositions[posIndex] - all_exon_counter;
                    all_exon_counter = featurePositions[posIndex];
                    genomePosition += interval * incr;
                    genomePositions[posIndex] = genomePosition;
                    posIndex++;
                    if (posIndex >= featurePositions.length) {
                        return genomePositions;
                    }
                }
                //No more positions of interest on this exon
                //move up counter to end of exon
                all_exon_counter = current_exon_end;
            }
        }

        return genomePositions;
    }

}
