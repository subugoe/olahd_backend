package ola.hd.longtermstorage.model;

import java.util.List;
import ola.hd.longtermstorage.domain.SearchTerms;

public class ResultSet {

    private int hits;
    private int offset;
    private int limit;
    private boolean metadataSearch;
    private boolean fulltextSearch;
    private String searchTerm;
    private String title;
    private String author;
    private String place;
    private String year;
    private List<HitList> hitlist;
    private List<Facets> facets;

    public ResultSet(
        String searchTerm, int hits, int offset, int limit, boolean metadataSearch,
        boolean fulltextSearch, List<HitList> hitlist, List<Facets> facets
    ) {
        this.hits = hits;
        this.offset = offset;
        this.limit = limit;
        this.metadataSearch = metadataSearch;
        this.fulltextSearch = fulltextSearch;
        this.searchTerm = searchTerm;
        this.hitlist = hitlist;
        this.facets = facets;
    }

    public ResultSet() {
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public void setSearchTerms(SearchTerms searchterms) {
        this.searchTerm = searchterms.getSearchterm();
        this.title = searchterms.getTitle();
        this.author = searchterms.getAuthor();
        this.place = searchterms.getPlace();
        this.year = searchterms.getYear();
    }

}
