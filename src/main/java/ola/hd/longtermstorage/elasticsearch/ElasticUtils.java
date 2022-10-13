package ola.hd.longtermstorage.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ola.hd.longtermstorage.model.Facets;
import ola.hd.longtermstorage.model.FulltextSnippets;
import ola.hd.longtermstorage.model.HitList;
import ola.hd.longtermstorage.model.ResultSet;
import ola.hd.longtermstorage.model.Values;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.springframework.util.CollectionUtils;

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
            String pid  = hitmap.getOrDefault("pid", "").toString();
            String id = "";
            String title = hitmap.getOrDefault("bytitle", "").toString();
            String subtitle = ElasticUtils.readSubtitleFromSearchHit(hitmap);
            //TODO: read multiple values together to only access the same fields once:
            String placeOfPublish = ElasticUtils.readPlaceOfPublishFromSearchHit(hitmap);
            int yearOfPublish = ElasticUtils.readYearFromSearchHit(hitmap);
            String publisher = ElasticUtils.readPublisherFromSearchHit(hitmap);
            String creator = hitmap.getOrDefault("bycreator", "").toString();
            Boolean gt = (Boolean)hitmap.getOrDefault("isGT", Boolean.FALSE);
            FulltextSnippets fulltextSnippets = null;
            HitList res = new HitList(pid, id, title, subtitle, placeOfPublish, yearOfPublish,
                    publisher, creator, fulltextSnippets, gt);
            hitList.add(res);
        }

        return new ResultSet(searchterm, (int)hits.totalHits, offset, limit,
                metadatasearch, fulltextsearch, hitList, facets);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readPlaceOfPublishFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map)hit.get("publish_infos");
            List<String> places = (List<String>)infos.get("place_publish");
            for (String s : places) {
                if (StringUtils.isNotBlank(s)) {
                    return s;
                }
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readSubtitleFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> title = (Map)hit.get("title");
            if (title.containsKey("subtitle")) {
                Object object = title.get("subtitle");
                if (object != null && object instanceof String) {
                    return object.toString();
                }
            }
        } catch (Exception e) {
            //pass: just skip if value is not available
        }
        return "";
    }

    /**
     * Mapping for request parameter-fields to "column-names" in elasticsearch
     *
     * @param field
     * @return
     */
    public static String getFilternameForField(String field) {
        // TODO: use a hashmap here
        if (Stream.of("author", "creator", "creators").anyMatch(field::equalsIgnoreCase)) {
            return "creator_infos.name.keyword";
        }
        return field;
    }

    /**
     * Fill aggregation-values from elastiscearch into objects of the response model (Facets).
     *
     * @param aggs
     * @return
     */
    public static List<Facets> aggsToFacets(Aggregations aggs) {
        List<Facets> facets = new ArrayList<>();
        if (aggs == null) {
            return facets;
        }
        Map<String, Aggregation> map = aggs.getAsMap();
        if (CollectionUtils.isEmpty(map)) {
            return facets;
        }

        List<Values> values = new ArrayList<>();
        for (Map.Entry<String, Aggregation> entry : map.entrySet()) {
            Terms terms = (Terms)entry.getValue();
            List<? extends Bucket> buckets = terms.getBuckets();
            for (Bucket x : buckets) {
                Values val = new Values(x.getKeyAsString(), (int)x.getDocCount());
                values.add(val);
            }
            facets.add(new Facets(terms.getName(), values));
        }
        return facets;
    }

}
