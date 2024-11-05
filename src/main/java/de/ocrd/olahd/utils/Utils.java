package de.ocrd.olahd.utils;

import de.ocrd.olahd.Constants;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
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

    /**
     * Convert the Bag-Info-Map to a String
     *
     * Example:
     * ```
     * Foo-Key: Foo-Value
     * Bar-Key: Bar-Value
     * ```
     * No line breaks, line lenght is not limited
     *
     * @param bagInfoMap
     * @return
     */
    public static String writeBagInfoMapToString(Map<String, String> bagInfoMap) {
        StringBuilder res = new StringBuilder();
        for(Map.Entry<String,String> entry : bagInfoMap.entrySet()){
            String key = entry.getKey() != null ? entry.getKey() : "";
            String value = entry.getValue() != null ? entry.getValue() : "";
            res.append(key).append(": ").append(value).append("\n");
        }
        return res.toString();
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

    private static String baseUrl = null;

    public static String getBaseUrl(HttpServletRequest request) {
        if (baseUrl == null) {
            baseUrl = readHost(request);
        }
        return baseUrl;
    }

    /**
     * Reads host and "scheme" from request and returns it.
     *
     * @param request
     * @return Examples: <code>http://localhost:8080</code>, <code>https://ola-hd.ocr-d.de</code>
     */
    private static String readHost(HttpServletRequest request) {

        if (request == null) {
            return "";
        }
        int port = request.getServerPort();
        String scheme = request.getScheme();

        if ("https".equals(request.getHeader("x-forwarded-proto"))) {
            scheme = "https";
        }

        String res = String.format("%s://%s", scheme, request.getServerName());
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

    public static void logWarn(String msg, Throwable t) {
        LOG.warn(msg, t);
    }

    public static void logDebug(String msg) {
        LOG.debug(msg);
    }

    public static void logError(String msg, Throwable t) {
        LOG.error(msg, t);
    }

    /**
     * Purpose of this function is logging only
     *
     * @param request
     * @return
     */
    public static String readRequestInfos(HttpServletRequest request) {
        if (request == null) {
            return "null";
        }
        StringBuilder res = new StringBuilder();
        res.append("Method: ").append(request.getMethod()).append("; ");
        try {
            res.append("Parts: ");
            Boolean first = true;
            for (Part p : request.getParts()) {
                if (first) {
                    first = false;
                } else {
                    res.append(",");
                }
                res.append(p.getName());
            }
            res.append("; ");

        } catch(Exception e) {
            res.append("cannot read parts: " + e);
        }

        try {
            res.append("Headers: ");
            Boolean first = true;
            for (String name : Collections.list(request.getHeaderNames())) {
                if (first) {
                    first = false;
                } else {
                    res.append(", ");
                }
                String header = request.getHeader(name);
                res.append("name: '").append(name).append("'").append(" value: '").append(header).append("'");
            }
            res.append("; ");
        } catch (Exception e) {
            res.append("cannot read headers: " + e);
        }
        res.append("Request.toString(): ").append(request.toString()).append("; ");
        res.append("RemoteHost: ").append(request.getRemoteHost()).append("; ");
        res.append("RemoteAddr: ").append(request.getRemoteAddr()).append("; ");
        res.append("RemotePort: ").append(request.getRemotePort()).append("; ");
        res.append("LocalName: ").append(request.getLocalName()).append("; ");
        res.append("LocalAddr: ").append(request.getLocalAddr()).append("; ");
        res.append("Protocol: ").append(request.getProtocol()).append("; ");
        return res.toString();
    }

    /**
     * Values like null and none are considered not existing.
     *
     * @param value
     * @return
     */
    public static boolean isNullValue(String value) {
        if (StringUtils.isAllBlank(value)) {
            return true;
        } else if (value.trim().equalsIgnoreCase("none")) {
            return true;
        } else if (value.trim().equalsIgnoreCase("null")) {
            return true;
        }
        return false;
    }

    /**
     * If Ocrd-Mets in `bagInfoMap` return its value otherwise the mets is expected at data/mets.xml
     *
     * @param bagInfoMap - Map containing key-value-pairs from bag-info.txt
     * @return
     */
    public static String getMetsPath(Map<String, String> bagInfoMap) {
        if (bagInfoMap.containsKey(Constants.BAGINFO_KEY_METS)) {
            return bagInfoMap.get(Constants.BAGINFO_KEY_METS);
        } else {
            return "data/mets.xml";
        }
    }

}
