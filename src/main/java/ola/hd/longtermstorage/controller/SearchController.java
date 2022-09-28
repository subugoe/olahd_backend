package ola.hd.longtermstorage.controller;

import static ola.hd.longtermstorage.Constants.LOGICAL_INDEX_NAME;
import static ola.hd.longtermstorage.Constants.PHYSICAL_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import ola.hd.longtermstorage.domain.Archive;
import ola.hd.longtermstorage.domain.ArchiveResponse;
import ola.hd.longtermstorage.domain.FilterSearchRequest;
import ola.hd.longtermstorage.domain.SearchHit;
import ola.hd.longtermstorage.domain.SearchHitDetail;
import ola.hd.longtermstorage.domain.SearchRequest;
import ola.hd.longtermstorage.domain.SearchResults;
import ola.hd.longtermstorage.elasticsearch.ElasticsearchService;
import ola.hd.longtermstorage.repository.mongo.ArchiveRepository;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.service.SearchService;
import org.elasticsearch.search.SearchHits;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@Api(description = "This endpoint is used to search in the system.")
@RestController
public class SearchController {

    private final SearchService searchService;
    private final ArchiveManagerService archiveManagerService;
    private final ArchiveRepository archiveRepository;
    private final ElasticsearchService elasticsearchService;

    @Autowired
    public SearchController(SearchService searchService, ArchiveManagerService archiveManagerService,
                            ArchiveRepository archiveRepository, ElasticsearchService elasticsearchService) {
        this.searchService = searchService;
        this.archiveManagerService = archiveManagerService;
        this.archiveRepository = archiveRepository;
        this.elasticsearchService = elasticsearchService;
    }

