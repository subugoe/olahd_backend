package de.ocrd.olahd.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to help building a number query in Elasticsearch
 */
public class EsNumberQuery {

    public final Cmp cmp;
    public final Integer value1;
    public final Integer value2;

    private EsNumberQuery(Cmp cmp, Integer value1, Integer value2) {
        super();
        this.cmp = cmp;
        this.value1 = value1;
        this.value2 = value2;
    }

    private static Pattern eqPat = Pattern.compile("^[0-9]+$");
    private static Pattern rangePat = Pattern.compile("^[0-9]+[\\s]*-[\\s]*[0-9]+$");
    private static Pattern ltPat = Pattern.compile("^[\\s]*<[\\s]*[0-9]+$");
    private static Pattern ltePat = Pattern.compile("^[\\s]*<=[\\s]*[0-9]+$");
    private static Pattern gtPat = Pattern.compile("^[\\s]*>[\\s]*[0-9]+$");
    private static Pattern gtePat = Pattern.compile("^[\\s]*>=[\\s]*[0-9]+$");

    public static EsNumberQuery fromQueryString(String queryStr) {
        var str = queryStr.trim();
        Cmp cmp = null;
        Integer value2 = null;
        if (eqPat.matcher(str).matches()) {
            return new EsNumberQuery(Cmp.EQ, Integer.valueOf(str), null);
        } else if (rangePat.matcher(str).matches()) {
            cmp = Cmp.RANGE;
            value2 = parseSecondNumber(str);
        } else if (ltPat.matcher(str).matches()) {
            cmp = Cmp.LT;
        } else if (ltePat.matcher(str).matches()) {
            cmp = Cmp.LTE;
        } else if (gtPat.matcher(str).matches()) {
            cmp = Cmp.GT;
        } else if (gtePat.matcher(str).matches()) {
            cmp = Cmp.GTE;
        } else {
            return null;
        }
        return new EsNumberQuery(cmp, parsefirstNumber(str), value2);
    }

    private static Pattern numberPat = Pattern.compile("\\d+");

    private static Integer parseSecondNumber(String str) {
        Matcher m = numberPat.matcher(str);
        m.find();
        m.find();
        return Integer.valueOf(m.group());
    }

    private static Integer parsefirstNumber(String str) {
        Matcher m = numberPat.matcher(str);
        m.find();
        return Integer.valueOf(m.group());
    }

    public enum Cmp {
        EQ,
        LT,
        LTE,
        GT,
        GTE,
        RANGE
    }
}