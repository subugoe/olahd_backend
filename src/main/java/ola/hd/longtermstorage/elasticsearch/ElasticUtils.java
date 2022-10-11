package ola.hd.longtermstorage.elasticsearch;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper functions for the work with elasticsearch
 */
public class ElasticUtils {

    private ElasticUtils() {

    }

    /**
     * Extract the year from a elasticsearch search hit to the logical entry
     *
     * @param hit - response from elasticsearch query
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static int readYearFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            Integer i = (Integer)infos.get("year_publish");
            if (i != null) {
                return i;
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return -1;
    }

    /**
     * Extract the publisher from a elasticsearch search hit to the logical entry
     *
     * @param hit
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static String readPublisherFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            List<String> publisher = (List<String>)infos.get("publisher");
            if (!publisher.isEmpty()) {
                return publisher.stream().map(String::trim).collect(Collectors.joining(","));
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

}
