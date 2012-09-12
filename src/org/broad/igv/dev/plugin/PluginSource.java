/*
 * Copyright (c) 2007-2012 The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc. All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever. The Broad Institute is not responsible for its use, misuse, or functionality.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 */

package org.broad.igv.dev.plugin;

import org.apache.log4j.Logger;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.CodecFactory;
import org.broad.igv.feature.tribble.IGVBEDCodec;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.Track;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.RuntimeUtils;
import org.broad.tribble.AsciiFeatureCodec;
import org.broad.tribble.Feature;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * A feature source which derives its information
 * from a command line plugin
 * User: jacob
 * Date: 2012/05/01
 */
public abstract class PluginSource<E extends Feature, D extends Feature> {

    private static Logger log = Logger.getLogger(PluginSource.class);

    /**
     * Initial command tokens. This will typically include both
     * the executable and command, e.g. {"/usr/bin/bedtools", "intersect"}
     */
    protected final List<String> cmd;
    protected final LinkedHashMap<Argument, Object> arguments;

    protected boolean strictParsing = true;
    protected String decodingCodec = null;
    protected URL[] decodingLibURLs = new URL[0];
    protected String format = "bed";

    /**
     * For decoding, we may need to know how many columns
     * were written out in the first place
     */
    protected Map<String, Integer> outputCols = new LinkedHashMap<String, Integer>(2);

    public PluginSource(List<String> cmd, LinkedHashMap<Argument, Object> arguments, Map<String, String> parsingAttrs, String specPath) {
        this.cmd = cmd;
        this.arguments = arguments;

        setParsingAttributes(parsingAttrs, specPath);
    }

    private void setParsingAttributes(Map<String, String> parsingAttrs, String specPath) {
        this.decodingCodec = parsingAttrs.get("decoding_codec");

        String tmpStrict = parsingAttrs.get("strict");
        String fmt = parsingAttrs.get("format");
        this.format = fmt != null ? fmt : this.format;
        if (tmpStrict != null) this.strictParsing = Boolean.parseBoolean(tmpStrict);
        String libs = parsingAttrs.get("libs");
        libs = libs != null ? libs : "";
        decodingLibURLs = FileUtils.getURLsFromString(libs, specPath);
    }


