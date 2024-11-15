package org.broad.igv.ui;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * An implement of StackSet for local file paths which supports serializing/deserializing itself to a string.
 *
 * The serialized form matches the format which already existed to store recent session files.
 * This is used by the by the
 */
public class RecentFileSet extends StackSet<String> {

    private static final String DELIMITER = ";";

    public RecentFileSet(int maxSize) {
        super(maxSize);
    }

    public RecentFileSet(Collection<String> c, int maxSize) {
        super(c, maxSize);
    }

    public String asString() {
        return String.join(DELIMITER, this);
    }

    public static RecentFileSet fromString(String string, int maxSize) {
        if(string == null || string.isBlank()){
            return new RecentFileSet(maxSize);
        }
        String[] files = string.split(DELIMITER);
        List<String> fileList = Arrays.stream(files)
                .filter(s -> !s.isBlank())
                // "null" was previously accounted for in older code so it's handled here
                // it doesn't seem like it should be possible to produce now though
                .filter(s -> !s.equals("null"))
                .map(String::strip)
                .toList();
        return new RecentFileSet(fileList, maxSize);
    }


}
