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

package org.broad.igv.data;

import org.apache.commons.math3.stat.StatUtils;
import org.broad.igv.logging.*;
import org.broad.igv.Globals;
import org.broad.igv.exceptions.ParserException;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.track.TrackProperties;
import org.broad.igv.track.TrackType;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.ResourceLocator;
import org.broad.igv.util.collections.DownsampledDoubleArrayList;
import org.broad.igv.util.collections.FloatArrayList;
import org.broad.igv.util.collections.IntArrayList;
import htsjdk.tribble.readers.AsciiLineReader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parser for wiggle and "wiggle-like" formats.
 */
public class WiggleParser {

    private static Logger log = LogManager.getLogger(WiggleParser.class);
    protected int chrColumn = 0;
    protected int startColumn = 1;
    protected int endColumn = 2;
    protected int dataColumn = 3;

    protected enum Type {
        FIXED, VARIABLE, BED_GRAPH, CPG, EXPR
    }

    protected Genome genome;

    /**
     * Parset dataset. Chromosome names have been aliased using the input genome
     */
    WiggleDataset dataset;
    /**
     * The type of wiggle locator (see UCSC documentation).
     */
    protected Type type = Type.BED_GRAPH;

    // State variables.  This is a serial type parser,  these variables are used to hold temporary
    // state.
    protected String chr;
    protected String lastChr = "";
    protected int lastPosition = 0;
    protected int start;
    protected int step = 1;
    protected int windowSpan = 1;
    protected int startBase = 1;   // <- set to zero for zero based coordinates
    IntArrayList startLocations = null;
    IntArrayList endLocations = null;
    FloatArrayList data = null;
    protected ResourceLocator resourceLocator;
    /**
     * Set of unsorted chromosomes, BEFORE ALIASING
     */
    protected Set<String> unsortedChromosomes;
    int estArraySize;
    Map<String, Integer> longestFeatureMap = new HashMap();
    // Used to estimate percentiles
    protected static final int maxSamples = 1000;
    DownsampledDoubleArrayList sampledData = new DownsampledDoubleArrayList(maxSamples, maxSamples);


    public WiggleParser(ResourceLocator locator) {
        this(locator, null);
    }

    public WiggleParser(ResourceLocator locator, Genome genome) {

        this.resourceLocator = locator;
        this.genome = genome;
        this.estArraySize = estArraySize(locator, genome);
        this.dataset = new WiggleDataset(genome, locator.getTrackName());

        if (locator.getPath().endsWith("CpG.txt")) {
            type = Type.CPG;
        } else if (locator.getPath().toLowerCase().endsWith(".expr")) {
            //gene_id	bundle_id	chr	left	right	FPKM	FPKM_conf_lo	FPKM_conf_hi
            type = Type.EXPR;
            chrColumn = 2;
            startColumn = 3;
            endColumn = 4;
            dataColumn = 5;
            startBase = 1;
            dataset.setType(TrackType.EXPR);
        }
    }

    private int estArraySize(ResourceLocator locator, Genome genome) {

        int estLines = ParsingUtils.estimateLineCount(locator.getPath());
        int nChromosomes = genome == null ? 24 : genome.getChromosomeNames().size();
        return Math.max(1000, (estLines / nChromosomes));

    }

    /**
     * Utility method.  Returns true if this looks like a wiggle locator,
     * based on file extension
     *
     * @param file
     * @return
     */
    public static boolean isWiggle(ResourceLocator file) {
        String ts = file.getFormat();
        return (ts.equals("cpg") || ts.equals("expr") || ts.equals("wig"));
    }

    public WiggleDataset parse() {
        parseFile(this.resourceLocator);
        this.parsingComplete();
        return this.dataset;
    }

