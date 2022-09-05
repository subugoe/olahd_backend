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
import ola.hd.longtermstorage.domain.SearchHit;
import ola.hd.longtermstorage.domain.SearchHitDetail;
import ola.hd.longtermstorage.domain.SearchRequest;
import ola.hd.longtermstorage.domain.SearchResults;
import ola.hd.longtermstorage.elasticsearch.ElasticsearchService;
import ola.hd.longtermstorage.repository.mongo.ArchiveRepository;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ResponseEntity<?> search(@ApiParam(value = "The query used to search.", required = true)
                                    @RequestParam(name = "q")
                                            String query,
                                    @ApiParam(value = "Max returned results.")
                                    @RequestParam(defaultValue = "25")
                                            int limit,
                                    @ApiParam(value = "Scroll ID for pagination")
                                    @RequestParam(defaultValue = "")
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
            @ApiParam(value = "PID or internal ID of the archive.", required = true)
            @RequestParam String id,
            @ApiParam(value = "An option to include all files in return.")
            @RequestParam(defaultValue = "false") boolean withFile,
            @ApiParam(value = "How many files should be returned?")
            @RequestParam(defaultValue = "1000") int limit,
            @ApiParam(value = "How many files should be skipped from the beginning?")
            @RequestParam(defaultValue = "0") int offset,
            @ApiParam(value = "Is this an internal (CDStar-ID) or not (PID, PPN).", required = true)
            @RequestParam(defaultValue = "false") boolean internalId) throws IOException {

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
            @ApiParam(value = "PID or internal ID of the archive.", required = true)
            @RequestParam String id,
            @ApiParam(value = "Is this an internal (CDStar-ID) or not (PID, PPN).", required = true)
            @RequestParam(defaultValue = "false") boolean internalId) {


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

        // Set previous version
        Archive prevArchive = archive.getPreviousVersion();
        if (prevArchive != null) {
            ArchiveResponse prevRes = new ArchiveResponse();
            prevRes.setPid(prevArchive.getPid());
            prevRes.setOnlineId(prevArchive.getOnlineId());
            prevRes.setOfflineId(prevArchive.getOfflineId());

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

                response.addNextVersion(nextRes);
            }
        }

        return ResponseEntity.ok(response);
    }
    /**
     * curl -X GET "http://localhost:8080/search-es/creator?text=eiffer"
     *
     * @param text
     * @return
     * @throws IOException
     */
    @ApiResponses({
            @ApiResponse(code = 200, message = "Search success", response = SearchResults.class)
    })
    @GetMapping(value = "/search-es/creator", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchCreator(@RequestParam(name = "text") String text) throws IOException {

        List<String> hits = elasticsearchService.searchCreator(text);

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(hits);

        return ResponseEntity.ok(jsonString);
    }

    /**
     * `curl -X GET "http://localhost:8080/search-es/fulltext?text=owing%20to%20Lemma"`
     *
     * @param text
     * @return
     * @throws IOException
     */
    @ApiResponses({
        @ApiResponse(code = 200, message = "Search success", response = SearchResults.class)
    })
    @GetMapping(value = "/search-es/fulltext", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@RequestParam(name = "text") String text) throws IOException {

        List<String> hits = elasticsearchService.searchFulltext(text);

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(hits);

        return ResponseEntity.ok(jsonString);
    }

    /**
     * `curl "localhost:8080/search-es/query-all?q=berlin&from=0&size=1" | jq`
     *
     * @param text
     * @return
     * @throws IOException
     */
    @ApiResponses({
        @ApiResponse(code = 200, message = "Search success", response = SearchResults.class)
    })
    @GetMapping(value = "/search-es/query-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchEsQuery(
            @RequestParam(name = "q") String query,
            @RequestParam(name = "from") int from,
            @RequestParam(name = "size") int size) throws IOException {

        Object hits = elasticsearchService.bigQuery(query, from, size);

        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(hits)
            .replaceAll("\"sourceAsMap\"\\s*:", "\"_source\":")
            .replaceAll("\"id\"\\s*:", "\"_id\":")
            .replaceAll("\"index\"\\s*:", "\"_index\":");
        return ResponseEntity.ok(jsonString);
    }

    /**
     * `curl "http://localhost:8080/search-es/meta.phys/EBBlgIEBI6n_xy-wUbKr" | jq`
     *
     * @param index
     * @param id
     * @return
     * @throws IOException
     */
    @ApiResponses({
        @ApiResponse(code = 200, message = "Search success", response = SearchResults.class)
    })
    @GetMapping(value = "/search-es/{index:" + LOGICAL_INDEX_NAME + "|" + PHYSICAL_INDEX_NAME
            + "}/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchEsElementById(@PathVariable String index, @PathVariable String id)
            throws IOException {
        Map<String, Object> res = elasticsearchService.getElement(index, id);
        if (res == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND);
        }
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(res);
        return ResponseEntity.ok(jsonString);
    }
}
