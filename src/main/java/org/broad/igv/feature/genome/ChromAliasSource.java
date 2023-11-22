package org.broad.igv.feature.genome;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

abstract public class ChromAliasSource {

    Map<String, ChromAlias> aliasCache;

    public ChromAliasSource() {
        aliasCache = new HashMap<>();
    }

    public abstract String getChromosomeName(String alias);

    public abstract String getChromosomeAlias(String chr, String nameSet);

    public abstract ChromAlias search(String alias) throws IOException;

    public void add(ChromAlias chromAlias) {
        aliasCache.put(chromAlias.getChr(), chromAlias);
    }
}