    /**
     * Stream will be closed after data written
     *
     * @param features
     * @param outputStream
     * @return
     */
    protected final int writeFeaturesToStream(Iterator<E> features, OutputStream outputStream, Argument argument) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream));

        int allNumCols = -1;
        if (features != null) {
            FeatureEncoder codec = getEncodingCodec(argument);
            String header = codec.getHeader();
            if (header != null) {
                writer.println(header);
            }
            while (features.hasNext()) {
                String line = codec.encode(features.next());
                if (line == null) continue;
                writer.println(line);

                //We require consistency of output
                int tmpNumCols = codec.getNumCols(line);
                if (allNumCols < 0) {
                    allNumCols = tmpNumCols;
                } else {
                    assert tmpNumCols == allNumCols;
                }
            }
        }
        writer.flush();
        writer.close();

        return allNumCols;
    }

    protected final String[] genFullCommand(String chr, int start, int end, int zoom) throws IOException {

        List<String> fullCmd = new ArrayList<String>(cmd);

        outputCols.clear();
        Map<String, String[]> argValsById = new HashMap<String, String[]>(arguments.size());
        for (Map.Entry<Argument, Object> entry : arguments.entrySet()) {
            Argument arg = entry.getKey();

            assert arg.isValidValue(entry.getValue());
            String[] sVal = null;
            String ts = null;
            switch (arg.getType()) {
                case LONGTEXT:
                case TEXT:
                    ts = (String) entry.getValue();
                    if (ts == null || ts.trim().length() == 0) {
                        continue;
                    }
                    sVal = new String[]{ts};
                    if (arg.getType() == Argument.InputType.TEXT) {
                        sVal = ts.split("\\s+");
                    }
                    break;
                case FEATURE_TRACK:
                case DATA_TRACK:
                    ts = createTempFile((Track) entry.getValue(), arg, chr, start, end, zoom);
                    sVal = new String[]{ts};
                    break;
                case MULTI_FEATURE_TRACK:
                    sVal = createTempFiles((List<FeatureTrack>) entry.getValue(), arg, chr, start, end, zoom);
                    break;
            }

            if (arg.getId() != null) {
                argValsById.put(arg.getId(), sVal);
            }

            if (arg.isOutput()) {
                String cmdArg = arg.getCmdArg();
                if (cmdArg.trim().length() > 0) {
                    for (String argId : argValsById.keySet()) {
                        cmdArg = cmdArg.replace("$" + argId, argValsById.get(argId)[0]);
                    }

                    fullCmd.add(cmdArg);
                }
                fullCmd.addAll(Arrays.asList(sVal));
            }
        }

        return fullCmd.toArray(new String[0]);
    }

    /**
     * Convenience for calling {@link #createTempFile(org.broad.igv.track.Track, org.broad.igv.dev.plugin.Argument, String, int, int, int)};
     * on each track
     *
     * @param tracks
     * @param chr
     * @param start
     * @param end
     * @param zoom
     * @return
     * @throws java.io.IOException
     */
    private String[] createTempFiles(List<FeatureTrack> tracks, Argument argument, String chr, int start, int end, int zoom) throws IOException {
        String[] fileNames = new String[tracks.size()];
        int fi = 0;
        for (FeatureTrack track : tracks) {
            fileNames[fi++] = createTempFile(track, argument, chr, start, end, zoom);
        }
        return fileNames;
    }

    /**
     * Write out data from feature sources within the specified interval
     * to temporary files.
     *
     * @param track
     * @param chr
     * @param start
     * @param end
     * @return String with temp file name.
     * @throws java.io.IOException
     */
    protected abstract String createTempFile(Track track, Argument argument, String chr, int start, int end, int zoom) throws IOException;

    protected final String createTempFile(List<E> features, Argument argument) throws IOException {
        File outFile = File.createTempFile("features", ".tmp", null);
        outFile.deleteOnExit();

        int numCols = writeFeaturesToStream(features.iterator(), new FileOutputStream(outFile), argument);
        String path = outFile.getAbsolutePath();
        outputCols.put(path, numCols);
        return path;
    }

    /**
     * Perform the actual combination operation between the constituent data
     * sources. This implementation re-runs the operation each call.
     *
     * @param chr
     * @param start
     * @param end
     * @return
     * @throws java.io.IOException
     */
    protected Iterator<D> getFeatures(String chr, int start, int end, int zoom) throws IOException {

        String[] fullCmd = genFullCommand(chr, start, end, zoom);

        //Start plugin process
        Process pr = RuntimeUtils.startExternalProcess(fullCmd, null, null);

        //Read back in the data which plugin output
        BufferedReader in = new BufferedReader(new InputStreamReader(pr.getInputStream()));

        List<D> featuresList = new ArrayList<D>();

        FeatureDecoder<D> codec = getDecodingCodec(decodingCodec, decodingLibURLs, cmd, arguments);
        codec.setOutputColumns(outputCols);

        String line;
        D feat;
        while ((line = in.readLine()) != null) {
            try {
                feat = codec.decode(line);
                if (feat != null)
                    featuresList.add(feat);
            } catch (Exception e) {
                if (strictParsing) {
                    throw new RuntimeException(e);
                }
            }
        }

        in.close();

        return featuresList.iterator();
    }

    protected FeatureEncoder getEncodingCodec(Argument argument) {
        String encodingCodec = argument.getEncodingCodec();

        if (encodingCodec == null) return new IGVBEDCodec();

        URL[] libURLs = argument.getLibURLs();

        try {
            ClassLoader loader = URLClassLoader.newInstance(
                    libURLs,
                    getClass().getClassLoader()
            );
            Class clazz = loader.loadClass(encodingCodec);
            Object codec = clazz.newInstance();
            return (FeatureEncoder) codec;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    protected FeatureDecoder<D> getDecodingCodec() {
        FeatureDecoder<D> codec = getDecodingCodec(decodingCodec, decodingLibURLs, cmd, arguments);
        codec.setOutputColumns(outputCols);
        return codec;
    }

    private FeatureDecoder<D> getDecodingCodec(String decodingCodec, URL[] libURLs, List<String> cmd, Map<Argument, Object> argumentMap) {
        if (decodingCodec == null) return
                new DecoderWrapper(CodecFactory.getCodec("." + format, GenomeManager.getInstance().getCurrentGenome()));

        try {

            ClassLoader loader = URLClassLoader.newInstance(
                    libURLs,
                    getClass().getClassLoader()
            );

            Class clazz = loader.loadClass(decodingCodec);
            Constructor constructor = clazz.getConstructor(List.class, Map.class);
            return (FeatureDecoder<D>) constructor.newInstance(cmd, argumentMap);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException(e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    private class DecoderWrapper<T extends Feature> implements FeatureDecoder<T> {

        private AsciiFeatureCodec<T> wrappedCodec;

        public DecoderWrapper(AsciiFeatureCodec<T> wrappedCodec) {
            this.wrappedCodec = wrappedCodec;
        }

        @Override
        public T decode(String line) {
            return wrappedCodec.decode(line);
        }

        @Override
        public void setOutputColumns(Map<String, Integer> outputColumns) {
            //no-op
        }
    }


}
