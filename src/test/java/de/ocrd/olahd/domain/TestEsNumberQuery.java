package de.ocrd.olahd.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import de.ocrd.olahd.domain.EsNumberQuery;
import org.junit.Test;

public class TestEsNumberQuery {

    @Test
    public void testIt() {
        var res = EsNumberQuery.fromQueryString("10-20");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(Integer.valueOf(20), res.value2);
        assertEquals(EsNumberQuery.Cmp.RANGE, res.cmp);

        res = EsNumberQuery.fromQueryString("<10");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(EsNumberQuery.Cmp.LT, res.cmp);
        assertNull(res.value2);

        res = EsNumberQuery.fromQueryString("<=10");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(EsNumberQuery.Cmp.LTE, res.cmp);
        assertNull(res.value2);

        res = EsNumberQuery.fromQueryString(">10");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(EsNumberQuery.Cmp.GT, res.cmp);
        assertNull(res.value2);

        res = EsNumberQuery.fromQueryString(">=10");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(EsNumberQuery.Cmp.GTE, res.cmp);
        assertNull(res.value2);

        res = EsNumberQuery.fromQueryString("10");
        assertEquals(Integer.valueOf(10), res.value1);
        assertEquals(EsNumberQuery.Cmp.EQ, res.cmp);
        assertNull(res.value2);

        res = EsNumberQuery.fromQueryString("nonNumber");
        assertNull(res);

        res = EsNumberQuery.fromQueryString("10=<20");
        assertNull(res);

        res = EsNumberQuery.fromQueryString("10<");
        assertNull(res);
    }
}
