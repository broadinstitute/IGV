package org.broad.igv.ucsc.bb;

import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class BBFileTest {

    /**
     * Test a BW file with an unusual layout (chromTree after full data).
     */
    @Test
    public void testChromTree() throws IOException {
        String bbFile = "https://data.broadinstitute.org/igvdata/test/data/bb/chromTreeTest.bigwig";
        BBFile reader = new BBFile(bbFile, null);
        reader.readHeader();
        String [] chrNames = reader.getChromosomeNames();
        assertEquals(6, chrNames.length);
    }

    /**
     * Test a BW file with an typical layout (chromTree before full data).
     */
    @Test
    public void testChromTree2() throws IOException {

        String bbFile = TestUtils.DATA_DIR + "bb/GCF_000009045.1_ASM904v1.ncbiGene.bb";
        BBFile reader = new BBFile(bbFile, null);
        BBHeader header = reader.readHeader();
        assertNotNull(header);
        String [] chrNames = reader.getChromosomeNames();
        assertEquals(1, chrNames.length);
    }

    @Test
    public void testAutoSQL() throws IOException {

        String bbFile = TestUtils.DATA_DIR + "bb/GCF_000009045.1_ASM904v1.ncbiGene.bb";
        BBFile reader = new BBFile(bbFile, null);
        reader.readHeader();

        String autoSql = "table bigGenePred\n" +
                "\"bigGenePred gene models\"\n" +
                "   (\n" +
                "   string chrom;       \"Reference sequence chromosome or scaffold\"\n" +
                "   uint   chromStart;  \"Start position in chromosome\"\n" +
                "   uint   chromEnd;    \"End position in chromosome\"\n" +
                "   string name;        \"Name or ID of item, ideally both human readable and unique\"\n" +
                "   uint score;         \"Score (0-1000)\"\n" +
                "   char[1] strand;     \"+ or - for strand\"\n" +
                "   uint thickStart;    \"Start of where display should be thick (start codon)\"\n" +
                "   uint thickEnd;      \"End of where display should be thick (stop codon)\"\n" +
                "   uint reserved;       \"RGB value (use R,G,B string in input file)\"\n" +
                "   int blockCount;     \"Number of blocks\"\n" +
                "   int[blockCount] blockSizes; \"Comma separated list of block sizes\"\n" +
                "   int[blockCount] chromStarts; \"Start positions relative to chromStart\"\n" +
                "   string name2;       \"Alternative/human readable name\"\n" +
                "   string cdsStartStat; \"Status of CDS start annotation (none, unknown, incomplete, or complete)\"\n" +
                "   string cdsEndStat;   \"Status of CDS end annotation (none, unknown, incomplete, or complete)\"\n" +
                "   int[blockCount] exonFrames; \"Exon frame {0,1,2}, or -1 if no frame for exon\"\n" +
                "   string type;        \"Transcript type\"\n" +
                "   string geneName;    \"Primary identifier for gene\"\n" +
                "   string geneName2;   \"Alternative/human readable gene name\"\n" +
                "   string geneType;    \"Gene type\"\n" +
                "   )\n" +
                "\n";

        assertEquals(autoSql.trim(), reader.getAutoSQL().trim());
    }


    @Test
    public void testExtraIndexSearch() throws IOException {

        String path = TestUtils.DATA_DIR + "genomes/GCF_000002655.1.chromAlias.bb";
        BBFile bbReader = new BBFile(path, null);

        // There are 5 extra indexes, 1 for each alias
        String ncbiName = "3";
        IGVFeature f1 = bbReader.search(ncbiName);
        assertNotNull(f1);
        assertEquals(ncbiName, f1.getAttribute("ncbi"));

        String ucscName = "chr2";
        IGVFeature f2 =  bbReader.search(ucscName);
        assertEquals(ucscName, f2.getAttribute("ucsc"));

        assertNull(bbReader.search("zzzz"));
    }

    @Test
    public void testExtraIndexTrixSearch() throws IOException {

        String path = TestUtils.DATA_DIR + "bb/GCF_000009045.1_ASM904v1.ncbiGene.bb";
        String trixPath = TestUtils.DATA_DIR + "bb/ixIxx/GCF_000009045.1_ASM904v1.ncbiGene.ix";
        BBFile bbReader = new BBFile(path, null, trixPath);

        // Search by name, which is the index parameter, does not require trix
        String name = "NP_389226.1";
        IGVFeature f =  bbReader.search(name);
        assertEquals(name, f.getName());


        // Search by alternate name,  does require trix
        String name2 = "ykoX";
        IGVFeature f2 =  bbReader.search(name2);
        assertEquals(name, f2.getName());

        assertNull(bbReader.search("zzzz"));
    }

    /**
     * Test a BW file with an unusual layout (chromTree after full data).
     */
    @Test
    public void testBigInteract() throws IOException {
        String bbFile = TestUtils.DATA_DIR + "bb/interactExample3.inter.bb";
        BBFile reader = new BBFile(bbFile, null);
        reader.readHeader();
        String [] chrNames = reader.getChromosomeNames();
        assertEquals(6, chrNames.length);
    }


}