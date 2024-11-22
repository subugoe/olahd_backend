package de.ocrd.olahd.elasticsearch;

import static de.ocrd.olahd.Constants.LOGICAL_INDEX_NAME;

import de.ocrd.olahd.domain.SearchTerms;
import de.ocrd.olahd.exceptions.ElasticServiceException;
import de.ocrd.olahd.model.Detail;
import de.ocrd.olahd.model.HitList;
import de.ocrd.olahd.model.ResultSet;
import java.io.IOException;
import java.util.Map;
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
     * Add information to entries without data.
     *
     * Entries without data can occur when pages an the METS are accidently not part of the <mets:structLink> element.
     * Then they are considered not part of the work and thus the metadata from the mets is not added to them. But as we
     * can consider every page in a ocrdzip part of the work, this metadata is added here via the pid
     *
     * @param res
     */
    private void patchNoDataEntries(ResultSet res) {
        for (HitList hit : res.getHitlist()) {
            if (Boolean.TRUE.equals(hit.getNoData())) {
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

    }

}
