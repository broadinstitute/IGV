package org.broad.igv.bedpe;

import org.broad.igv.feature.genome.Genome;

import java.awt.*;

class WGFeature implements BedPE {

    private final int start1;
    private final int end1;
    private final int start2;
    private final int end2;
    private final BedPE feature;


    WGFeature(BedPE f, Genome genome) {
        this.feature = f;
        this.start1 = genome.getGenomeCoordinate(f.getChr1(), f.getStart1());
        this.end1 = genome.getGenomeCoordinate(f.getChr1(), f.getEnd1());
        this.start2 = genome.getGenomeCoordinate(f.getChr2(), f.getStart2());
        this.end2 = genome.getGenomeCoordinate(f.getChr2(), f.getEnd2());

    }

    @Override
    public String getChr1() {
        return "All";
    }

    @Override
    public int getStart1() {
        return start1;
    }

    @Override
    public int getEnd1() {
        return end1;
    }

    @Override
    public String getChr2() {
        return "All";
    }

    @Override
    public int getStart2() {
        return start2;
    }

    @Override
    public int getEnd2() {
        return end2;
    }

    @Override
    public boolean isSameChr() {
        return true;
    }

    @Override
    public Color getColor() {
        return feature.getColor();
    }

    @Override
    public int getThickness() {
        return feature.getThickness();
    }

    @Override
    public void setShape(BedPEShape s) {
        feature.setShape(s);
    }

    @Override
    public BedPEShape getShape() {
        return feature.getShape();
    }

    @Override
    public String getValueString() {
        return feature.getValueString();
    }

    @Override
    public float getScore() {
        return feature.getScore();
    }

    @Override
    public String getContig() {
        return feature.getContig();
    }

    @Override
    public int getStart() {
        return Math.min(start1, start2);
    }

    @Override
    public int getEnd() {
        return Math.max(end1, end2);
    }
}
