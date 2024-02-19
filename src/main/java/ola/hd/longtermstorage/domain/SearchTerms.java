package ola.hd.longtermstorage.domain;

public class SearchTerms {
    private String searchterm;
    private String author;
    private String title;
    private String place;
    private String year;

    public SearchTerms(
        String searchterm, String author, String title,
        String place, String year
    ) {
        super();
        this.searchterm = searchterm;
        this.author = author;
        this.title = title;
        this.place = place;
        this.year = year;
    }

    public String getSearchterm() {
        return searchterm;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getPlace() {
        return place;
    }

    public String getYear() {
        return year;
    }
}
