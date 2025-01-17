package org.broad.igv.util;

import org.broad.igv.bedpe.BedPE;
import org.broad.igv.bedpe.BedPEFeature;
import org.broad.igv.bedpe.BedPEParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FeatureCacheTest {

    @Test
    public void testGetFeatures() throws Exception {

        String p = TestUtils.DATA_DIR + "bedpe/interleaved_chrs.bedpe";

        List<BedPE> features = BedPEParser.parse(new ResourceLocator(p), null).features;

        //chr5:135,300,000-135,399,999
        String chr = "chr5";
        int start = 135300000 - 1;
        int end = 135399999;

        // Manually count overlaps
        int count = 0;
        for (BedPE f : features) {
            if (chr.equals(f.getChr()) && f.getEnd() >= start && f.getStart() <= end) count++;
        }

        FeatureCache<BedPE> cache = new FeatureCache<>(features);
        List<BedPE> subset = cache.getFeatures(chr, start, end);
        assertEquals(count, subset.size());
    }

}