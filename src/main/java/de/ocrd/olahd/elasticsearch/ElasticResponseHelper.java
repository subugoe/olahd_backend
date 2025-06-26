package de.ocrd.olahd.elasticsearch;

import de.ocrd.olahd.domain.SearchTerms;
import de.ocrd.olahd.model.Detail;
import de.ocrd.olahd.model.Facets;
import de.ocrd.olahd.model.HitList;
import de.ocrd.olahd.model.ResultSet;
import de.ocrd.olahd.model.Values;
import de.ocrd.olahd.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.springframework.util.CollectionUtils;

/**
 * Helper functions for the work with elasticsearch
 */
public class ElasticResponseHelper {

    private static final Object CREATOR_SEPARATOR = "; ";
    private static final Object PLACES_SEPARATOR = ", ";

    public ElasticResponseHelper() {
    }

    /**
     * Extract the results from the response and fill it into the response model.
     *
     * The query uses an aggregation to put the search hits belonging to one ocrd-zip together. The
     * results must be extracted from this aggregation. Additionally aggregations where queried for
     * the facets/filter-values. These are filled into a List of {@linkplain Facets} here and put
     * into the response model ({@linkplain ResultSet}.
     *
     * @return
     */
    public ResultSet responseToResultSet(
        SearchResponse response, SearchTerms searchterms, boolean metadatasearch,
        boolean fulltextsearch, int offset, int limit
    ) {
        Aggregations aggs = response.getAggregations();
        Terms hits = (Terms) aggs.get(ElasticQueryHelper.HITS_AGG);
        Cardinality counter = (Cardinality) aggs.get(ElasticQueryHelper.COUNTER_AGG);

        ResultSet res = putHitAggsIntoResponseModel(hits, counter);
        List<Facets> facets = this.createFacetsFromAggs(aggs);
        res.setFacets(facets);
        res.setSearchTerms(searchterms);
        res.setMetadataSearch(metadatasearch);
        res.setFulltextSearch(fulltextsearch);
        res.setOffset(offset);
        res.setLimit(limit);
        return res;
    }

    /**
     * Fill the search hit from a detail-query into the response model
     *
     * @param hit
     * @return
     */
    public Detail fillSearchHitIntoDetail(Map<String, Object> hit) {
        Detail res = new Detail();
        res.setPID(hit.getOrDefault("pid", "").toString());
        res.setTitle(readTitleFromSearchHit(hit));
        res.setSubtitle(readSubtitleFromSearchHit(hit));
        res.setPublisher(readPublisherFromSearchHit(hit));
        res.setYearOfPublish(readYearFromSearchHit(hit));
        res.setPlaceOfPublish(readPlaceOfPublishFromSearchHit(hit));
        res.setYearDigitization(readYearDigitizationFromSearchHit(hit));
        res.setCreator(readCreatorFromSearchHit(hit));
        res.setGT(readIsGtFromSearchHit(hit));

        return res;
    }

    /**
     * Extract the results from the response and fill it into the response model.
     *
     * This method is for the simplified search query
     *
     * @return
     */
    public ResultSet responseToResultSetV2(
        SearchResponse response, SearchTerms searchterms, boolean metadatasearch,
        boolean fulltextsearch, int offset, int limit
    ) {

        ResultSet res = new ResultSet();
        List<HitList> hitlist = new ArrayList<>();
        res.setHitlist(hitlist);

        for (SearchHit hit : response.getHits()) {
            Map<String, Object> hitmap = hit.getSourceAsMap();
            HitList hitResult = new HitList();
            hitlist.add(hitResult);
            hitResult.setPid(hitmap.get("pid").toString());
            hitResult.setTitle(readTitleFromSearchHit(hitmap));
            hitResult.setSubtitle(readSubtitleFromSearchHit(hitmap));
            hitResult.setPlaceOfPublish(readPlaceOfPublishFromSearchHit(hitmap));
            hitResult.setYearOfPublish(readYearFromSearchHit(hitmap));
            hitResult.setPublisher(readPublisherFromSearchHit(hitmap));
            hitResult.setCreator(readCreatorFromSearchHit(hitmap));
            hitResult.setGt(readIsGtFromSearchHit(hitmap));
        }

        List<Facets> facets = new ArrayList<>();
        for (Aggregation agg : response.getAggregations().asList()) {
            Terms terms = (Terms) agg;
            List<Values> values = new ArrayList<>();
            for (Bucket bucket : terms.getBuckets()) {
                Values val = new Values(bucket.getKeyAsString(), (int)bucket.getDocCount(), false);
                values.add(val);
            }
            facets.add(new Facets(terms.getName(), values));
        }
        res.setFacets(facets);
        res.setSearchTerms(searchterms);
        res.setMetadataSearch(metadatasearch);
        res.setFulltextSearch(fulltextsearch);
        res.setOffset(offset);
        res.setLimit(limit);

        return res;
    }

