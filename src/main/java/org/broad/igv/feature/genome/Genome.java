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


/*
 * Genome.java
 *
 * Created on November 9, 2007, 9:05 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package org.broad.igv.feature.genome;

import org.apache.commons.math3.stat.StatUtils;
import org.broad.igv.Globals;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.Cytoband;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.Track;
import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.liftover.Liftover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Simple model of a genome.  Keeps an ordered list of Chromosomes, an alias table, and genome position offsets
 * for each chromosome to support a whole-genome view.
 */
public class Genome {

    private static Logger log = LogManager.getLogger(Genome.class);
    public static final int MAX_WHOLE_GENOME_LONG = 100;

    private static Object aliasLock = new Object();

    private String id;
    private String displayName;
    private List<String> chromosomeNames;
    private List<String> longChromosomeNames;
    private LinkedHashMap<String, Chromosome> chromosomeMap;
    private long totalLength = -1;
    private long nominalLength = -1;
    private Map<String, Long> cumulativeOffsets = new HashMap();
    private Map<String, String> chrAliasTable;
    private Sequence sequence;
    private FeatureTrack geneTrack;
    private String species;
    private String ucscID;
    private String blatDB;
    private List<ResourceLocator> annotationResources;
    private Map<ResourceLocator, List<Track>> annotationTracks;
    private boolean showWholeGenomeView = true;

    private Map<String, Liftover> liftoverMap;

    /**
     * @param id
     * @param displayName
     * @param sequence       the reference Sequence object.  Can be null.
     * @param chromosOrdered Whether the chromosomes are already ordered. If false, they will be sorted.
     */
    public Genome(String id, String displayName, Sequence sequence, boolean chromosOrdered) {

        this.id = id;
        this.displayName = displayName;
        this.chrAliasTable = new HashMap<>();
        this.sequence = (sequence instanceof InMemorySequence) ? sequence : new SequenceWrapper(sequence);
        this.chromosomeNames = sequence.getChromosomeNames();
        this.ucscID = ucsdIDMap.containsKey(id) ? ucsdIDMap.get(id) : id;
        this.chromosomeMap = new LinkedHashMap<>();

        int maxLength = -1;
        for (int i = 0; i < chromosomeNames.size(); i++) {
            String chr = chromosomeNames.get(i);
            int length = sequence.getChromosomeLength(chr);
            maxLength = length > maxLength ? length : maxLength;
            chromosomeMap.put(chr, new Chromosome(i, chr, length));
        }

        if (!chromosOrdered) {
            List<Chromosome> chromosomeList = new ArrayList<>(chromosomeMap.values());
            Collections.sort(chromosomeList, new ChromosomeComparator(maxLength / 10));
            for (int ii = 0; ii < chromosomeList.size(); ii++) {
                Chromosome chrom = chromosomeList.get(ii);
                chrom.setIndex(ii);
                chromosomeNames.set(ii, chrom.getName());
            }
        }

        initializeChromosomeAliases();
    }


    /**
     * Alternate constructor for defining a minimal genome, usually from parsing a chrom.sizes file.
     *
     * @param id
     * @param chromosomes
     */
    public Genome(String id, List<Chromosome> chromosomes) {
        this.id = id;
        this.displayName = id;
        this.chrAliasTable = new HashMap<String, String>();
        this.sequence = null;

        chromosomeNames = new ArrayList<String>(chromosomes.size());
        chromosomeMap = new LinkedHashMap<String, Chromosome>(chromosomes.size());
        for (Chromosome chromosome : chromosomes) {
            chromosomeNames.add(chromosome.getName());
            chromosomeMap.put(chromosome.getName(), chromosome);
        }
        initializeChromosomeAliases();

    }


    public String getCanonicalChrName(String str) {
        if (str == null) {
            return str;
        } else if (chrAliasTable.containsKey(str)) {
            return chrAliasTable.get(str);
        } else {
            return str;
        }
    }

    public boolean isKnownChr(String str) {
        return chrAliasTable.containsKey(str);
    }

    /**
     * Populate the chr alias table.  The input is a collection of chromosome synonym lists.  The
     * directionality is determined by the "true" chromosome names.
     *
     * @param synonymsList
     */
    public void addChrAliases(Collection<Collection<String>> synonymsList) {

        if (synonymsList == null) return;
        if (chrAliasTable == null) chrAliasTable = new HashMap<>();

        // Convert names to a set for fast "contains" testing.
        Set<String> chrNameSet = new HashSet<String>(chromosomeNames);

        for (Collection<String> synonyms : synonymsList) {

            // Find the chromosome name as used in this genome
            String chr = null;
            for (String syn : synonyms) {
                if (chrNameSet.contains(syn)) {
                    chr = syn;
                    break;
                }
            }

            // If found register aliases
            if (chr != null) {
                for (String syn : synonyms) {
                    chrAliasTable.put(syn, chr);
                }
            } else {
                // Nothing to do.  SHould this be logged?
            }
        }
    }


