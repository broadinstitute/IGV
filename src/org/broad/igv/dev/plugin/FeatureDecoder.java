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

import org.broad.tribble.Feature;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * User: jacob
 * Date: 2012-Aug-02
 *
 * @see PluginCodec
 * @see FeatureEncoder
 */
public interface FeatureDecoder<T extends Feature> {


    /**
     * Decode all features from the specified input stream
     * Stream is closed afterwards
     *
     * @param is
     * @param strictParsing If true, errors are thrown if we cannot parse a given line.
     *                      If false, we simply skip that line. Mostly applicable to AsciiDecoders,
     *                      but could be useful more generally.
     * @return Iterator of decoded features
     * @throws IOException
     */
    Iterator<T> decodeAll(InputStream is, boolean strictParsing) throws IOException;

    /**
     * @param outputColumns Map from temporary output file path to the number of columns
     *                      contained within that file. The implementation is not required to do anything
     *                      with this information, it is provided in case it is necessary.
     */
    void setOutputColumns(Map<String, Integer> outputColumns);

    /**
     * It may be the case that the output is processed differently, depending on the input.
     * We allow for that by
     *
     * @param commands    Command portions of the input, e.g. {"find", "."}
     * @param argumentMap Arguments with their values. e.g. "-name", "myFile"
     */
    void setInputs(List<String> commands, Map<Argument, Object> argumentMap);
}
