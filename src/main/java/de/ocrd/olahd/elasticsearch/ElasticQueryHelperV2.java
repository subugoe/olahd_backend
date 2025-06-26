package de.ocrd.olahd.elasticsearch;

import static de.ocrd.olahd.Constants.LOGICAL_INDEX_NAME;

import de.ocrd.olahd.domain.EsNumberQuery;
import de.ocrd.olahd.domain.SearchTerms;
import de.ocrd.olahd.utils.Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * Class to create the facet-search-query. Groups together steps to create the Elasticsearch Query to do the facet
 * search
 */
public class ElasticQueryHelperV2 {

    /** Name of the aggregation containing the search hits */
    public static final String HITS_AGG = "group-by-pid";
    /** Name of the sub-aggregation containing the pids per facet */
    public static final String SUB_AGG_PIDS = "pids-per-facet";
    /** Max size of pids-per-facet aggregation */
    public static final int MAX_PID_PER_FACET = 100;
    public static final String COUNTER_AGG = "counter";

    /** Fields which are fetched from source */
    private static final String[] SOURCE_FIELDS = new String[] { "pid", "publish_infos", "title", "doctype", "IsGt",
        "creator_infos", "structrun", "filegrp_use_types"
    };

    private final static String F_CREATOR = "Creators";
    private final static String F_TITLE = "Titles";
    private final static String F_PLACE = "Place";
    private final static String F_YEAR = "Publish Year";
    private final static String F_FGRP = "File Groups";

    /**
     * Mapping from filter-name to corresponding column
     *
     * Filters are named "Creators", "Titles" or "Publishers" etc. This function returns the corresponding column from
     * the Elasticsearch-entry. For example for Filter Creator, the column to filter must be creator_infos.name.keyword
     */
    public static final Map<String, String> FILTER_MAP = Map.of(
        F_CREATOR, "creator_infos.name.keyword",
        F_TITLE, "title.title.keyword",
        F_PLACE, "publish_infos.place_publish.keyword",
        F_YEAR, "publish_infos.year_publish",
        F_FGRP, "filegrp_use_types.keyword"
    );

    private SearchTerms searchterms;
    private int limit;
    private int offset;
    private boolean extended;
    private Boolean isGt;
    private boolean metadatasearch;
    private boolean fulltextsearch;
    private String sort;
    private String[] field;
    private String[] value;
    private Set<String> fulltextPids;

    public ElasticQueryHelperV2(
        SearchTerms searchterm, int limit, int offset, boolean extended, Boolean isGt,
        boolean metadatasearch, boolean fulltextsearch, String sort, String[] field, String[] value,
        Set<String> fulltextPids
    ) {
        super();
        this.searchterms = searchterm;
        this.limit = limit;
        this.offset = offset;
        this.extended = extended;
        this.isGt = isGt;
        this.metadatasearch = metadatasearch;
        this.fulltextsearch = fulltextsearch;
        this.sort = sort;
        this.field = field;
        this.value = value;
        this.fulltextPids = fulltextPids;
    }

    /**
     * Create the "searchSource". This is the search-Document elasticsearch executes
     *
     * The search consists of four parts: - the part of the query responsible for matching the documents
     * (query.bool.must) - the part of the query for filtering the results (query.bool.filter) - the aggregation used to
     * group the search hits (aggregations.group-by-pid) - the aggregations for collecting the facets
     * (aggregations.Titles, aggregations.Creators ...)
     *
     * @return
     */
    public SearchRequest createSearchRequest() {
        SearchRequest res = new SearchRequest().indices(LOGICAL_INDEX_NAME);
        SearchSourceBuilder source = new SearchSourceBuilder();
        res.source(source);

        // part 1: matching
        BoolQueryBuilder query = this.createQuery();
        // part 2: filters
        // TODO: according to API do not use filters if 'extended' is specified. could be added here
        this.addFacetFilters(query);
        // part 4: aggregations for collecting the facets
        List<TermsAggregationBuilder> aggFacets = this.createFacetAggregations();

        // putting things together
        source.query(query);
        source.from(offset);
        source.size(limit);

        for (TermsAggregationBuilder agg : aggFacets) {
            source.aggregation(agg);
        }
        return res;
    }

    /**
     * Add filters corresponding to the facets selected by the user
     *
     * @param query
     */
    private void addFacetFilters(BoolQueryBuilder query) {
        if (field != null && field.length > 0) {
            Map<String, List<String>> filters = new HashMap<>();
            for (int i = 0; i < field.length; i++) {
                String fieldName = FILTER_MAP.getOrDefault(field[i], field[i]);
                filters.putIfAbsent(fieldName, new ArrayList<>());
                filters.get(fieldName).add(value[i]);
            }
            BoolQueryBuilder boolMust = QueryBuilders.boolQuery();
            for (Entry<String, List<String>> entry : filters.entrySet()) {
                BoolQueryBuilder boolShould = QueryBuilders.boolQuery();
                for (String filterValue : entry.getValue()) {
                    boolShould.should(QueryBuilders.termQuery(entry.getKey(), filterValue));
                }
                boolMust.must(boolShould);
            }
            query.filter(boolMust);
        }
    }