    /**
     * Update the chromosome alias table with common variations.  Also, add own names.
     */
    void initializeChromosomeAliases() {
        chrAliasTable.putAll(getAutoAliases());
    }


    Map<String, String> getAutoAliases() {

        Map<String, String> autoAliases = new HashMap<String, String>();

        for (String name : chromosomeNames) {
            autoAliases.put(name, name);
        }

        for (String name : chromosomeNames) {
            if (name.startsWith("gi|")) {
                // NCBI
                String alias = getNCBIName(name);
                autoAliases.put(alias, name);

                // Also strip version number out, if present
                int dotIndex = alias.lastIndexOf('.');
                if (dotIndex > 0) {
                    alias = alias.substring(0, dotIndex);
                    autoAliases.put(alias, name);
                }
            }
        }


        // Auto insert UCSC conventions for first 50
        int count = 0;
        for (String name : chromosomeNames) {
            // UCSC Conventions
            if (name.toLowerCase().startsWith("chr")) {
                autoAliases.put(name.substring(3), name);
            } else {
                autoAliases.put("chr" + name, name);
            }
            if (count++ == 50) break;
        }

        // Special case for human and mouse -- for other genomes define these in the alias file.
        if (id.startsWith("hg") || id.equalsIgnoreCase("1kg_ref")) {
            autoAliases.put("23", "chrX");
            autoAliases.put("24", "chrY");
            autoAliases.put("MT", "chrM");
        } else if (id.startsWith("mm") || id.startsWith("rheMac")) {
            autoAliases.put("21", "chrX");
            autoAliases.put("22", "chrY");
            autoAliases.put("MT", "chrM");
        } else if (id.equals("b37")) {
            autoAliases.put("chrM", "MT");
            autoAliases.put("chrX", "23");
            autoAliases.put("chrY", "24");
        }

        Collection<Map.Entry<String, String>> aliasEntries = new ArrayList(autoAliases.entrySet());
        for (Map.Entry<String, String> aliasEntry : aliasEntries) {
            // Illumina conventions
            String alias = aliasEntry.getKey();
            String chr = aliasEntry.getValue();
            if (!alias.endsWith(".fa")) {
                String illuminaName = alias + ".fa";
                autoAliases.put(illuminaName, chr);
            }
            if (!chr.endsWith(".fa")) {
                String illuminaName = chr + ".fa";
                autoAliases.put(illuminaName, chr);
            }
        }

        return autoAliases;
    }

    /**
     * Extract the user friendly name from an NCBI accession
     * example: gi|125745044|ref|NC_002229.3|  =>  NC_002229.3
     */
    public static String getNCBIName(String name) {

        String[] tokens = name.split("\\|");
        return tokens[tokens.length - 1];
    }


    /**
     * Return the chromosome name associated with the "home" button,  usually the whole genome chromosome.
     *
     * @return
     */
    public String getHomeChromosome() {
        if (showWholeGenomeView == false || chromosomeNames.size() == 1 || getLongChromosomeNames().size() > MAX_WHOLE_GENOME_LONG) {
            return chromosomeNames.get(0);
        } else {
            return Globals.CHR_ALL;
        }
    }


    public Chromosome getChromosome(String chrName) {
        return chromosomeMap.get(getCanonicalChrName(chrName));
    }


    public List<String> getAllChromosomeNames() {
        return chromosomeNames;
    }


    public Collection<Chromosome> getChromosomes() {
        return chromosomeMap.values();
    }


    public long getTotalLength() {
        if (totalLength < 0) {
            totalLength = 0;
            for (Chromosome chr : chromosomeMap.values()) {
                totalLength += chr.getLength();
            }
        }
        return totalLength;
    }


    public long getCumulativeOffset(String chr) {

        Long cumOffset = cumulativeOffsets.get(chr);
        if (cumOffset == null) {
            long offset = 0;
            for (String c : getLongChromosomeNames()) {
                if (chr.equals(c)) {
                    break;
                }
                offset += getChromosome(c).getLength();
            }
            cumOffset = offset;
            cumulativeOffsets.put(chr, cumOffset);
        }
        return cumOffset;
    }

    /**
     * Covert the chromosome coordinate in basepairs to genome coordinates in kilo-basepairs
     *
     * @param chr
     * @param locationBP
     * @return The overall genome coordinate, in kilo-bp
     */
    public int getGenomeCoordinate(String chr, int locationBP) {
        return (int) ((getCumulativeOffset(chr) + locationBP) / 1000);
    }

