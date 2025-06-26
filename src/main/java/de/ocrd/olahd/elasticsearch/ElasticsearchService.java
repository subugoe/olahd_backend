package de.ocrd.olahd.elasticsearch;

import static de.ocrd.olahd.Constants.LOGICAL_INDEX_NAME;

import de.ocrd.olahd.domain.SearchTerms;
import de.ocrd.olahd.exceptions.ElasticServiceException;
import de.ocrd.olahd.model.Detail;
import de.ocrd.olahd.model.HitList;
import de.ocrd.olahd.model.ResultSet;
import de.ocrd.olahd.utils.Utils;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.ElasticsearchStatusException;
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
    private RestHighLevelClient client;

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
        sourceBuilder.query(
            QueryBuilders.boolQuery().filter(QueryBuilders.termQuery("pid", pid))
                .filter(QueryBuilders.termQuery("IsFirst", true))
        ).size(1);
        sourceBuilder.fetchSource(false);
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            if (hits.getTotalHits() == 1) {
                return hits.getAt(0).getId();
            } else if (hits.getTotalHits() > 1) {
                throw new ElasticServiceException(
                    "Found more than one hit (IsFirst is true) in the logical Index for a pid"
                );
            } else {
                return null;
            }
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

    /**
     * Get logical index-element by pid and with IsFirst = true.
     *
     * After saving, each ocrd-zip is indexed in the logical index. There can be multiple entries
     * for an ocrd-zip but only one of them with the flag IsFirst set to true. Each entry has a pid.
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

        BoolQueryBuilder query = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("pid.keyword", pid))
            .must(QueryBuilders.matchQuery("IsFirst", true));
        sourceBuilder.query(query).size(1);

        Map<String, Object> hit = null;
        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            if (hits.getTotalHits() == 0) {
                return null;
            } else if (hits.getTotalHits() > 1) {
                throw new ElasticServiceException(
                    "Found more than one hit (IsFirst is true) in the logical Index for a pid"
                );
            }
            hit = hits.getAt(0).getSourceAsMap();
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }

        ElasticResponseHelper util = new ElasticResponseHelper();
        return util.fillSearchHitIntoDetail(hit);
    }

    /**
     * Execute facet search with elasticsearch. This is the main functionality for searching
     *
     * @param searchterm     term to be searched for in metadata and/or fulltexts
     * @param limit          number of results in the hitlist
     * @param offset         starting point of the next resultset from search results to support
     *                       pagination
     * @param extended       if false, an initial search is started and no facets or filters are
     *                       applied
     * @param isGT           if true, search only for GT data
     * @param metadatasearch if true, search over the metadata
     * @param fulltextsearch if true, search over the fulltexts
     * @param sort           Defines sorting fields and direction as a comma separated list
     *                       according to the following pattern field|{asc|desc}
     * @param field
     * @param value
     * @return
     */
    public ResultSet facetSearch(
        SearchTerms searchterms, int limit, int offset, boolean extended,
        Boolean isGt, boolean metadatasearch, boolean fulltextsearch, String sort,
        String[] field, String[] value
    ) {

        ElasticQueryHelper util = new ElasticQueryHelper(
            searchterms, limit, offset, extended,
            isGt, metadatasearch, fulltextsearch, sort, field, value
        );
        SearchRequest request = util.createSearchRequest();

        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            ElasticResponseHelper util2 = new ElasticResponseHelper();
            ResultSet res = util2.responseToResultSet(
                response, searchterms, metadatasearch, fulltextsearch, offset, limit
            );
            patchNoDataEntries(res);
            return res;
        } catch (IOException | ElasticsearchStatusException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

    /**
     * This is the newer version of the facet search. This is the main functionality for searching.
     *
     * Previously it was a grouped search over both, logical and physical Index. Now for the metadata it searches only
     * in the logical Index and only in entries having `IsFirst = true`. For the fulltext-search the physical index is
     * queried first and then the resulting pids are used to filter the logical-IsFirst-Entries.
     *
     * @param searchterm     term to be searched for in metadata and/or fulltexts
     * @param limit          number of results in the hitlist
     * @param offset         starting point of the next resultset from search results to support
     *                       pagination
     * @param extended       if false, an initial search is started and no facets or filters are
     *                       applied
     * @param isGT           if true, search only for GT data
     * @param metadatasearch if true, search over the metadata
     * @param fulltextsearch if true, search over the fulltexts
     * @param sort           Defines sorting fields and direction as a comma separated list
     *                       according to the following pattern field|{asc|desc}
     * @param field
     * @param value
     * @return
     */
    public ResultSet facetSearchV2(
        SearchTerms searchterms, int limit, int offset, boolean extended,
        Boolean isGt, boolean metadatasearch, boolean fulltextsearch, String sort,
        String[] field, String[] value
    ) {

        // Get the pids matching the searchterm in its fulltext
        Set<String> fulltextPids = null;
        if (fulltextsearch && StringUtils.isNotBlank(searchterms.getSearchterm())) {
            fulltextPids = this.queryFulltextPids(searchterms.getSearchterm());
        }

        // Create the search query
        ElasticQueryHelperV2 util = new ElasticQueryHelperV2(
            searchterms, limit, offset, extended,
            isGt, metadatasearch, fulltextsearch, sort, field, value,
            fulltextPids
        );
        SearchRequest request = util.createSearchRequest();

        try {
            // Execute the main search-query
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);

            // Transfer the results into the result-objects
            ElasticResponseHelper util2 = new ElasticResponseHelper();
            ResultSet res = util2.responseToResultSetV2(
                response, searchterms, metadatasearch, fulltextsearch, offset, limit
            );
            return res;
        } catch (IOException | ElasticsearchStatusException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

    /**
     * Add information to entries without data.
     *
     * Entries without data can occur when pages an the METS are accidently not part of the <mets:structLink> element.
     * Then they are considered not part of the work and thus the metadata from the mets is not added to them. But as we
     * can consider every page in a ocrdzip part of the work, this metadata is added here via the pid
     *
     * @param res
     */
    private void patchNoDataEntries(ResultSet res) {
        int counter = 0;
        for (HitList hit : res.getHitlist()) {
            if (Boolean.TRUE.equals(hit.getNoData())) {
                counter += 1;
                String pid = hit.getPid();
                Detail details = null;
                try {
                    details = this.getDetailsForPid(pid);
                } catch (Exception e) {
                    // just pass if details not available
                    continue;
                }
                if (details != null) {
                    hit.setTitle(details.getTitle());
                    hit.setPublisher(details.getPublisher());
                    hit.setPlaceOfPublish(details.getPlaceOfPublish());
                    hit.setYearOfPublish(details.getYearOfPublish());
                    hit.setSubtitle(details.getSubtitle());
                    hit.setCreator(details.getCreator());
                }
            }
        }
        if (counter > 0) {
            // TODO: after changing the query (simplify) this whole function might not be needed any longer. If there
            // are no logs, function and it's invocation should be deleted
            Utils.logDebug("Patched %d hits without data", counter);
        }
    }

    /**
     * Query the phys index and get all pids containing the searchterm in their fulltexts
     *
     * @param searchterm
     * @return
     */
    private Set<String> queryFulltextPids(String searchterm) {
        SearchRequest request = new SearchRequest().indices(de.ocrd.olahd.Constants.PHYSICAL_INDEX_NAME);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        request.source(sourceBuilder);

        BoolQueryBuilder query = QueryBuilders.boolQuery()
            .must(QueryBuilders.matchQuery("fulltext", searchterm));
        sourceBuilder.query(query);
        sourceBuilder.size(9999);
        sourceBuilder.fetchSource("pid", null);

        try {
            SearchResponse response = client.search(request, RequestOptions.DEFAULT);
            SearchHits hits = response.getHits();
            return StreamSupport.stream(hits.spliterator(), false)
                .map(hit -> hit.getSourceAsMap().get("pid"))
                .filter(pid -> pid != null && StringUtils.isNotBlank(pid.toString()))
                .map(Object::toString)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new ElasticServiceException("Error executing search request", e);
        }
    }

}
