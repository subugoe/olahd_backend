package ola.hd.longtermstorage.model;

public class Facets {
    String name;
    Values values;

    public Facets(String name, Values values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Values getValues() {
        return values;
    }

    public void setValues(Values values) {
        this.values = values;
    }
}