    /**
     * Translate a genome coordinate, in kilo-basepairs, to a chromosome & position in basepairs.
     *
     * @param genomeKBP The "genome coordinate" in kilo-basepairs.  This is the distance in kbp from the start of the
     *                  first chromosome.
     * @return the position on the corresponding chromosome
     */
    public ChromosomeCoordinate getChromosomeCoordinate(int genomeKBP) {

        long cumOffset = 0;
        List<String> wgChrNames = getLongChromosomeNames();
        for (String c : wgChrNames) {
            int chrLen = getChromosome(c).getLength();
            if ((cumOffset + chrLen) / 1000 > genomeKBP) {
                int bp = (int) (genomeKBP * 1000 - cumOffset);
                return new ChromosomeCoordinate(c, bp);
            }
            cumOffset += chrLen;
        }


        String c = wgChrNames.get(wgChrNames.size() - 1);
        int bp = (int) (genomeKBP - cumOffset) * 1000;
        return new ChromosomeCoordinate(c, bp);
    }


    public String getId() {
        return id;
    }

    public String getBlatDB() {
        return blatDB;
    }

    public void setBlatDB(String blatDB) {
        this.blatDB = blatDB;
    }

    public void setUcscID(String ucscID) {
        this.ucscID = ucscID;
    }

    public String getUCSCId() {
        return ucscID == null ? id : ucscID;
    }

    public String getSpecies() {
        if (species == null) {
            species = Genome.getSpeciesForID(getUCSCId());
        }
        return species;
    }

    public String getNextChrName(String chr) {
        List<String> chrList = getAllChromosomeNames();
        for (int i = 0; i < chrList.size() - 1; i++) {
            if (chrList.get(i).equals(chr)) {
                return chrList.get(i + 1);
            }
        }
        return null;
    }

    public String getPrevChrName(String chr) {
        List<String> chrList = getAllChromosomeNames();
        for (int i = chrList.size() - 1; i > 0; i--) {
            if (chrList.get(i).equals(chr)) {
                return chrList.get(i - 1);
            }
        }
        return null;
    }

    /**
     * Return the nucleotide sequence on the + strand for the genomic interval.  This method can return null
     * if sequence is not available.
     *
     * @param chr
     * @param start start position in "zero-based" coordinates
     * @param end   end position
     * @return sequence, or null if not available
     * @api
     */

    public byte[] getSequence(String chr, int start, int end) {
        return getSequence(getCanonicalChrName(chr), start, end, true);
    }

    public byte[] getSequence(String chr, int start, int end, boolean useCache) {


        if (sequence == null) {
            return null;
        }

        Chromosome c = getChromosome(chr);
        if (c == null) {
            return null;
        }
        end = Math.min(end, c.getLength());
        if (end <= start) {
            return null;
        }
        return sequence.getSequence(chr, start, end, useCache);
    }

    public boolean sequenceIsRemote() {
        return sequence.isRemote();
    }

