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

import org.broad.igv.feature.genome.ChromAlias;

import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Interface for features in IGV annotation tracks  (FeatureTrack and derived classes).
 */

public interface IGVFeature extends LocusScore, IGVNamedFeature {

    default String getIdentifier() {
        return null;
    }

    default Strand getStrand() {
        return Strand.NONE;
    }

    default int getLength() {
        return getEnd() - getStart();
    }

    default List<String> getAttributeKeys() {
        return Collections.EMPTY_LIST;
    }

    default String getAttribute(String key) {
        return null;
    }

    default void removeAttribute(String key) {}

    /**
     * Return true if the given feature is completely contained within the bounds of this
     * feature. amd is on the same strand.
     * <p/>
     *
     * @param feature
     * @return
     */
    default boolean contains(IGVFeature feature) {
        if (feature == null) {
            return false;
        }
        if (!this.getChr().equals(feature.getChr()) ||
                this.getStrand() != feature.getStrand()) {
            return false;
        }
        if ((feature.getStart() >= this.getStart()) && (feature.getEnd() <= this.getEnd())) {
            return true;
        } else {
            return false;
        }
    }

    default boolean contains(double location) {
        return location >= getStart() && location < getEnd();
    }

    default List<Exon> getExons() {
        return null;
    }

    public Color getColor();

    default String getURL() {
        return null;
    }

   default Map<String, String> getAttributes() {
        return Collections.EMPTY_MAP;
   }
}