    /**
     * Convert aggregations with the hits to ResultSet as specified by the API
     *
     * @param hits
     * @param counter
     * @param limit
     * @param offset
     * @return
     */
    private ResultSet putHitAggsIntoResponseModel(Terms hits, Cardinality counter) {
        ResultSet res = new ResultSet();
        List<HitList> hitlist = new ArrayList<>();
        res.setHitlist(hitlist);

        for (Bucket hit : hits.getBuckets()) {
            HitList hitResult = new HitList();
            hitlist.add(hitResult);
            Terms sub1agg = hit.getAggregations().get("group-by-log");
            Bucket sub1 = sub1agg.getBuckets().get(0);
            TopHits sub2agg = (TopHits) sub1.getAggregations().get("by_top_hits");
            Map<String, Object> hitmap = sub2agg.getHits().getAt(0).getSourceAsMap();

            hitResult.setPid(hit.getKeyAsString());
            hitResult.setTitle(readTitleFromSearchHit(hitmap));
            hitResult.setSubtitle(readSubtitleFromSearchHit(hitmap));
            hitResult.setPlaceOfPublish(readPlaceOfPublishFromSearchHit(hitmap));
            hitResult.setYearOfPublish(readYearFromSearchHit(hitmap));
            hitResult.setPublisher(readPublisherFromSearchHit(hitmap));
            hitResult.setCreator(readCreatorFromSearchHit(hitmap));
            hitResult.setGt(readIsGtFromSearchHit(hitmap));
            hitResult.setNoData();
        }

        res.setHits((int) counter.getValue());

        return res;
    }

    /**
     * Fill aggregation-values from Elasticsearch into objects of the response model (Facets).
     *
     * @param aggs
     * @return
     */
    private List<Facets> createFacetsFromAggs(Aggregations aggs) {
        List<Facets> facets = new ArrayList<>();
        if (aggs == null) {
            return facets;
        }
        Map<String, Aggregation> map = aggs.getAsMap();
        if (CollectionUtils.isEmpty(map)) {
            return facets;
        }

        for (Map.Entry<String, Aggregation> entry : map.entrySet()) {
            if (
                entry.getKey().equals(ElasticQueryHelper.HITS_AGG)
                    || entry.getKey().equals(ElasticQueryHelper.COUNTER_AGG)
            ) {
                continue;
            }
            List<Values> values = new ArrayList<>();
            Terms terms = (Terms) entry.getValue();
            List<? extends Bucket> buckets = terms.getBuckets();
            for (Bucket x : buckets) {
                if (x.getAggregations() != null) {
                    Map<String, Aggregation> subAggMap = x.getAggregations().getAsMap();
                    Aggregation agg = subAggMap.get(ElasticQueryHelper.SUB_AGG_PIDS);
                    int pidCount = ((Terms) agg).getBuckets().size();
                    /** if `size` is reached with pids-per-facet more results are there. Edge-case (size is reached but
                     * not exceeded) is assumed (cannot be assured) when doc-count == pid-count */
                    boolean limited = ElasticQueryHelper.MAX_PID_PER_FACET == pidCount && x.getDocCount() > pidCount;
                    Values val = new Values(x.getKeyAsString(), pidCount, limited);
                    values.add(val);
                }
            }
            facets.add(new Facets(terms.getName(), values));
        }
        return facets;
    }

