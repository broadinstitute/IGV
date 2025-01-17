package org.broad.igv.feature.genome;

import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.ucsc.bb.BBFile;
import java.io.IOException;

public class ChromAliasBB extends ChromAliasSource {


    BBFile reader;

    public ChromAliasBB(String path, Genome genome) throws IOException {
        this.reader =  new BBFile(path, genome);
    }


    /**
     * Return an alternate chromosome name (alias).
     *
     * @param chr
     * @param nameSet -- The name set, e.g. "ucsc"
     * @returns {*|undefined}
     */
    public String getChromosomeAlias(String chr, String nameSet) {
        ChromAlias aliasRecord = this.aliasCache.get(chr);
        return aliasRecord != null && aliasRecord.containsKey(nameSet) ? aliasRecord.get(nameSet) : chr;
    }

    /**
     * Search for chromosome alias bed record.  If found, cache results in the alias -> chr map
     *
     * @param alias
     * @returns {Promise<any>}
     */
    public ChromAlias search(String alias) throws IOException {
        if (!this.aliasCache.containsKey(alias)) {
            IGVFeature f =  this.reader.search(alias);
            if (f != null) {
                String chr = f.getChr();
                ChromAlias aliasRecord = new ChromAlias(chr);
                this.aliasCache.put(chr, aliasRecord);
                for (String key : f.getAttributeKeys()){
                    final String a = f.getAttribute(key);
                    aliasRecord.put(key, a);
                    this.aliasCache.put(a, aliasRecord);      // One entry for each alias
                }
            }
        }
        return this.aliasCache.get(alias);
    }

    public String [] getChromosomeNames() {
        return this.reader.getChromosomeNames();
    }

}