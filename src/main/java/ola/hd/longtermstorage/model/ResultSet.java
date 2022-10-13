package ola.hd.longtermstorage.model;

import java.util.List;

public class ResultSet {

    private int hits;
    private int offset;
    private int limit;
    private boolean metadataSearch;
    private boolean fulltextSearch;
    private String searchTerm;
    private List<HitList> hitlist;
    private List<Facets> facets;

    public ResultSet(String searchTerm, int hits, int offset, int limit, boolean metadataSearch,
            boolean fulltextSearch, List<HitList> hitlist, List<Facets> facets) {
        this.hits = hits;
        this.offset = offset;
        this.limit = limit;
        this.metadataSearch = metadataSearch;
        this.fulltextSearch = fulltextSearch;
        this.searchTerm = searchTerm;
        this.hitlist = hitlist;
        this.facets = facets;
    }

    public int getHits() {
        return hits;
    }

    public void setHits(int hits) {
        this.hits = hits;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isMetadataSearch() {
        return metadataSearch;
    }

    public void setMetadataSearch(boolean metadataSearch) {
        this.metadataSearch = metadataSearch;
    }

    public boolean isFulltextSearch() {
        return fulltextSearch;
    }

    public void setFulltextSearch(boolean fulltextSearch) {
        this.fulltextSearch = fulltextSearch;
    }

    public String getSearchTerm() {
        return searchTerm;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
    }

    public List<HitList> getHitlist() {
        return hitlist;
    }

    public void setHitlist(List<HitList> hitlist) {
        this.hitlist = hitlist;
    }

    public List<Facets> getFacets() {
        return facets;
    }

    public void setFacets(List<Facets> facets) {
        this.facets = facets;
    }

}