    protected void parseFile(ResourceLocator locator) {

        initializeDataHolders();
        unsortedChromosomes = new HashSet();

        AsciiLineReader reader = null;
        String nextLine = null;
        int lineNumber = 0;
        float[] dataArray = null;

        try {
            reader = ParsingUtils.openAsciiReader(locator);

            if (type == Type.EXPR) {
                reader.readLine(); // Skip header line
            } else if(type == null) {
                type = Type.BED_GRAPH;   // Until proven otherwise (e.g. by fixedStep line)
            }

            int position = -1;

            while ((nextLine = reader.readLine()) != null) {
                lineNumber++;

                if (nextLine.startsWith("#") || nextLine.startsWith("data") || nextLine.startsWith("browser") || nextLine.trim().length() == 0) {
                    continue;
                    // Skip
                }

                if (nextLine.startsWith("track")) {
                    ParsingUtils.parseTrackLine(nextLine, dataset.getTrackProperties());
                    if (dataset.getTrackProperties().getBaseCoord() == TrackProperties.BaseCoord.ZERO) {
                        this.startBase = 0;
                    }

                } else if (nextLine.startsWith("fixedStep")) {
                    type = Type.FIXED;
                    parseStepLine(nextLine);
                    position = start;
                    if (start < lastPosition) {
                        unsortedChromosomes.add(chr);
                    }

                } else if (nextLine.startsWith("variableStep")) {
                    type = Type.VARIABLE;
                    parseStepLine(nextLine);
                    if (start < lastPosition) {
                        unsortedChromosomes.add(chr);
                    }

                } else {
                    // Must be data
                    String[] tokens = Globals.singleTabMultiSpacePattern.split(nextLine);
                    int nTokens = tokens.length;
                    if (nTokens == 0) {
                        continue;
                    }
                    try {
                        if (type.equals(Type.CPG)) {

                            if (nTokens > 3) {
                                chr = tokens[1].trim();
                                if (!chr.equals(lastChr)) {
                                    changedChromosome(dataset, lastChr);
                                }
                                lastChr = chr;

                                int endPosition = -1;
                                try {
                                    endPosition = Integer.parseInt(tokens[2].trim());
                                } catch (NumberFormatException numberFormatException) {
                                    log.error("Column 2 is not a number");

                                    throw new ParserException("Column 2 must be numeric." + " Found: " + tokens[1],
                                            lineNumber, nextLine);
                                }
                                int startPosition = endPosition - 1;

                                if (startPosition < lastPosition) {
                                    unsortedChromosomes.add(chr);
                                }
                                lastPosition = startPosition;

                                float value = Float.parseFloat(tokens[4].trim());
                                if (tokens[3].trim().equals("R")) {
                                    value = -value;
                                }

                                addData(chr, startPosition, endPosition, value);
                            }
                        } else if (type.equals(Type.BED_GRAPH) || type.equals(Type.EXPR)) {

                            if (nTokens > 3) {
                                chr = tokens[chrColumn].trim();
                                if (!chr.equals(lastChr)) {
                                    changedChromosome(dataset, lastChr);
                                    //If we are seeing this chromosome again with something
                                    //in-between, assume it's unsorted
                                    if(dataset.containsChromosome(chr)){
                                        unsortedChromosomes.add(chr);
                                    }

                                }
                                lastChr = chr;

                                int startPosition = -1;
                                try {
                                    startPosition = Integer.parseInt(tokens[startColumn].trim());
                                } catch (NumberFormatException numberFormatException) {
                                    log.error("Column " + (startColumn + 1) + "  is not a number");

                                    throw new ParserException("Column (startColumn + 1) must be numeric." + " Found: " +
                                            tokens[startColumn],
                                            lineNumber, nextLine);
                                }

                                if (startPosition < lastPosition) {
                                    unsortedChromosomes.add(chr);
                                }
                                lastPosition = startPosition;


                                int endPosition = -1;
                                try {
                                    endPosition = Integer.parseInt(tokens[endColumn].trim());
                                    int length = endPosition - startPosition;
                                    updateLongestFeature(length);
                                } catch (NumberFormatException numberFormatException) {
                                    log.error("Column " + (endColumn + 1) + " is not a number");

                                    throw new ParserException("Column " + (endColumn + 1) +
                                            " must be numeric." + " Found: " + tokens[endColumn],
                                            lineNumber, nextLine);
                                }

                                addData(chr, startPosition, endPosition, Float.parseFloat(tokens[dataColumn].trim()));
                            }
                        } else if (type.equals(Type.VARIABLE)) {
                            if (nTokens > 1) {

                                // Per UCSC specification variable and fixed step coordinates are "1" based.
                                // We need to subtract 1 to convert to the internal "zero" based coordinates.
                                int startPosition = Integer.parseInt(tokens[0]) - 1;
                                if (startPosition < lastPosition) {
                                    unsortedChromosomes.add(chr);
                                }
                                lastPosition = startPosition;

                                int endPosition = startPosition + windowSpan;
                                addData(chr, startPosition, endPosition, Float.parseFloat(tokens[1]));
                            }
                        } else {    // Fixed step -- sorting is checked when step line is parsed
                            if (position >= 0) {
                                if (dataArray == null) {
                                    dataArray = new float[nTokens];
                                }
                                for (int ii = 0; ii < dataArray.length; ii++) {
                                    dataArray[ii] = Float.parseFloat(tokens[ii].trim());
                                }
                                int endPosition = position + windowSpan;
                                addData(chr, position, endPosition, dataArray);
                            }
                            position += step;
                            lastPosition = position;
                        }

                    } catch (NumberFormatException e) {
                        log.error(e);
                        throw new ParserException(e.getMessage(), lineNumber, nextLine);
                    }


                }

            }

            // The last chromosome
            changedChromosome(dataset, lastChr);

        } catch (ParserException pe) {
            throw (pe);
        } catch (Exception e) {
            if (nextLine != null && lineNumber != 0) {
                throw new ParserException(e.getMessage(), e, lineNumber, nextLine);
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    protected void parsingComplete() {
        dataset.sort(unsortedChromosomes);
        dataset.setLongestFeatureMap(longestFeatureMap);

        double[] sd = sampledData.toArray();
        double percent10 = StatUtils.percentile(sd, 10.0);
        double percent90 = StatUtils.percentile(sd, 90.0);
        dataset.setPercent10((float) percent10);
        dataset.setPercent90((float) percent90);
    }

    private void updateLongestFeature(int length) {
        if (longestFeatureMap.containsKey(chr)) {
            longestFeatureMap.put(chr, Math.max(longestFeatureMap.get(chr), length));
        } else {
            longestFeatureMap.put(chr, length);
        }
    }

    // fixedStep chrom=chrM strt=1 step=1

    protected void parseStepLine(String header) {
        String[] tokens = header.split("\\s+");
        for (String token : tokens) {
            String[] keyValue = token.split("=");
            if (keyValue.length >= 2) {
                if (keyValue[0].equalsIgnoreCase("chrom")) {
                    chr = keyValue[1];
                    if (!chr.equals(lastChr)) {
                        changedChromosome(dataset, lastChr);

                    }
                    lastChr = chr;

                } else if (keyValue[0].equalsIgnoreCase("start")) {
                    // Per UCSC specification variable and fixed step coordinates are "1" based.
                    // We need to subtract 1 to convert to the internal "zero" based coordinates.

                    start = Integer.parseInt(keyValue[1]) - startBase;
                    if (start < lastPosition) {
                        unsortedChromosomes.add(chr);
                    }

                } else if (keyValue[0].equalsIgnoreCase("step")) {
                    step = Integer.parseInt(keyValue[1]);
                } else if (keyValue[0].equalsIgnoreCase("span")) {
                    windowSpan = Integer.parseInt(keyValue[1]);
                    updateLongestFeature(windowSpan);
                }

            }
        }
    }


    protected void changedChromosome(WiggleDataset dataset, String lastChr) {

        if (startLocations != null && startLocations.size() > 0) {

            String convertedChr = genome == null ? lastChr : genome.getCanonicalChrName(lastChr);
            dataset.addDataChunk(convertedChr, startLocations, endLocations, data);
            //sz = startLocations.size();

            float[] f = data.toArray();
            for (float ii : f) {
                sampledData.add(ii);
            }


        }
        initializeDataHolders();
    }

    protected void initializeDataHolders() {
        startLocations = new IntArrayList(estArraySize);
        endLocations = new IntArrayList(estArraySize);
        data = new FloatArrayList(estArraySize);
        lastPosition = -1;
    }

    public void addData(String chr, int startPosition, int endPosition, float[] values) {
        addData(chr, startPosition, endPosition, values[0]);
    }

    public void addData(String chr, int startPosition, int endPosition, float value) {
        startLocations.add(startPosition);
        endLocations.add(endPosition);
        this.data.add(value);
    }

}
