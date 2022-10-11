package ola.hd.longtermstorage.elasticsearch;

import static ola.hd.longtermstorage.Constants.LOGICAL_INDEX_NAME;
import static ola.hd.longtermstorage.Constants.PHYSICAL_INDEX_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ola.hd.longtermstorage.domain.FilterSearchRequest;
import ola.hd.longtermstorage.elasticsearch.mapping.LogicalEntry;
import ola.hd.longtermstorage.elasticsearch.mapping.PhysicalEntry;
import ola.hd.longtermstorage.exceptions.ElasticServiceException;
import ola.hd.longtermstorage.model.Detail;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;;

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
    private LogicalRepository logicalRepository;
    @Autowired
    private PhysicalRepository physicalRepository;
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
     * Test method for fulltext search
     *
     * TODO: remove if not needed/used somewhere
     * @param text
     * @return
     */
    public List<String> searchFulltext(String text) {
        List<String> result = new ArrayList<>();
        text = text.replaceAll(" ", "%20");
        for (PhysicalEntry entry: physicalRepository.fulltextSearch(text)) {
            /* TODO: Logical-index does not yet contain link to corresponding OCRD-ZIP. This should
             * be returned here instead*/
            result.add(entry.getFilename());
        }
        return result;
    }

    /**
     * search logical index for creator
     *
     * TODO: remove if not needed/used somewhere
     * @param name - name or part of name of creator
     * @return id's of matching entries
     */
    public List<String> searchCreator(String text) {
        List<String> result = new ArrayList<>();
        for (LogicalEntry entry: logicalRepository.findByBycreatorContaining(text)) {
            /* TODO: Logical-index does not yet contain link to corresponding OCRD-ZIP. This should
             * be returned here instead*/
            result.add(entry.getParent().getRecordIdentifier());
        }
        return result;
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

    /**
     * Execute a query with filters
     *
     * @return
     * @throws IOException
     */
    public SearchHits filterSearch(FilterSearchRequest filter) throws IOException {
        SearchRequest request = null;
        if (Boolean.TRUE.equals(filter.getPages())) {
            request = new SearchRequest().indices(LOGICAL_INDEX_NAME, PHYSICAL_INDEX_NAME);
        } else {
            request = new SearchRequest().indices(LOGICAL_INDEX_NAME);
        }

        SearchSourceBuilder builder = new SearchSourceBuilder();
        BoolQueryBuilder query = QueryBuilders.boolQuery();
        if (StringUtils.isNotBlank(filter.getTitle())) {
            query.must(QueryBuilders.matchQuery("bytitle", filter.getTitle()));
        }
        if (StringUtils.isNotBlank(filter.getAuthor())) {
            query.must(QueryBuilders.matchQuery("bycreator", filter.getAuthor()));
        }
        if (filter.getYear() > 0) {
            query.must(QueryBuilders.matchQuery("publish_infos.year_publish", filter.getYear()));
        }
        builder.query(query);
        request.source(builder);

        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            return response.getHits();
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing filter-search request", e);
        }
    }

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
                .must(QueryBuilders.matchQuery("pid", pid))
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
        String title = hit.getOrDefault("bytitle", "").toString();
        String creator = hit.getOrDefault("bycreator", "").toString();
        String publisher = ElasticUtils.readPublisherFromSearchHit(hit);
        Integer year = ElasticUtils.readYearFromSearchHit(hit);
        // TODO: fileTree must be provided as an object it cannot be a string. Models have to be
        //       changed
        String fileTree = "";

        Detail res = new Detail(title, year, creator, publisher, fileTree);
        return res;
    }

}
