package ola.hd.longtermstorage.model;

public class Values {
    String value;
    int occurences;

    public Values(String value, int occurences) {
        this.value = value;
        this.occurences = occurences;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getOccurences() {
        return occurences;
    }

    public void setOccurences(int occurences) {
        this.occurences = occurences;
    }
}