    /**
     * Extract the year from a Elasticsearch search hit to the logical entry
     *
     * @param hit - response from Elasticsearch query
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int readYearFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                Map<String, ?> infos = (Map) firstElement.get("publish_infos");
                Integer i = (Integer) infos.get("year_publish");
                if (i != null) {
                    return i;
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }
        try {
            Map<String, Object> infos = (Map) hit.get("publish_infos");
            Integer i = (Integer) infos.get("year_publish");
            if (i != null) {
                return i;
            }
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return -1;
    }

    /**
     * Extract the year of digitization from a Elasticsearch search hit to the logical entry
     *
     * @param hit - response from Elasticsearch query
     * @return
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int readYearDigitizationFromSearchHit(Map<String, Object> hit) {
        try {
            Map<String, Object> infos = (Map) hit.get("digitization_infos");
            Integer i = (Integer) infos.get("year_digitization");
            if (i != null) {
                return i;
            } else {
                String s = (String) infos.get("year_digitization_string");
                if (StringUtils.isNotBlank(s)) {
                    return Integer.parseInt(s.substring(0, 4));
                }
            }
        } catch (Exception e) {
            // pass: just skip if value is not available
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
    private static String readPublisherFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                Map<String, ?> infos = (Map) firstElement.get("publish_infos");
                List<String> publisher = (List<String>) infos.get("publisher");
                if (!publisher.isEmpty()) {
                    return publisher.stream().collect(Collectors.joining(", "));
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }

        try {
            Map<String, Object> infos = (Map) hit.get("publish_infos");
            List<String> publisher = (List<String>) infos.get("publisher");
            if (!publisher.isEmpty()) {
                return publisher.stream().collect(Collectors.joining(", "));
            }
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readPlaceOfPublishFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                Map<String, ?> infos = (Map) firstElement.get("publish_infos");
                List<String> places = (List<String>) infos.get("place_publish");
                StringBuilder res = new StringBuilder();
                for (String s : places) {
                    if (StringUtils.isNotBlank(s)) {
                        if (!res.toString().isBlank()) {
                            res.append(PLACES_SEPARATOR);
                        }
                        res.append(s);
                    }
                }
                if (!res.toString().isBlank()) {
                    return res.toString();
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }

        try {
            Map<String, Object> infos = (Map) hit.get("publish_infos");
            List<String> places = (List<String>) infos.get("place_publish");
            StringBuilder res = new StringBuilder();
            for (String s : places) {
                if (StringUtils.isNotBlank(s)) {
                    if (!res.toString().isBlank()) {
                        res.append(PLACES_SEPARATOR);
                    }
                    res.append(s);
                }
            }
            return res.toString().trim();
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readSubtitleFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                Map<String, ?> title = (Map) firstElement.get("title");
                if (title.containsKey("subtitle")) {
                    Object object = title.get("subtitle");
                    if (object != null && object instanceof String) {
                        return object.toString();
                    }
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }

        try {
            Map<String, Object> title = (Map) hit.get("title");
            if (title.containsKey("subtitle")) {
                Object object = title.get("subtitle");
                if (object != null && object instanceof String) {
                    return object.toString();
                }
            }
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readCreatorFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                List<Map<String, Object>> infos = (List) firstElement.get("creator_infos");
                StringBuilder result = new StringBuilder();
                for (Map<String, Object> creator : infos) {
                    if (creator.containsKey("name")) {
                        Object object = creator.get("name");
                        if (object != null && object instanceof String) {
                            if (!result.toString().isBlank()) {
                                result.append(CREATOR_SEPARATOR);
                            }
                            result.append(object.toString());
                        }
                    }
                }
                if (result.toString().isBlank()) {
                    return result.toString();
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }

        try {
            List<Map<String, Object>> infos = (List) hit.get("creator_infos");
            StringBuilder result = new StringBuilder();
            for (Map<String, Object> creator : infos) {
                if (creator.containsKey("name")) {
                    Object object = creator.get("name");
                    if (object != null && object instanceof String) {
                        if (!result.toString().isBlank()) {
                            result.append(CREATOR_SEPARATOR);
                        }
                        result.append(object.toString());
                    }
                }
            }
            return result.toString();
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return "";
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static String readTitleFromSearchHit(Map<String, Object> hit) {
        try {
            List<?> structrun = (List<?>) hit.get("structrun");
            if (structrun != null) {
                Map<String, Object> firstElement = (Map) structrun.get(0);
                Map<String, ?> title = (Map) firstElement.get("title");
                if (title.containsKey("title")) {
                    Object object = title.get("title");
                    if (object != null && object instanceof String) {
                        return object.toString();
                    }
                }
            }
        } catch (Exception e) {
            // pass: just skip if value not found in structurn and try following
        }

        try {
            Map<String, Object> title = (Map) hit.get("title");
            if (title.containsKey("title")) {
                Object object = title.get("title");
                if (object != null && object instanceof String) {
                    return object.toString();
                }
            }
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return "";
    }

    private static Boolean readIsGtFromSearchHit(Map<String, Object> hit) {
        Boolean result = null;
        try {
            result = Utils.stringToBool(hit.get("IsGt").toString());
        } catch (Exception e) {
            // pass: just skip if value is not available
        }
        return result != null ? result : false;
    }
}
