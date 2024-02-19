package ola.hd.longtermstorage.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.util.List;
import ola.hd.longtermstorage.domain.Archive;
import ola.hd.longtermstorage.domain.ArchiveResponse;
import ola.hd.longtermstorage.domain.SearchTerms;
import ola.hd.longtermstorage.elasticsearch.ElasticQueryHelper;
import ola.hd.longtermstorage.elasticsearch.ElasticsearchService;
import ola.hd.longtermstorage.model.Detail;
import ola.hd.longtermstorage.model.ResultSet;
import ola.hd.longtermstorage.msg.ErrMsg;
import ola.hd.longtermstorage.repository.mongo.ArchiveRepository;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringArrayPropertyEditor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

@Api(description = "This endpoint is used to search in the system.")
@RestController
public class SearchController {

    private final ArchiveManagerService archiveManagerService;
    private final ArchiveRepository archiveRepository;
    private final ElasticsearchService elasticsearchService;

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    public SearchController(
        ArchiveManagerService archiveManagerService, ArchiveRepository archiveRepository,
        ElasticsearchService elasticsearchService
    ) {
        this.archiveManagerService = archiveManagerService;
        this.archiveRepository = archiveRepository;
        this.elasticsearchService = elasticsearchService;
    }

    @ApiOperation(value = "Search for an archive based on its internal (CDStar-) ID or PID.")
    @ApiResponses({ @ApiResponse(code = 200, message = "Search success", response = String.class),
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
        boolean internalId
    ) throws IOException {

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
        boolean internalId
    ) throws IOException {

        // Get the data
        Archive archive = null;
        if (internalId) {
            archive = archiveRepository.findByOnlineIdOrOfflineId(id, id);
        } else {
            archive = archiveRepository.findByPid(id);
        }

        if (archive == null) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
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
     * Fix for using arrays as path parameters. In
     * {@linkplain #search(String, String, int, int, boolean, Boolean, boolean, boolean, String, String[], String[])}
     * string arrays are used which "sometimes" do not work properly when `,` is contained. In this
     * case "sometimes" the comma is interpreted as a value separator. See
     * https://stackoverflow.com/questions/4998748/how-to-prevent-parameter-binding-from-interpreting-commas-in-spring-3-0-5
     *
     * @param binder
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String[].class, new StringArrayPropertyEditor(null));
    }

    /**
     * Search the index
     *
     * @param id
     * @param searchterm
     * @param limit
     * @param offset
     * @param extended       - false: don't apply filters
     * @param isGT
     * @param metadatasearch
     * @param fulltextsearch
     * @param sort
     * @param field
     * @param value
     * @return
     * @throws IOException
     */
    @ApiOperation(value = "Facet Search")
    @ApiResponses({ @ApiResponse(code = 200, message = "Ok") })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
        @RequestParam(required = false) @ApiParam(value = "The PID or the PPN of the work.", required = false)
        String id,
        @RequestParam(required = false) @ApiParam(value = "Search Term", required = false)
        String searchterm,
        @RequestParam(defaultValue = "25") @ApiParam(value = "Number of results in the hitlist from search results")
        int limit,
        @RequestParam(defaultValue = "0") @ApiParam(value = "Starting point of the next resultset from search results to support pagination")
        int offset,
        @RequestParam(defaultValue = "false") @ApiParam(value = "If false, an initial search is started and no facets or filters are applied")
        boolean extended,
        @RequestParam(required = false) @ApiParam(value = "If true, search only for GT data")
        Boolean isGT,
        @RequestParam(defaultValue = "true") @ApiParam(value = "If true, search over the metadata")
        boolean metadatasearch,
        @RequestParam(defaultValue = "false") @ApiParam(value = "If true, search over the fulltexts")
        boolean fulltextsearch,
        @RequestParam(defaultValue = "title|asc") @ApiParam(value = "Defines sorting fields and direction as a comma separated list according to the following pattern field|{asc|desc}")
        String sort,
        @RequestParam(required = false) @ApiParam(value = "Contains the facete names")
        String[] field,
        @RequestParam(required = false) @ApiParam(value = "Contains the facete values")
        String[] value,
        @RequestParam(required = false) @ApiParam(value = "Title", required = false)
        String title,
        @RequestParam(required = false) @ApiParam(value = "Author", required = false)
        String author,
        @RequestParam(required = false) @ApiParam(value = "Place", required = false)
        String place,
        @RequestParam(required = false) @ApiParam(value = "Year", required = false)
        String year
    ) throws IOException {
        if (field != null) {
            if (value == null || field.length != value.length) {
                throw new HttpClientErrorException(
                    HttpStatus.BAD_REQUEST, ErrMsg.FIELD_NOT_EQUALS_VALUE
                );
            }
            for (String x : field) {
                if (!ElasticQueryHelper.FILTER_MAP.containsKey(x)) {
                    throw new HttpClientErrorException(
                        HttpStatus.BAD_REQUEST, ErrMsg.UNKNOWN_FILTER
                    );
                }
            }
        }

        if (StringUtils.isNotBlank(id)) {
            Detail detail = elasticsearchService.getDetailsForPid(id);
            if (detail == null) {
                Archive archive = ArchiveRepository.getLatestVersion(archiveRepository, id);
                // Archive archive = null;
                if (archive != null && archive.getPid() != null) {
                    detail = elasticsearchService.getDetailsForPid(archive.getPid());
                    if (detail == null) {
                        throw new HttpClientErrorException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "No search entry for a PID found which is registered in the mongdb and does not have a "
                                + "next version"
                        );
                    }
                    detail.setInfoForPreviousPid(id);
                } else {
                    throw new HttpClientErrorException(
                        HttpStatus.NOT_FOUND, ErrMsg.RECORD_NOT_FOUND
                    );
                }
            }
            return ResponseEntity.ok(detail);
        } else {
            SearchTerms searchterms = new SearchTerms(searchterm, title, author, place, year);
            ResultSet resultSet = elasticsearchService.facetSearch(
                searchterms, limit, offset, extended, isGT, metadatasearch, fulltextsearch, sort,
                field, value
            );
            return ResponseEntity.ok(resultSet);
        }
    }

}