    @ApiOperation(value = "Search on archive.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Search success", response = SearchResults.class)
    })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
            @RequestParam(name = "q") @ApiParam(value = "The query used to search.", required = true)
            String query,
            @RequestParam(defaultValue = "25") @ApiParam(value = "Max returned results.", example = "25")
            int limit,
            @RequestParam(defaultValue = "") @ApiParam(value = "Scroll ID for pagination")
            String scroll) throws IOException {

        SearchRequest searchRequest = new SearchRequest(query, limit, scroll);
        SearchResults results = searchService.search(searchRequest);

        ObjectMapper mapper = new ObjectMapper();
        for (SearchHit hit : results.getHits()) {
            String data;

            if (hit.getType().equals("file")) {
                data = new String(archiveManagerService.getFile(hit.getId(), hit.getName(), true, true).getContent());
            } else {
                data = archiveManagerService.getArchiveInfo(hit.getId(), false, 0, 0, true);
            }

            SearchHitDetail detail = mapper.readValue(data, SearchHitDetail.class);
            hit.setDetail(detail);
        }

        return ResponseEntity.ok(results);
    }

    @ApiOperation(value = "Search for an archive based on its internal (CDStar-) ID or PID.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Search success", response = String.class),
            @ApiResponse(code = 404, message = "Archive not found", response = String.class)
    })
    @GetMapping(value = "/search-archive", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchArchive(
            @RequestParam @ApiParam(value = "PID or internal ID of the archive.", required = true)
            String id,
            @RequestParam(defaultValue = "false") @ApiParam(value = "An option to include all files in return.")
            boolean withFile,
            @RequestParam(defaultValue = "1000") @ApiParam(value = "How many files should be returned?", example = "1000")
            int limit,
            @RequestParam(defaultValue = "0") @ApiParam(value = "How many files should be skipped from the beginning?", example = "0")
            int offset,
            @RequestParam(defaultValue = "false") @ApiParam(value = "Is this an internal (CDStar-ID) or not (PID, PPN).", required = true)
            boolean internalId) throws IOException {

        String info = archiveManagerService.getArchiveInfo(id, withFile, limit, offset, internalId);

        return ResponseEntity.ok(info);
    }

    @ApiOperation(value = "Get the information of an archive from the system database.")
    @ApiResponses({
            @ApiResponse(code = 200, message = "Information found", response = String.class),
            @ApiResponse(code = 404, message = "Information not found", response = String.class)
    })
    @GetMapping(value = "/search-archive-info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ArchiveResponse> searchArchiveInfo(
            @RequestParam @ApiParam(value = "PID or internal ID of the archive.", required = true)
            String id,
            @RequestParam(defaultValue = "false") @ApiParam(value = "Is this an internal (CDStar-ID) or not (PID, PPN).", required = true)
            boolean internalId) {

        // Get the data
        Archive archive = null;
        if (internalId) {
            archive = archiveRepository.findByOnlineIdOrOfflineId(id, id);
        } else {
            archive = archiveRepository.findByPid(id);
        }

        // Build the response
        ArchiveResponse response = new ArchiveResponse();

        // Set some data
        response.setPid(archive.getPid());
        response.setOnlineId(archive.getOnlineId());
        response.setOfflineId(archive.getOfflineId());
        response.setLogId(elasticsearchService.getLogIdForPid(archive.getPid()));

        // Set previous version
        Archive prevArchive = archive.getPreviousVersion();
        if (prevArchive != null) {
            ArchiveResponse prevRes = new ArchiveResponse();
            prevRes.setPid(prevArchive.getPid());
            prevRes.setOnlineId(prevArchive.getOnlineId());
            prevRes.setOfflineId(prevArchive.getOfflineId());
            prevRes.setLogId(elasticsearchService.getLogIdForPid(prevArchive.getPid()));

            response.setPreviousVersion(prevRes);
        }

        // Set next versions
        List<Archive> nextVersions = archive.getNextVersions();
        if (nextVersions != null) {
            for (Archive nextArchive : nextVersions) {
                ArchiveResponse nextRes = new ArchiveResponse();
                nextRes.setPid(nextArchive.getPid());
                nextRes.setOnlineId(nextArchive.getOnlineId());
                nextRes.setOfflineId(nextArchive.getOfflineId());
                nextRes.setLogId(elasticsearchService.getLogIdForPid(nextArchive.getPid()));

                response.addNextVersion(nextRes);
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Execute an uri search on both indexes: meta.olahds_log and meta.olahds_phys.
     *
     * This is similar to the prototypes cdstar-search functionality
     *
     * `curl "localhost:8080/search-es/query-all?q=berlin&from=0&size=1" | jq`
     *
     * @param text
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Make an uri search on the logical and physical index")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Search success")
    })
    @GetMapping(value = "/search-es/query-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchEsQuery(
            @RequestParam(name = "q") @ApiParam(value = "The query used to search", required = true)
            String query,
            @RequestParam(name = "from") @ApiParam(value = "Number of hits to skip", required = true, example = "0")
            int from,
            @RequestParam(name = "size") @ApiParam(value = "Maximum number of hits to return", required = true, example = "10")
            int size) throws IOException {

        Object hits = elasticsearchService.bigQuery(query, from, size);

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(hits)
            .replaceAll("\"sourceAsMap\"\\s*:", "\"_source\":")
            .replaceAll("\"id\"\\s*:", "\"_id\":")
            .replaceAll("\"index\"\\s*:", "\"_index\":");
        return ResponseEntity.ok(jsonString);
    }

    /**
     * Get an index entry by its id
     *
     * `curl "http://localhost:8080/search-es/meta.phys/EBBlgIEBI6n_xy-wUbKr" | jq`
     *
     * @param index
     * @param id
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Get index entry by id")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Index entry was found"),
        @ApiResponse(code = 404, message = "Index or entry for id not found")
    })
    @GetMapping(value = "/search-es/{index:" + LOGICAL_INDEX_NAME + "|" + PHYSICAL_INDEX_NAME
            + "}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchEsElementById(
            @PathVariable @ApiParam("The name of the index to query")
            String index,
            @PathVariable @ApiParam("Id of the index entry to fetch")
            String id)
            throws IOException {
        Map<String, Object> res = elasticsearchService.getElement(index, id);
        if (res == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(res);
        return ResponseEntity.ok(jsonString);
    }

    /**
     * Get the id for the logical index by pid
     *
     * @param index
     * @param id
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Get the id of the logical index entry for a pid and IsFirst = true")
    @ApiResponses({
        @ApiResponse(code = 200, message = "index entry for pid was found"),
        @ApiResponse(code = 404, message = "no index entry for pid found")
    })
    @GetMapping(value = "/search-es/logid4pid", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getLogIdForPid(
            @RequestParam@ApiParam(value = "The PID or the PPN of the work.", required = true)
            String pid
            ) throws IOException {
        String docId = elasticsearchService.getLogIdForPid(pid);
        if (docId == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(docId);
        return ResponseEntity.ok(jsonString);
    }

    /**
     * Search with filter functionality
     *
     * XXX: purpose of this function is to give a first draft/template for a filter/facet search.
     *      remove if not longer needed/used in the frontend
     *
     *
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Search index with filters")
    @ApiResponses({
        @ApiResponse(code = 200, message = "Search success")
    })
    @PostMapping(value = "/search-es/filter-search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> filterSearch(@RequestBody FilterSearchRequest filter) throws IOException {
        SearchHits hits = elasticsearchService.filterSearch(filter);
        ObjectMapper mapper = new ObjectMapper();
        /* if elasticsearch is queried via kibana or cmd, the names are _source, _id and _index. So
         * this is changed here for now to be interchangeable while developing*/
        String jsonString = mapper.writeValueAsString(hits)
            .replaceAll("\"sourceAsMap\"\\s*:", "\"_source\":")
            .replaceAll("\"id\"\\s*:", "\"_id\":")
            .replaceAll("\"index\"\\s*:", "\"_index\":");
        return ResponseEntity.ok(jsonString);
    }
}
