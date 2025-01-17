package org.broad.igv.bedpe;

import org.broad.igv.feature.genome.Genome;
import org.broad.igv.ui.color.ColorUtilities;

import java.awt.*;

/**
 * 0   string chrom;        "Chromosome (or contig, scaffold, etc.). For interchromosomal, use 2 records"
 * 1   uint chromStart;     "Start position of lower region. For interchromosomal, set to chromStart of this region"
 * 2   uint chromEnd;       "End position of upper region. For interchromosomal, set to chromEnd of this region"
 * 3   string name;         "Name of item, for display.  Usually 'sourceName/targetName/exp' or empty"
 * 4   uint score;          "Score (0-1000)"
 * 5   double value;        "Strength of interaction or other data value. Typically basis for score"
 * 6   string exp;          "Experiment name (metadata for filtering). Use . if not applicable"
 * 7   string color;        "Item color.  Specified as r,g,b or hexadecimal #RRGGBB or html color name, as in //www.w3.org/TR/css3-color/#html4. Use 0 and spectrum setting to shade by score"
 * 8   string sourceChrom;  "Chromosome of source region (directional) or lower region. For non-directional interchromosomal, chrom of this region."
 * 9   uint sourceStart;    "Start position in chromosome of source/lower/this region"
 * 10  uint sourceEnd;      "End position in chromosome of source/lower/this region"
 * 11  string sourceName;   "Identifier of source/lower/this region"
 * 12  string sourceStrand; "Orientation of source/lower/this region: + or -.  Use . if not applicable"
 * 13  string targetChrom;  "Chromosome of target region (directional) or upper region. For non-directional interchromosomal, chrom of other region"
 * 14  uint targetStart;    "Start position in chromosome of target/upper/this region"
 * 15  uint targetEnd;      "End position in chromosome of target/upper/this region"
 * 16  string targetName;   "Identifier of target/upper/this region"
 * 17  string targetStrand; "Orientation of target/upper/this region: + or -.  Use . if not applicable"
 */
public class InteractFeature extends BedPEFeature {

    String chr;
    int start;
    int end;
    private double value;

    public InteractFeature() {

    }

    public static InteractFeature fromTokens(String[] tokens, Genome genome) {

        if (tokens.length < 12) {
            return null;
        }

        InteractFeature feature = new InteractFeature();

        feature.chr = genome == null ? tokens[0] : genome.getCanonicalChrName(tokens[0]);

        feature.start = Integer.parseInt(tokens[1]);
        feature.end = Integer.parseInt(tokens[2]);
        feature.name = tokens[3];
        feature.score = Float.parseFloat(tokens[4]);
        feature.value = Double.parseDouble(tokens[5]);
        feature.color = ColorUtilities.stringToColor(tokens[7]);

        feature.chr1 =  genome == null ? tokens[8] : genome.getCanonicalChrName(tokens[8]);
        feature.start1 = Integer.parseInt(tokens[9]);
        feature.end1 = Integer.parseInt(tokens[10]);

        feature.chr2 =  genome == null ? tokens[13] : genome.getCanonicalChrName(tokens[13]);
        feature.start2 = Integer.parseInt(tokens[14]);
        feature.end2 = Integer.parseInt(tokens[15]);

        return feature;
    }

    @Override
    public String getChr() {
        return chr;
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public int getEnd() {
        return end;
    }
}
