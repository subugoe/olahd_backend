package ola.hd.longtermstorage.elasticsearch;

import static ola.hd.longtermstorage.Constants.LOGICAL_INDEX_NAME;
import static ola.hd.longtermstorage.Constants.PHYSICAL_INDEX_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import ola.hd.longtermstorage.exceptions.ElasticServiceException;
import ola.hd.longtermstorage.model.Detail;
import ola.hd.longtermstorage.model.FileTree;
import ola.hd.longtermstorage.model.ResultSet;
import ola.hd.longtermstorage.msg.ErrMsg;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;;

/**
 * Service used to do something with our elasticsearch-service.
 *
 * Currently here are 2 ways to query the index. Via Repository ({@linkplain LogicalRepository} or
 * {@linkplain PhysicalRepository} or with the RestHighLevelClient
 *
 */
@Service
public class ElasticsearchService {

    @Autowired
    private RestHighLevelClient client;

    /**
     * Execute a query with a simple query-string on both indices, logical and physical.
     *
     * @param query
     * @param from
     * @param size
     * @return
     * @throws IOException
     */
    public Object bigQuery(String query, int from, int size) throws IOException {
        SearchRequest request = new SearchRequest().indices(LOGICAL_INDEX_NAME, PHYSICAL_INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.simpleQueryStringQuery(query));
        sourceBuilder.from(from);
        sourceBuilder.size(size);
        request.source(sourceBuilder);

        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        return response.getHits();
    }

    /**
     * get an element by id
     *
     * @param index
     * @param id
     * @return
     * @throws IOException
     */
    public Map<String, Object> getElement(String index, String id) throws IOException {
        GetRequest request = new GetRequest(index).id(id);
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        if (!response.isExists()) {
            return null;
        }
        Map<String, Object> res = new HashMap<>();
        res.put("_source", response.getSourceAsMap());
        res.put("_id", response.getId());
        res.put("_index", response.getIndex());
        return res;
    }

