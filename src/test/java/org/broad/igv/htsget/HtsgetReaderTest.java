package org.broad.igv.htsget;

import org.broad.igv.feature.genome.Genome;
import org.junit.Test;

import static org.junit.Assert.*;

public class HtsgetReaderTest {

    @Test
    public void testReadHeader() throws Exception {

        String url = "https://htsget.ga4gh.org/variants/giab.NA12878";

        HtsgetReader reader = HtsgetReader.getReader(url);

        String ticket = reader.readHeader();

        System.out.println(ticket);

        assertNotNull(ticket);
    }

    @Test
    public void testReadData() throws Exception {
//https://htsget.ga4gh.org/variants/1000genomes.phase1.chr8?format=VCF&referenceName=8&start=128732400&end=128770475

        String url = "https://htsget.ga4gh.org/variants/giab.NA12878";
        String chr = "8";
        int start = 128732400 - 1;
        int end = 128770475;

        HtsgetReader reader = HtsgetReader.getReader(url);

        byte[] data = reader.readData(chr, start, end);
        assertNotNull(data);
        System.out.println(new String(data));


    }

}