    public boolean sequenceIsFasta() {
        return sequence.isFasta();
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Return the reference base at the given position.  Can return null if reference sequence is unknown
     *
     * @param chr
     * @param pos
     * @return the reference base, or null if unknown
     */
    public byte getReference(String chr, int pos) {
        return sequence == null ? null : sequence.getBase(chr, pos);
    }


    public void setCytobands(LinkedHashMap<String, List<Cytoband>> chrCytoMap) {

        for (Map.Entry<String, List<Cytoband>> entry : chrCytoMap.entrySet()) {
            String chr = entry.getKey();
            List<Cytoband> cytobands = entry.getValue();

            Chromosome chromosome = chromosomeMap.get(chr);
            if (chromosome != null) {
                chromosome.setCytobands(cytobands);
            }
        }

    }

    public void setGeneTrack(FeatureTrack geneFeatureTrack) {
        this.geneTrack = geneFeatureTrack;
    }

    /**
     * Return the annotation track associated with this genome.  This is a legacy ".genome" artifact, can return null.
     *
     * @return a FeatureTrack, or null
     */
    public FeatureTrack getGeneTrack() {
        return geneTrack;
    }

    /**
     * Return names of "long" chromosomes relative to a fraction of the median length.  The intent here is to
     * remove small contigs in an otherwise well assembled genome to support a "whole genome" view.  We first sort
     * chromosomes in length order, then look for the first large break in size.
     *
     * @return
     */
    public List<String> getLongChromosomeNames() {

        if (longChromosomeNames == null) {
            longChromosomeNames = new ArrayList<>();
            if (chromosomeMap.size() < 100) {
                // Keep all chromosomes within 10% of the mean in length
                double[] lengths = new double[chromosomeMap.size()];
                int idx = 0;
                for (Chromosome c : chromosomeMap.values()) {
                    lengths[idx++] = c.getLength();
                }
                double mean = StatUtils.mean(lengths);
                double std = Math.sqrt(StatUtils.variance(lengths));
                double min = 0.1 * mean;
                for (String chr : getAllChromosomeNames()) {
                    if (chromosomeMap.get(chr).getLength() > min) {
                        longChromosomeNames.add(chr);
                    }
                }
            } else {
                // Long list, likely many small contigs.  Search for a break between long (presumably assembled)
                // chromosomes and small contigs.
                List<Chromosome> allChromosomes = new ArrayList<>(chromosomeMap.values());
                allChromosomes.sort((c1, c2) -> c2.getLength() - c1.getLength());

                Chromosome lastChromosome = null;
                Set<String> tmp = new HashSet<>();
                for (Chromosome c : allChromosomes) {
                    if (lastChromosome != null) {
                        double delta = lastChromosome.getLength() - c.getLength();
                        if (delta / lastChromosome.getLength() > 0.7) {
                            break;
                        }
                    }
                    tmp.add(c.getName());
                    lastChromosome = c;
                }


                for (String chr : getAllChromosomeNames()) {
                    if (tmp.contains(chr)) {
                        longChromosomeNames.add(chr);
                    }
                }
            }
        }
        return longChromosomeNames;

    }

    public long getNominalLength() {

        if (nominalLength < 0) {
            nominalLength = 0;
            for (String chrName : getLongChromosomeNames()) {
                Chromosome chr = getChromosome(chrName);
                nominalLength += chr.getLength();
            }
        }
        return nominalLength;
    }


    // TODO A hack (obviously),  we need to record a species in the genome definitions to support old style
    // blat servers.
    private static Map<String, String> ucscSpeciesMap;

    private static synchronized String getSpeciesForID(String id) {
        if (ucscSpeciesMap == null) {
            ucscSpeciesMap = new HashMap<>();

            InputStream is = null;

            try {
                is = Genome.class.getResourceAsStream("speciesMapping.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(is));

                String nextLine;
                while ((nextLine = br.readLine()) != null) {
                    if (nextLine.startsWith("#")) continue;
                    String[] tokens = Globals.tabPattern.split(nextLine);
                    if (tokens.length == 2) {
                        ucscSpeciesMap.put(tokens[0].trim(), tokens[1].trim());
                    } else {
                        log.error("Unexpected number of tokens in species mapping file for line: " + nextLine);
                    }
                }
            } catch (IOException e) {
                log.error("Error reading species mapping table", e);
            } finally {
                if (is != null) try {
                    is.close();
                } catch (IOException e) {
                    log.error("", e);
                }
            }

        }

        for (Map.Entry<String, String> entry : ucscSpeciesMap.entrySet()) {
            if (id.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // Map some common IGV genome IDs to UCSC equivalents.  Primarily for BLAT usage
    private static Map<String, String> ucsdIDMap;

    static {
        ucsdIDMap = new HashMap<>();
        ucsdIDMap.put("1kg_ref", "hg18");
        ucsdIDMap.put("1kg_v37", "hg19");
        ucsdIDMap.put("b37", "hg19");
    }


    public boolean sequenceIsLoaded(ReferenceFrame frame) {
        return sequence.isLoaded(frame);
    }

    public void setAnnotationResources(List<ResourceLocator> annotationResources) {
        this.annotationResources = annotationResources;
    }

    public List<ResourceLocator> getAnnotationResources() {
        return annotationResources;
    }

    public Map<ResourceLocator, List<Track>> getAnnotationTracks() {
        return annotationTracks;
    }

    public void setAnnotationTracks(Map<ResourceLocator, List<Track>> annotationTracks) {
        this.annotationTracks = annotationTracks;
    }

    /**
     * Mock genome for unit tests
     */

    private Genome(String id) {
        this.id = id;
    }

    ;

    public static Genome mockGenome = new Genome("hg19");

    public void setShowWholeGenomeView(boolean showWholeGenomeView) {
        this.showWholeGenomeView = showWholeGenomeView;
    }

    public boolean getShowWholeGenomeView() {
        return showWholeGenomeView;
    }

    public void setLongChromosomeNames(List<String> chrNames) {
        this.longChromosomeNames = chrNames;
    }

    public Map<String, Liftover> getLiftoverMap() {
        return liftoverMap;
    }

    public void setLiftoverMap(Map<String, Liftover> liftoverMap) {
        this.liftoverMap = liftoverMap;
    }
}
