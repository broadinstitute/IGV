package org.broad.igv.bedpe;

import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.IGVFeature;

import java.awt.*;
import java.util.Map;

/**
 * Created by jrobinso on 6/29/18.
 */
public class BedPEFeature implements BedPE  {

    protected String chr1;
    protected int start1;
    protected int end1;

    protected String chr2;
    protected int start2;
    protected int end2;
    protected String name;
    protected String scoreString = "";
    protected float score;
    protected Color color;
    protected int thickness = 1;
    protected String type;
    Map<String, String> attributes;
    BedPEShape shape;
    private boolean isComplement = false;

    public BedPEFeature() {
    }

    public BedPEFeature(String chr1, int start1, int end1, String chr2, int start2, int end2) {
        this.chr1 = chr1;
        this.start1 = start1;
        this.chr2 = chr2;
        this.end1 = end1;
        this.start2 = start2;
        this.end2 = end2;
    }

    public BedPEFeature getComplement() {
        BedPEFeature complement = new BedPEFeature(chr2, start2, end2, chr1, start1, end1);
        complement.name = name;
        complement.scoreString = scoreString;
        complement.score = score;
        complement.color = color;
        complement.thickness = thickness;
        complement.type = type;
        complement.attributes = attributes;
        complement.isComplement = true;
        return complement;
    }

    public String getChr() {
        if(isSameChr()) {
            return chr1;
        } else {
            return chr1 + " " + chr2;
        }
    }

    public int getStart() {
        return Math.min(start1, start2);
    }

    public int getEnd() {
        return Math.max(end1, end2);
    }

    public double getMidStart() {
        return Math.min ((start1 + end1) / 2.0, (start2 + end2) / 2.0);
    }

    public double getMidEnd() {
        return Math.max ((start1 + end1) / 2.0, (start2 + end2) / 2.0);
    }

    @Override
    public boolean isComplement() {
        return this.isComplement;
    }

    @Override
    public float getScore() {
        return score;
    }

    public boolean isSameChr() {
        return chr1.equals(chr2);
    }
    @Override
    public Color getColor() {
        return color;
    }

    @Override
    public int getThickness() {
        return thickness;
    }

    public String getContig() {
        return getChr();
    }

    @Override
    public BedPEShape getShape() {
        return shape;
    }

    @Override
    public void setShape(BedPEShape shape) {
        this.shape = shape;
    }

    public String getValueString() {

        StringBuffer buf = new StringBuffer();

        String locus1 = chr1 + ":" + start1 + "-" + end1;
        String locus2 = chr2 + ":" + start2 + "-" + end2;
        if(name != null && name.length() > 0 && !name.equals(".")) {
            buf.append(name + "<br>");
        }
        buf.append(locus1);
        buf.append("<br>" + locus2);
        buf.append("<br>Score: " + score);
        if(attributes != null) {
            buf.append("<br><hr>");
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                buf.append("<br>" + entry.getKey() + ": " + entry.getValue());
            }
        }

        return buf.toString();
    }

    public String getChr1() {
        return chr1;
    }

    public int getStart1() {
        return start1;
    }

    public int getEnd1() {
        return end1;
    }

    public String getChr2() {
        return chr2;
    }

    public int getStart2() {
        return start2;
    }

    public int getEnd2() {
        return end2;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    @Override
    public String getName() {
        return name;
    }
}
