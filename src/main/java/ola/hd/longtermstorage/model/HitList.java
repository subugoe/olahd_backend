package ola.hd.longtermstorage.model;

public class HitList {
    private String PID;
    private String ID;
    private String title;
    private String subtitle;
    private String placeOfPublish;
    private int yearOfPublish;
    private String publisher;
    private String creator;
    private FulltextSnippets fulltextSnippets;

    public HitList(String PID, String ID, String title, String subtitle, String placeOfPublish, int yearOfPublish,
            String publisher, String creator, FulltextSnippets fulltextSnippets) {
        this.PID = PID;
        this.ID = ID;
        this.title = title;
        this.subtitle = subtitle;
        this.placeOfPublish = placeOfPublish;
        this.yearOfPublish = yearOfPublish;
        this.publisher = publisher;
        this.creator = creator;
        this.fulltextSnippets = fulltextSnippets;
    }

    public String getPID() {
        return PID;
    }

    public void setPID(String PID) {
        this.PID = PID;
    }

    public String getID() {
        return ID;
    }

    public void setID(String ID) {
        this.ID = ID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getPlaceOfPublish() {
        return placeOfPublish;
    }

    public void setPlaceOfPublish(String placeOfPublish) {
        this.placeOfPublish = placeOfPublish;
    }

    public int getYearOfPublish() {
        return yearOfPublish;
    }

    public void setYearOfPublish(int yearOfPublish) {
        this.yearOfPublish = yearOfPublish;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public FulltextSnippets getFulltextSnippets() {
        return fulltextSnippets;
    }

    public void setFulltextSnippets(FulltextSnippets fulltextSnippets) {
        this.fulltextSnippets = fulltextSnippets;
    }

}
