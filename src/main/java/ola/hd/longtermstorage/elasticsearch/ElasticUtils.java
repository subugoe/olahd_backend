package ola.hd.longtermstorage.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ola.hd.longtermstorage.model.Facets;
import ola.hd.longtermstorage.model.FulltextSnippets;
import ola.hd.longtermstorage.model.HitList;
import ola.hd.longtermstorage.model.ResultSet;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

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

    /**
     * Convert Elasticsearch Hits to ResultSet as specified in the api
     *
     * @param hits
     * @return
     */
    public static ResultSet resultSetFromHits(SearchHits hits, String searchterm,
            boolean metadatasearch, boolean fulltextsearch, int offset, int limit) {
        List<HitList> hitList = new ArrayList<>();
        List<Facets> facets = new ArrayList<>();
        for (SearchHit hit : hits.getHits()) {
            Map<String, Object> hitmap = hit.getSourceAsMap();
            String PID  = hitmap.getOrDefault("pid", "").toString();
            String ID = "";
            String title = hitmap.getOrDefault("bytitle", "").toString();
            String subtitle = "";
            String placeOfPublish = "";
            int yearOfPublish = ElasticUtils.readYearFromSearchHit(hitmap);
            String publisher = ElasticUtils.readPublisherFromSearchHit(hitmap);
            String creator = hitmap.getOrDefault("bycreator", "").toString();
            FulltextSnippets fulltextSnippets = null;
            HitList res = new HitList(PID, ID, title, subtitle, placeOfPublish, yearOfPublish,
                    publisher, creator, fulltextSnippets);
            hitList.add(res);
        }

        return new ResultSet(searchterm, (int)hits.totalHits, offset, limit,
                metadatasearch, fulltextsearch, hitList, facets);
    }

    /**
     * Mapping for request parameter-fields to "column-names" in elasticsearch
     *
     * @param field
     * @return
     */
    public static String getFilternameForField(String field) {
        // TODO: use a hashmap here
        if (field.equals("author")) {
            return "creator_infos.name.keyword";
        }
        return field;
    }

}
