package ola.hd.longtermstorage.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

public class TestMetsWebConverter {

    public static void main(String[] args) throws Exception {
        new TestMetsWebConverter().convertMetsLinks();

    }


    @Test
    public void convertMetsLinks() throws Exception {
        // Arrange
        String host = "http://dummy-host";
        ByteArrayOutputStream metsWeb = new ByteArrayOutputStream();
        InputStream metsInput = getTestMetsfile();

        // Act
        MetsWebConverter.convertMets("dummy-pid", host, metsInput, metsWeb);

        // Assert
        String metsWebString = new String(metsWeb.toByteArray(), "utf-8");
        List<FlocatInMets> x = getMetsFilegrps(metsWebString);
        for (FlocatInMets flocat : x) {
            assertNotEquals(flocat.otherloctype, "FILE", String.format("'FILE' still present: %s", flocat));
            if (flocat.grp.equals("DEFAULT")) {
                assertTrue(flocat.link.startsWith("http"), String.format("Wrong link: %s", flocat.link));
            } else {
                assertTrue(flocat.link.startsWith(host), String.format("Wrong link: %s", flocat.link));
            }
        }
    }

    @Test
    public void convertMetsGroup() throws Exception {
        // Arrange
        String host = "http://dummy-host";
        ByteArrayOutputStream metsWeb = new ByteArrayOutputStream();
        InputStream metsInput = getTestMetsfile2();

        // Act
        MetsWebConverter.convertMets("dummy-pid", host, metsInput, metsWeb);

        // Assert
        String metsWebString = new String(metsWeb.toByteArray(), "utf-8");
        List<FlocatInMets> x = getMetsFilegrps(metsWebString);

        List<FlocatInMets> defGroup = x.stream().filter(f -> f.grp.equals("DEFAULT")).collect(Collectors.toList());
        assertFalse(defGroup.isEmpty(), "filegrp DEFAULT should be present");
    }


    /**
     * Read the test METS-file from resource folder to an InputStream
     *
     * @return
     * @throws IOException
     */
    private static InputStream getTestMetsfile() throws IOException {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("test-mets.xml");
        return resource.getInputStream();
    }

    /**
     * Read the second test METS-file from resource folder to an InputStream
     *
     * This Mets file does not contain the DEFAULT filegrp. It contains filegrp OCR-D-IMG
     *
     * @return
     * @throws IOException
     */
    private static InputStream getTestMetsfile2() throws IOException {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource("test-mets2.xml");
        return resource.getInputStream();
    }

    /**
     * Read metsfile and return list of contained files as {@linkplain FlocatInMets}
     *
     * @param metsfile: content of metsfile
     * @return
     * @throws Exception
     */
    private static List<FlocatInMets> getMetsFilegrps(String metsfile) throws Exception {
        SAXBuilder sax = new SAXBuilder();
        Document doc = sax.build(new ByteArrayInputStream(metsfile.getBytes("utf-8")));
        Element rootNode = doc.getRootElement();

        Namespace nsMets = Namespace.getNamespace("http://www.loc.gov/METS/");
        Namespace nsXlink = Namespace.getNamespace("http://www.w3.org/1999/xlink");
        List<FlocatInMets> res = new ArrayList<>();
        List<Element> listFileSec = rootNode.getChildren("fileSec", nsMets);
        List<Element> listFileGrp = listFileSec.get(0).getChildren("fileGrp", nsMets);
        for (Element e : listFileGrp) {
            for (Element e2 : e.getChildren("file", nsMets)) {
                for (Element e3 : e2.getChildren("FLocat", nsMets)) {
                    var x = new FlocatInMets();
                    res.add(x);
                    x.grp = e.getAttributeValue("USE");
                    x.loctype = e3.getAttributeValue("LOCTYPE");
                    x.otherloctype = e3.getAttributeValue("OTHERLOCTYPE");
                    x.link = e3.getAttributeValue("href", nsXlink);
                }
            }
        }
        return res;
    }

    /** Class to wrap mets:FLocat of Metsfile */
    private static class FlocatInMets {
        String grp;
        String loctype;
        String otherloctype;
        String link;

        @Override
        public String toString() {
            return String.format("{grp: %s, loctype: %s, ol: %s, link: %s}", grp, loctype, otherloctype, link);
        }
    }
}
