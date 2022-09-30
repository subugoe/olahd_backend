package ola.hd.longtermstorage.model;

public class FulltextSnippets {
    private String value;
    private int page;

    public FulltextSnippets(String value, int page) {
        this.value = value;
        this.page = page;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }
}