    /**
     * Get the id for the first logical index element of a pid.
     *
     * After saving each ocrd-zip is indexed in the logical index. There can be multiple entries for
     * an ocrd-zip but only one of them with the flag IsFirst set to true. Each entry has a pid.
     * This query finds the logical entries for a pid with IsFirst set to true.
     *
     * @param pid - PID of ocrd-zip for which the index entry is requested
     * @return
     * @throws IOException
     */
    public String getLogIdForPid(String pid) {
        SearchRequest request = new SearchRequest().indices(LOGICAL_INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        request.source(sourceBuilder);
        sourceBuilder.query(QueryBuilders.boolQuery()
                .filter(QueryBuilders.termQuery("pid", pid))
                .filter(QueryBuilders.termQuery("IsFirst", true)))
                .size(1);
        sourceBuilder.fetchSource(false);
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            if (hits.getTotalHits() == 1 ) {
                return hits.getAt(0).getId();
            } else if (hits.getTotalHits() > 1) {
                throw new ElasticServiceException("Found more than one hit (IsFirst is true) in the"
                        + " logical Index for a pid");
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

//    /**
//     * Execute a query with filters
//     *
//     * @return
//     * @throws IOException
//     */
//    public SearchHits filterSearch(FilterSearchRequest filter) throws IOException {
//        SearchRequest request = null;
//        if (Boolean.TRUE.equals(filter.getPages())) {
//            request = new SearchRequest().indices(LOGICAL_INDEX_NAME, PHYSICAL_INDEX_NAME);
//        } else {
//            request = new SearchRequest().indices(LOGICAL_INDEX_NAME);
//        }
//
//        SearchSourceBuilder builder = new SearchSourceBuilder();
//        BoolQueryBuilder query = QueryBuilders.boolQuery();
//        if (StringUtils.isNotBlank(filter.getTitle())) {
//            query.must(QueryBuilders.matchQuery("bytitle", filter.getTitle()));
//        }
//        if (StringUtils.isNotBlank(filter.getAuthor())) {
//            query.must(QueryBuilders.matchQuery("bycreator", filter.getAuthor()));
//        }
//        if (filter.getYear() > 0) {
//            query.must(QueryBuilders.matchQuery("publish_infos.year_publish", filter.getYear()));
//        }
//        builder.query(query);
//        request.source(builder);
//
//        try {
//            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
//            return response.getHits();
//        } catch (IOException e) {
//            throw new ElasticServiceException("Error executing filter-search request", e);
//        }
//    }

    /**
     * Get logical index-element by pid and with IsFirst = true.
     *
     * After saving, each ocrd-zip is indexed in the logical index. There can be multiple entries for
     * an ocrd-zip but only one of them with the flag IsFirst set to true. Each entry has a pid.
     * This query finds the logical entries for a pid with IsFirst set to true.
     *
     * @param pid - PID of ocrd-zip for which the index entry is requested
     * @return
     * @throws IOException
     */
    public Detail getDetailsForPid(String pid) {
        SearchRequest request = new SearchRequest().indices(LOGICAL_INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        request.source(sourceBuilder);
        sourceBuilder.query(QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("pid.keyword", pid))
                .must(QueryBuilders.matchQuery("IsFirst", true)))
                .size(1)
                .fetchSource(new String[]{"bytitle", "bycreator", "publish_infos"}, null);

        Map<String, Object> hit = null;
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            if (hits.getTotalHits() == 0 ) {
                return null;
            } else if (hits.getTotalHits() > 1) {
                throw new ElasticServiceException("Found more than one hit (IsFirst is true) in the"
                        + " logical Index for a pid");
            }
            hit = hits.getAt(0).getSourceAsMap();
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }

        String PID  = hit.getOrDefault("pid", "").toString();
        String ID = "";
        String title = hit.getOrDefault("bytitle", "").toString();
        String subtitle = "";
        String placeOfPublish = "";
        int yearOfPublish = ElasticUtils.readYearFromSearchHit(hit);
        String publisher = ElasticUtils.readPublisherFromSearchHit(hit);
        String creator = hit.getOrDefault("bycreator", "").toString();
        String genre = "";
        String label = "";
        String classification = "";
        String copyright = "";
        String license = "";
        String licenseURL = "";
        String owner = "";
        String ownerURL = "";
        boolean isGT = false;
        // TODO: fileTree must be provided as an object it cannot be a string. Models have to be
        //       changed
        FileTree fileTree = null;

        Detail res = new Detail( PID,  ID,  title,  subtitle,  placeOfPublish, yearOfPublish,  publisher,  creator,  genre,  label,  classification,  copyright,  license,  licenseURL,  owner,  ownerURL,  isGT,  fileTree);

        return res;
    }

    /**
     * Execute facet search with elasticsearch
s     *
     * @param searchterm
     * @param limit
     * @param offset
     * @param extended
     * @param isGT
     * @param metadatasearch
     * @param fulltextsearch
     * @param sort
     * @param field
     * @param value
     * @return
     */
    public ResultSet facetSearch(String searchterm, int limit, int offset, boolean extended, boolean isGT,
            boolean metadatasearch, boolean fulltextsearch, String sort, String[] field, String[] value) {
        SearchRequest request = new SearchRequest().indices(LOGICAL_INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        request.source(sourceBuilder);
        BoolQueryBuilder query = null;

        if (metadatasearch) {
            query = QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("metadata", searchterm));
        } else if (fulltextsearch) {
            throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "to be implemented");
        } else {
            throw new HttpClientErrorException(HttpStatus.BAD_REQUEST, ErrMsg.FULL_OR_METASEARCH);
        }

        // Filters:
        if (field != null) {
            Map<String, List<String>> filters = new HashMap<>();
            for (int i = 0; i < field.length; i++) {
                String fieldName = ElasticUtils.getFilternameForField(field[i]);
                filters.putIfAbsent(fieldName, new ArrayList<>());
                filters.get(fieldName).add(value[i]);
            }
            BoolQueryBuilder boolFilter = QueryBuilders.boolQuery();
            for (Entry<String, List<String>> entry : filters.entrySet()) {
                for (String filterValue : entry.getValue()) {
                    boolFilter.should(QueryBuilders.termQuery(entry.getKey(), filterValue));
                }
            }
            query.filter(boolFilter);
        }

        // Facets:
        TermsAggregationBuilder aggregation = AggregationBuilders.terms("Creators")
                .field("bycreator.keyword");
        TermsAggregationBuilder aggregation1 = AggregationBuilders.terms("Titles")
                .field("bytitle.keyword");
        sourceBuilder.aggregation(aggregation);
        sourceBuilder.aggregation(aggregation1);

        try {
            sourceBuilder.query(query);
            sourceBuilder.size(limit);
            sourceBuilder.from(offset);
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            Aggregations aggs = response.getAggregations();

            ResultSet res = ElasticUtils.resultSetFromHits(hits, searchterm, metadatasearch,
                    fulltextsearch, offset, limit);
            res.setFacets(ElasticUtils.aggsToFacets(aggs));
            return res;
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

}
