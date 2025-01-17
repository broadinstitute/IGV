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

package org.broad.igv.feature.tribble.reader;

import htsjdk.tribble.Feature;
import org.broad.igv.Globals;
import org.broad.igv.bedpe.BedPEFeature;
import org.broad.igv.bedpe.InteractFeature;
import org.broad.igv.feature.*;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.tribble.UCSCCodec;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.color.ColorUtilities;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Dec 20, 2009
 * Time: 10:15:49 PM
 */
public class InteractCodec extends UCSCCodec<InteractFeature> {

    private Genome genome;

    public InteractCodec(Genome genome, FeatureType featureType) {
        super(BasicFeature.class, featureType);
        this.genome = genome;
    }


    //@Override
    public InteractFeature decode(String[] tokens) {
        return InteractFeature.fromTokens(tokens, genome);
    }

    public boolean canDecode(String path) {
        return true;
    }
}