    private BoolQueryBuilder createQuery() {
        BoolQueryBuilder res = QueryBuilders.boolQuery();
        String searchterm = searchterms.getSearchterm();
        res = res.must(QueryBuilders.matchQuery("IsFirst", true));
        if (StringUtils.isNotBlank(searchterm)) {
            if (metadatasearch && fulltextsearch) {
                BoolQueryBuilder boolMust = QueryBuilders.boolQuery();
                BoolQueryBuilder boolShould = QueryBuilders.boolQuery();
                boolShould.should(addMatchOrQstr("metadata", searchterm));
                boolShould.should(
                    QueryBuilders.termsQuery("pid.keyword", fulltextPids != null ? fulltextPids : new HashSet<>())
                );
                res = res.must(boolMust.must(boolShould));
            } else if (fulltextsearch) {
                res = res.must(
                    QueryBuilders.termsQuery("pid.keyword", fulltextPids != null ? fulltextPids : new HashSet<>())
                );
            } else {
                res = res.must(addMatchOrQstr("metadata", searchterm));
            }
        }
        if (Boolean.TRUE.equals(this.isGt)) {
            res = res.must(QueryBuilders.matchQuery("IsGt", true));
        }
        // This sets filters for the "advanced search"
        if (searchterms.hasFilter()) {
            if (StringUtils.isNotBlank(searchterms.getAuthor())) {
                res = res.filter(
                    addMatchOrQstr("creator_infos.name", searchterms.getAuthor())
                );
            }
            if (StringUtils.isNotBlank(searchterms.getTitle())) {
                res = res.filter(addMatchOrQstr("title.title", searchterms.getTitle()));
            }
            if (StringUtils.isNotBlank(searchterms.getPlace())) {
                res = res.filter(
                    addMatchOrQstr("publish_infos.place_publish", searchterms.getPlace())
                );
            }
            if (StringUtils.isNotBlank(searchterms.getYear())) {
                QueryBuilder numberQuery = createYearQuery(searchterms.getYear());
                if (numberQuery != null) {
                    res = res.filter(numberQuery);
                } else {
                    Utils.logInfo("Search for year cannot be used: '" + searchterms.getYear() + "'");
                }
            }
        }
        return res;
    }

    /**
     * Add a match- or a query-string-query
     *
     * This method checks weather the searchterm contains one or more asterisks and then either creates a match query or
     * a query_string_query.
     *
     * Previously we only had a match query which hits on complete word matches. Later we wanted to add a wildcard
     * search. This creates one of it. I wanted to keep the match query for when the asterisk is not used, because
     * I think it is faster.
     *
     * @param string
     * @param searchterm
     * @return
     */
    private QueryBuilder addMatchOrQstr(String fieldname, String searchterm) {
        if (searchterm.indexOf('*') > -1) {
            return QueryBuilders.queryStringQuery(searchterm).field(fieldname);
        } else {
            return QueryBuilders.matchQuery(fieldname, searchterm);
        }
    }

    private QueryBuilder createYearQuery(String numberStr) {
        if (numberStr.indexOf('*') > -1) {
            // asterisk and range cannot work together
            if (numberStr.indexOf('>') > -1 || numberStr.indexOf('<') > -1) {
                return null;
            }
            return QueryBuilders.queryStringQuery(numberStr).field("publish_infos.year_publish_string");
        }
        EsNumberQuery x = EsNumberQuery.fromQueryString(numberStr);
        if (x == null) {
            return null;
        } else if (x.cmp == EsNumberQuery.Cmp.EQ) {
            return QueryBuilders.matchQuery("publish_infos.year_publish", numberStr);
        }

        var rangeQuery = QueryBuilders.rangeQuery("publish_infos.year_publish");
        switch (x.cmp) {
        case GT:
            return rangeQuery.gt(x.value1);
        case LT:
            return rangeQuery.lt(x.value1);
        case GTE:
            return rangeQuery.gte(x.value1);
        case LTE:
            return rangeQuery.lte(x.value1);
        case RANGE:
            return rangeQuery.gte(x.value1).lte(x.value2);
        default:
            // This cannot happen, except the enum was extended
            throw new AssertionError("Unexpected switch default: createYearQuery");
        }
    }

    private List<TermsAggregationBuilder> createFacetAggregations() {
        List<TermsAggregationBuilder> res = new ArrayList<>();
        // Facets
        res.add(createSingleFacetAggregation(F_TITLE, FILTER_MAP.get(F_TITLE)));
        res.add(createSingleFacetAggregation(F_CREATOR, FILTER_MAP.get(F_CREATOR)));
        res.add(createSingleFacetAggregation(F_PLACE, FILTER_MAP.get(F_PLACE)));
        res.add(createSingleFacetAggregation(F_YEAR, FILTER_MAP.get(F_YEAR)));
        res.add(createSingleFacetAggregation(F_FGRP, FILTER_MAP.get(F_FGRP)));
        return res;
    }

    private TermsAggregationBuilder createSingleFacetAggregation(String term, String field) {
        return AggregationBuilders.terms(term).field(field);
    }
}
