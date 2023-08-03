package ola.hd.longtermstorage.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * static functions for simple tasks
 */
public class Utils {

    private Utils() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
    private static final String BAGINFO_REGEX_LINEBREAK = "\\r?\\n|\\r";
    private static final String BAGINFO_REGEX_KEY_VALUE_SEPARATOR= ":\\s{0,1}";
    private static final String BAGINFO_REGEX_INDENT_LINE = "^\\s+.*";

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
                res.put(lastKey, oldVal + line.stripLeading());
            } else {
                String[] parts = line.split(BAGINFO_REGEX_KEY_VALUE_SEPARATOR, 2);
                lastKey = parts[0];
                String value = parts[1];
                res.put(lastKey, value);
            }
        }
        return res;
    }

    private static final Set<String> trueSet = new HashSet<String>(Arrays.asList("1", "true", "yes"));
    private static final Set<String> falseSet = new HashSet<String>(Arrays.asList("0", "false", "no"));

    /**
     * Parse booleans from string with 1, true, yes, 0, false, no
     *
     * @param s
     * @return
     */
    public static Boolean stringToBool(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        s = s.trim().toLowerCase();
        if (trueSet.contains(s)) {
            return true;
        } else if (falseSet.contains(s)) {
            return false;
        }
        return null;
    }

    /**
     * Reads host and protocol from request and returns it.
     *
     * @param request
     * @return Examples: <code>http://localhost:8080</code>, <code>http://ola-hd.ocr-d.de</code>
     */
    public static String readHost(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        int port = request.getServerPort();
        String res = String.format("%s://%s", request.getScheme(), request.getServerName());
        if (port != 80 && port != 443) {
            res += ":" + String.valueOf(port);
        }
        return res;
    }

    public static void logInfo(String msg) {
        LOG.info(msg);
    }

    public static void logWarn(String msg) {
        LOG.warn(msg);
    }
}
