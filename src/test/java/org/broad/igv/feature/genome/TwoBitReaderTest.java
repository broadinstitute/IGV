package org.broad.igv.feature.genome;

import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class TwoBitReaderTest {


    @Test
    public void readSequenceLocal() throws IOException {

        String expectedSequence = "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNACTCTATCTATCTATCTATCTATCTTTTT" +
                "CCCCCCGGGGGGagagagagactc tagcatcctcctacctcacNNacCNctTGGACNCcCaGGGatttcN" +
                "NNcccNNCCNCgN";

        String testFile = TestUtils.DATA_DIR + "twobit/foo.2bit";
        TwoBitReader reader = new TwoBitReader(testFile);

        int start = 5;
        int end = 100;
        String seqString = new String(reader.readSequence("chr1", start, end));
        assertEquals(expectedSequence.substring(start, end), seqString);
    }

    @Test
    public void readSequenceRemote() throws IOException {


        String url = "https://hgdownload.soe.ucsc.edu/goldenPath/hg38/bigZips/hg38.2bit";
        TwoBitReader reader = new TwoBitReader(url);

        // Non-masked no "N" region  chr1:11,830-11,869
        String expectedSeq = "GATTGCCAGCACCGGGTATCATTCACCATTTTTCTTTTCG";
        byte[] seqbytes = reader.readSequence("chr1", 11829, 11869);
        String seq = new String(seqbytes);
        assertEquals(expectedSeq, seq);

        // "N" region  chr1:86-124
        expectedSeq = "NNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN";
        seqbytes = reader.readSequence("chr1", 85, 124);
        seq = new String(seqbytes);
        assertEquals(expectedSeq, seq);

        // partially masked region chr1:120,565,295-120,565,335
        expectedSeq = "TATGAACTTTTGTTCGTTGGTgctcagtcctagaccctttt";
        seqbytes = reader.readSequence("chr1", 120565294, 120565335);
        seq = new String(seqbytes);
        assertEquals(expectedSeq, seq);
        
    }

}