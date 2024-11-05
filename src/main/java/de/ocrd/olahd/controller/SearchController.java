package de.ocrd.olahd.controller;

import de.ocrd.olahd.Constants;
import de.ocrd.olahd.domain.Archive;
import de.ocrd.olahd.domain.ArchiveResponse;
import de.ocrd.olahd.domain.ResponseMessage;
import de.ocrd.olahd.domain.SearchTerms;
import de.ocrd.olahd.elasticsearch.ElasticQueryHelper;
import de.ocrd.olahd.elasticsearch.ElasticsearchService;
import de.ocrd.olahd.model.Detail;
import de.ocrd.olahd.model.ResultSet;
import de.ocrd.olahd.msg.ErrMsg;
import de.ocrd.olahd.repository.mongo.ArchiveRepository;
import de.ocrd.olahd.service.ArchiveManagerService;
import de.ocrd.olahd.utils.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
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
    @ApiResponses({
        @ApiResponse(code = 200, message = "Ok"),
        @ApiResponse(code = 400, message = "Invalid search parameters", response = String.class),
        @ApiResponse(code = 404, message = "Parameter id is provided, but no matching archive was found", response = String.class)
    })
    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(
        @RequestParam(required = false) @ApiParam(value = "The PID or the PPN of the work. If provided, information about the archive is returned", required = false)
        String id,
        @RequestParam(required = false) @ApiParam(value = "Search Term", required = false)
        String searchterm,
        @RequestParam(defaultValue = "25") @ApiParam(value = "Limt the number of results in the hitlist. To support pagination.")
        int limit,
        @RequestParam(defaultValue = "0") @ApiParam(value = "Skip results first x results. To support pagination")
        int offset,
//        @RequestParam(defaultValue = "false") @ApiParam(value = "If false, an initial search is started and no facets or filters are applied")
//        boolean extended,
        @RequestParam(required = false) @ApiParam(value = "If true, search only for GT data")
        Boolean isGT,
        @RequestParam(defaultValue = "true") @ApiParam(value = "If true, search over the metadata")
        boolean metadatasearch,
        @RequestParam(defaultValue = "false") @ApiParam(value = "If true, search over the fulltexts")
        boolean fulltextsearch,
//        @RequestParam(defaultValue = "title|asc") @ApiParam(value = "Defines sorting fields and direction as a comma separated list according to the following pattern field|{asc|desc}")
//        String sort,
        @RequestParam(required = false) @ApiParam(value = "List of facet names used in the search. Used to narrow down the results")
        String[] field,
        @RequestParam(required = false) @ApiParam(value = "List of facet values according to the facet names. Used to narrow down the results")
        String[] value,
        @RequestParam(required = false) @ApiParam(value = "Title filter", required = false)
        String title,
        @RequestParam(required = false) @ApiParam(value = "Author filter", required = false)
        String author,
        @RequestParam(required = false) @ApiParam(value = "Place filter", required = false)
        String place,
        @RequestParam(required = false) @ApiParam(value = "Year filter", required = false)
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
                Archive archive = archiveRepository.getLatestVersion(id);
                // Archive archive = null;
                if (archive != null && archive.getPid() != null) {
                    detail = elasticsearchService.getDetailsForPid(archive.getPid());
                    if (detail == null) {
                        // This happens, when a PID is found, but elasticsearch does not have an entry for it. This is
                        // somehow expected if the PID is a previous version, but not if it is the newest of a work.
                        // This can also happen if elasticsearch has entries for the pid, but none with isFirst == true
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
            SearchTerms searchterms = new SearchTerms(searchterm, author, title, place, year);
            ResultSet resultSet = elasticsearchService.facetSearch(
                searchterms, limit, offset, false, isGT, metadatasearch, fulltextsearch, null,
                field, value
            );
            return ResponseEntity.ok(resultSet);
        }
    }
    @ApiOperation(value = "Returns the latest PID for an Ocrd-Identifier")
    @ApiResponses({ @ApiResponse(code = 200, message = "PID for Ocrd-Identifier found", response = String.class),
        @ApiResponse(code = 404, message = "Ocrd-Identifier not found", response = String.class)
    })
    @GetMapping(value = "/search-ocrdid", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchNewestOcrdIdentifier(
        @RequestParam @ApiParam(value = "Ocrd-Identifier", required = true)
        String id
    ) throws IOException {
        Archive archive = archiveRepository.findTopByOcrdIdentifierOrderByCreatedAtDesc(id);
        if (archive != null && archive.getPid() != null) {
            return ResponseEntity.ok(archive.getPid());
        } else {
            throw new HttpClientErrorException(
                HttpStatus.NOT_FOUND, ErrMsg.OCRD_IDENTIFIER_NOT_FOUND
            );
        }
    }

    /**
     * Get indexing info for a pid
     *
     * This info is stored in bag-info and in the mongodb. It contains for example Ocrd-Work-Identifier,
     * Olahd-Search-Image-Filegrp and Olahd-GT from bag-info.txt. And Olahd-Search-Prev-PID which can be provided
     * through bag-info.txt as well or as a form-parameter (form parameter wins). It is stored in the mongodb.
     *
     * @param id   PID or PPA
     * @param path of file relative to the data-folder
     * @return file from archive's
     * @throws IOException
     */
    @ApiOperation(value = "Provides info used for indexing and re-indexing")
    @ApiResponses({ @ApiResponse(code = 200, message = "Info is available", response = String.class),
        @ApiResponse(code = 404, message = "An archive with the specified identifier is not available in online storage.", response = ResponseMessage.class)
    })
    @GetMapping(value = "/search/indexer-info", produces = { MediaType.TEXT_PLAIN_VALUE })
    public ResponseEntity<String> indexerInfo(
        @ApiParam(value = "The PID/PPA of the work.", required = true) @RequestParam
        String pid
    ) throws IOException {
        if (pid.isBlank()) {
            throw new HttpClientErrorException(
                HttpStatus.UNPROCESSABLE_ENTITY, ErrMsg.PARAM_ID_IS_EMPTY
            );
        }

        String bagInfo = null;
        try (
            Response response = archiveManagerService.exportFile(pid, Paths.get("data", "../bag-info.txt").toString())
        ) {
            byte[] data = response.body().bytes();
            bagInfo = new String(data, "utf-8");
        } catch (HttpClientErrorException e) {
            if (HttpStatus.NOT_FOUND == e.getStatusCode()) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ID_NOT_FOUND_ONLINE);
            }
            throw e;
        }
        Map<String, String> bagInfoMap = Utils.readBagInfoToMap(bagInfo);

        Archive archive = archiveRepository.findByPid(pid);
        Archive prevArchive = archive.getPreviousVersion();

        if (prevArchive != null) {
            bagInfoMap.put(Constants.BAGINFO_KEY_PREV_PID, prevArchive.getPid());
        }
        return ResponseEntity.ok(Utils.writeBagInfoMapToString(bagInfoMap));
    }
}
