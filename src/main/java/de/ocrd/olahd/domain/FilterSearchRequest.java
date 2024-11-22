package de.ocrd.olahd.domain;

/**
 * Class used to provide filter values as request parameters for filter-search-endpoint
 */
public class FilterSearchRequest {

    private String author;
    private String title;
    private int year;
    private Boolean pages;


    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author) {
        this.author = author;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public int getYear() {
        return year;
    }
    public void setYear(int year) {
        this.year = year;
    }
    public Boolean getPages() {
        return pages;
    }
    public void setPages(Boolean pages) {
        this.pages = pages;
    }
}
