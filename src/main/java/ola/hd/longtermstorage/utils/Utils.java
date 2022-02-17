package ola.hd.longtermstorage.utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

/**
 * static functions for simple tasks
 */
public class Utils {

    private Utils() {
    }

    private static final String BAGINFO_REGEX_LINEBREAK = "\\r?\\n|\\r";
    private static final String BAGINFO_REGEX_KEY_VALUE_SEPARATOR= ":\\s{0,1}";
    private static final String BAGINFO_REGEX_INDENT_LINE = "\"^\\\\s+.*\"";

    /**
     * Read String of bag-info.txt-file to Map. Inspired by
     * gov.loc.repository.bagit.reader.KeyValueReader (can't use that, want to read from string).
     *
     * Assumption: format is valid, should have been validated in olahd-import-function allready.
     *
     * @param bagitInfoTxt - content of bag-info.txt
     * @return bag-info.txt keys and values in a map
     */
    public static Map<String, String> readBagInfoToMap(String text) {
        Map<String, String> res = new HashMap<>();
        String lastKey = null;
        if (StringUtils.isBlank(text)) {
            return res;
        }
        for (String line : text.split(BAGINFO_REGEX_LINEBREAK)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.matches(BAGINFO_REGEX_INDENT_LINE) && lastKey != null) {
                String oldVal = res.get(lastKey);
                res.put(lastKey, oldVal + " " + line.stripLeading());
            } else {
                String[] parts = line.split(BAGINFO_REGEX_KEY_VALUE_SEPARATOR, 2);
                lastKey = parts[0].trim();

                String value = parts[1];
                res.put(lastKey, value);
            }
        }
        return res;
    }
}
