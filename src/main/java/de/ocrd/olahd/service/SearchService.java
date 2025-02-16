package de.ocrd.olahd.service;

import de.ocrd.olahd.domain.SearchRequest;
import de.ocrd.olahd.domain.SearchResults;
import java.io.IOException;

public interface SearchService {

    /**
     * Used to perform search on the storage
     * @param searchRequest And instance of the {@link SearchRequest} class.
     * @return              The search result.
     * @throws IOException  Thrown when there is a problem with the search.
     */
    SearchResults search(SearchRequest searchRequest) throws IOException;
}
