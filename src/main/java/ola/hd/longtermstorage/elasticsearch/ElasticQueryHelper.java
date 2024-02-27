package ola.hd.longtermstorage.elasticsearch;

import static ola.hd.longtermstorage.Constants.LOGICAL_INDEX_NAME;
import static ola.hd.longtermstorage.Constants.PHYSICAL_INDEX_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ola.hd.longtermstorage.domain.EsNumberQuery;
import ola.hd.longtermstorage.domain.SearchTerms;
import ola.hd.longtermstorage.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.elasticsearch.search.aggregations.pipeline.PipelineAggregatorBuilders;
import org.elasticsearch.search.aggregations.pipeline.bucketsort.BucketSortPipelineAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;

/**
 * Class to create the facet-search-query. Groups together steps to create the Elasticsearch Query
 * to do the facet search
 */
public class ElasticQueryHelper {

    /** Name of the aggregation containing the search hits */
    public static final String HITS_AGG = "group-by-pid";
    /** Name of the sub-aggregation containing the pids per facet */
    public static final String SUB_AGG_PIDS = "pids-per-facet";
    public static final String COUNTER_AGG = "counter";

    /** Fields which are fetched from source */
    private static final String[] SOURCE_FIELDS = new String[] { "pid", "publish_infos", "title",
        "doctype", "IsGt", "creator_infos", "structrun" };

    /**
     * Mapping from filter-name to corresponding column
     *
     * Filters are named "Creators", "Titles" or "Publishers" etc. This function returns the
     * corresponding column from the Elasticsearch-entry. For example for Filter Creator, the column
     * to filter must be creator_infos.name.keyword
     */
    public static final Map<String, String> FILTER_MAP = Map.of(
        "Creators", "creator_infos.name.keyword",
        "Titles", "title.title.keyword",
        "Place", "publish_infos.place_publish.keyword",
        "Publish Year", "publish_infos.year_publish"
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

    public ElasticQueryHelper(
        SearchTerms searchterm, int limit, int offset, boolean extended, Boolean isGt,
        boolean metadatasearch, boolean fulltextsearch, String sort, String[] field, String[] value
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
    }

    /**
     * Create the "searchSource". This is the search-Document elasticsearch executes
     *
     * The search consists of four parts: - the part of the query responsible for matching the
     * documents (query.bool.must) - the part of the query for filtering the results
     * (query.bool.filter) - the aggregation used to group the search hits
     * (aggregations.group-by-pid) - the aggregations for collecting the facets
     * (aggregations.Titles, aggregations.Creators ...)
     *
     * @return
     */
    public SearchRequest createSearchRequest() {
        SearchRequest res = new SearchRequest().indices(LOGICAL_INDEX_NAME, PHYSICAL_INDEX_NAME);
        SearchSourceBuilder source = new SearchSourceBuilder();
        res.source(source);

        // part 1: matching
        BoolQueryBuilder query = this.createQuery();
        // part 2: filters
        // TODO: according to API do not use filters if 'extended' is specified. could be added here
        this.addFilters(query);
        // part 3: aggregations for the search hits
        List<AggregationBuilder> aggsMerge = this.createMergeAggregation();
        // part 4: aggregations for collecting the facets
        List<TermsAggregationBuilder> aggFacets = this.createFacetAggregations();

        // putting things together
        source.query(query);
        source.size(0);
        for (AggregationBuilder agg : aggsMerge) {
            source.aggregation(agg);
        }
        for (TermsAggregationBuilder agg : aggFacets) {
            source.aggregation(agg);
        }
        return res;
    }

    private void addFilters(BoolQueryBuilder query) {
        // Filters:
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
        if (StringUtils.isBlank(searchterm)) {
            if (Boolean.TRUE.equals(this.isGt)) {
                res = res.must(QueryBuilders.matchQuery("IsGt", true));
            } else {
                res = res.must(QueryBuilders.matchAllQuery());
            }
        } else {
            if (metadatasearch && fulltextsearch) {
                BoolQueryBuilder boolMust = QueryBuilders.boolQuery();
                BoolQueryBuilder boolShould = QueryBuilders.boolQuery();
                boolShould.should(QueryBuilders.matchQuery("metadata", searchterm));
                boolShould.should(QueryBuilders.matchQuery("fulltext", searchterm));
                res = res.must(boolMust.must(boolShould));
            } else if (fulltextsearch) {
                res = res.must(QueryBuilders.matchQuery("fulltext", searchterm));
            } else {
                res = res.must(QueryBuilders.matchQuery("metadata", searchterm));
            }
            if (Boolean.TRUE.equals(this.isGt)) {
                res = res.must(QueryBuilders.matchQuery("IsGt", true));
            }
        }
        if (searchterms.hasFilter()) {
            if (StringUtils.isNotBlank(searchterms.getAuthor())) {
                res = res.filter(
                    QueryBuilders.matchQuery("creator_infos.name", searchterms.getAuthor())
                );
            }
            if (StringUtils.isNotBlank(searchterms.getTitle())) {
                res = res.filter(QueryBuilders.matchQuery("title.title", searchterms.getTitle()));
            }
            if (StringUtils.isNotBlank(searchterms.getPlace())) {
                res = res.filter(
                    QueryBuilders.matchQuery("publish_infos.place_publish", searchterms.getPlace())
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

    private QueryBuilder createYearQuery(String numberStr) {
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
        res.add(createSingleFacetAggregation("Titles", "title.title.keyword"));
        res.add(createSingleFacetAggregation("Creators", "creator_infos.name.keyword"));
        res.add(createSingleFacetAggregation("Place", "publish_infos.place_publish.keyword"));
        res.add(createSingleFacetAggregation("Publish Year", "publish_infos.year_publish"));
        return res;
    }

    private TermsAggregationBuilder createSingleFacetAggregation(String term, String field) {
        TermsAggregationBuilder res = AggregationBuilders.terms(term).field(field);
        return res.subAggregation(AggregationBuilders.terms(SUB_AGG_PIDS).field("pid.keyword"));
    }

    private List<AggregationBuilder> createMergeAggregation() {
        List<AggregationBuilder> res = new ArrayList<>();
        TermsAggregationBuilder byPid = AggregationBuilders.terms(HITS_AGG)
            .field("pid.keyword")
            .size(99999);
        BucketSortPipelineAggregationBuilder pager = PipelineAggregatorBuilders
            .bucketSort("pager", List.of(new FieldSortBuilder("_key").order(SortOrder.ASC)))
            .from(offset)
            .size(limit);
        TermsAggregationBuilder byLog = AggregationBuilders.terms("group-by-log")
            .field("log.keyword")
            .missing("zzz")
            .size(1)
            .order(BucketOrder.key(true));
        TopHitsAggregationBuilder byTopHits = AggregationBuilders.topHits("by_top_hits")
            .size(1)
            .fetchSource(SOURCE_FIELDS, null);
        CardinalityAggregationBuilder counter = AggregationBuilders.cardinality(COUNTER_AGG)
            .field("pid.keyword");

        byPid.subAggregation(pager);
        byPid.subAggregation(byLog);
        byLog.subAggregation(byTopHits);
        res.add(byPid);
        res.add(counter);
        return res;
    }
}
