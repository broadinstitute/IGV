package org.broad.igv.ucsc.bb.codecs;

import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.ucsc.bb.BedData;

public interface BBCodec {

    IGVFeature decode(BedData feat);
}
