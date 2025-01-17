package org.broad.igv.bedpe;

import org.broad.igv.feature.IGVFeature;

import java.awt.*;

public interface BedPE extends IGVFeature {

    default String getName() {
        return null;
    }

    String getChr1();

    int getStart1();

    int getEnd1();

    String getChr2();

    int getStart2();

    int getEnd2();

    boolean isSameChr();

    Color getColor();

    int getThickness();

    void setShape(BedPEShape s);

    BedPEShape getShape();

    String getValueString();

    default double getMidStart() {
        return Math.min ((getStart1() + getEnd1()) / 2.0, (getStart2() + getEnd2()) / 2.0);
    }

    default double getMidEnd() {
        return Math.max ((getStart1() + getEnd1()) / 2.0, (getStart2() + getEnd2()) / 2.0);
    }

    default double getCenterDistance() {
        return Math.abs((getStart1() + getEnd1()) / 2.0 - (getStart2() + getEnd2())  / 2.0);
    }

    /**
     * @return true if this feature is a shadow complement of an inter-chr feature.
     */
    default boolean isComplement() {
        return false;
    }
}
