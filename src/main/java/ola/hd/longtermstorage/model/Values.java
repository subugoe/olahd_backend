package ola.hd.longtermstorage.model;

public class Values {
    String value;
    int occurences;
    boolean limited;

    public Values(String value, int occurences, boolean limited) {
        this.value = value;
        this.occurences = occurences;
        /** This flag indicates if `occurrences` are be higher as specified. ES query contains a size limit for facet
         *  occurrences. */
        this.limited = limited;
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

    public void setOccurences(int occurrences) {
        this.occurences = occurrences;
    }

    public boolean isLimited() {
        return limited;
    }

    public void setLimited(boolean limited) {
        this.limited = limited;
    }
}
