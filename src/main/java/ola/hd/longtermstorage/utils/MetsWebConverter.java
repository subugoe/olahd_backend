package ola.hd.longtermstorage.utils;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import ola.hd.longtermstorage.controller.ExportController;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

/**
 * This class offers functionality to convert the file-locations containing in a Metsfile to
 * web-accessible URL's.
 * Example:
 * <code>
 * <mets:fileGrp USE="OCR-D-IMG">
 *   <mets:file MIMETYPE="image/jpeg" ID="OCR-D-IMG_0001">
 *     <mets:FLocat LOCTYPE="URL" xlink:href="OCR-D-IMG/OCR-D-IMG_0001.jpg"/>
 * </code>
 * will be converted to something like:
 * <code>
 * <mets:fileGrp USE="OCR-D-IMG">
 *   <mets:file MIMETYPE="image/jpeg" ID="OCR-D-IMG_0001">
 *     <mets:FLocat LOCTYPE="URL" xlink:href="http://ola-hd.ocr-d.de/api/export/file?id=21.T11998/0000-001C-9CBE-D&amp;path=OCR-D-IMG/OCR-D-IMG_0001.jpg" />
 * </code>
 */
public class MetsWebConverter {

    /** Path to where the files are available */
    private static final String PREFIX_IMAGE_EXPORT;
    private static final String PREFIX_TIFF_TO_JPEG;

    private static final Namespace NS_METS = Namespace.getNamespace("http://www.loc.gov/METS/");
    private static final Namespace NS_XLINK = Namespace.getNamespace("http://www.w3.org/1999/xlink");

    /** Pattern to get the value of query-param "path" from a link in a METS file */
    private static final Pattern METS_LINK_PATH_PATTERN = Pattern.compile("[\\?&]path=([^&#]*)");

    static {
        try {
            URI uri1 = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ExportController.class).exportFile("PARAM", "PARAM")
            ).toUri();
            String path1 = uri1.isAbsolute() ? uri1.toURL().getFile() : uri1.toString();
            PREFIX_IMAGE_EXPORT = "%s/api" + path1.replaceAll("PARAM", "%s");

            URI uri2 = WebMvcLinkBuilder.linkTo(
                WebMvcLinkBuilder.methodOn(ExportController.class).exportTiffAsJpegFile("PARAM", "PARAM")
            ).toUri();
            String path2 = uri2.isAbsolute() ? uri2.toURL().getFile() : uri2.toString();
            PREFIX_TIFF_TO_JPEG = "%s/api" + path2.replaceAll("PARAM", "%s");
        } catch (IOException e) {
            // I think the Exception cannot be thrown the way I use the WebmvcLinkBuilder (only generating the urls)
            throw new RuntimeException(e);
        }
    }

    private MetsWebConverter() {
    }

    /**
     * Change file refs of file in mets from FILE to URL to make the files web-accessible
     *
     *
     * @param pid - PID belonging to the Ocrd-zip of the Metsfile
     * @param ins - InputStream containing the original Metsfile
     * @param outs - Where to write the converted Metsfile to
     * @throws IOException
     * @throws JDOMException
     * @throws Exception
     */
    public static void convertMets(String pid, String host, InputStream ins, OutputStream outs) throws JDOMException, IOException {
        SAXBuilder sax = new SAXBuilder();
        Document doc = sax.build(ins);
        Element rootNode = doc.getRootElement();

        Namespace nsMets = Namespace.getNamespace("http://www.loc.gov/METS/");
        List<Element> listFileSec = rootNode.getChildren("fileSec", nsMets);
        List<Element> listFileGrp = listFileSec.get(0).getChildren("fileGrp", nsMets);

        replaceFileWithUrl(listFileGrp, host, pid);
        addDefaultGroup(listFileGrp);
        changeTiffLinks(listFileGrp, host, pid);

        XMLOutputter xmlOutput = new XMLOutputter();
        Format format = Format.getPrettyFormat();
        format.setIndent("   ");
        xmlOutput.setFormat(format);
        xmlOutput.output(doc, outs);
    }

    /**
     * Change all links in DEFAULT file-group to use tiff-jpeg convert endpoint
     *
     * @param listFileGrp
     */
    private static void changeTiffLinks(List<Element> listFileGrp, String host, String pid) {
        for (Element e : listFileGrp) {
            if ("DEFAULT".equals(e.getAttributeValue("USE"))) {
                for (Element e2 : e.getChildren("file", NS_METS)) {
                    for (Element e3 : e2.getChildren("FLocat", NS_METS)) {
                        String lt = e3.getAttributeValue("LOCTYPE");
                        if ("URL".equals(lt)) {
                            String link = e3.getAttributeValue("href", NS_XLINK);
                            Matcher matcher = METS_LINK_PATH_PATTERN.matcher(link);
                            if (matcher.find()){
                                String path = matcher.group(1);
                                if (path.endsWith(".tif") || path.endsWith(".tiff")) {
                                    e3.setAttribute(
                                        "href", String.format(PREFIX_TIFF_TO_JPEG, host, pid, path),NS_XLINK
                                    );
                                }
                            }
                        }
                    }
                }
                // only default file-grp should be adapted
                break;
            }
        }

    }

    /**
     * If DEFAULT file-group is missing rename the image file-group to default
     *
     * Change group-name of image group to DEFAULT so that DFG-Viewer can display the images
     *
     * @param listFileGrp
     */
    private static void addDefaultGroup(List<Element> listFileGrp) {
        List<String> fileGrpStr = readFileGroups(listFileGrp);

        if (!fileGrpStr.contains("DEFAULT")) {
            if (fileGrpStr.contains("OCR-D-IMG")) {
                for (Element e : listFileGrp) {
                    if (e.getAttributeValue("USE").equals("OCR-D-IMG")) {
                        e.setAttribute("USE", "DEFAULT");
                    }
                }
            }
        }
    }

    private static List<String> readFileGroups(List<Element> listFileGrp) {
        List<String> res = new ArrayList<>(listFileGrp.size());
        for (Element e : listFileGrp) {
            res.add(e.getAttributeValue("USE"));
        }
        return res;
    }

    /**
     * Replace FILE-links with URL-links
     *
     */
    private static void replaceFileWithUrl(List<Element> listFileGrp, String host, String pid) {
        for (Element e : listFileGrp) {
            for (Element e2 : e.getChildren("file", NS_METS)) {
                for (Element e3 : e2.getChildren("FLocat", NS_METS)) {
                    String otherLt = e3.getAttributeValue("OTHERLOCTYPE");
                    if (otherLt != null && otherLt.equals("FILE")) {
                        String path = e3.getAttributeValue("href", NS_XLINK);
                        if (!path.startsWith("http") && !path.startsWith("/")) {
                            e3.setAttribute("href", String.format(PREFIX_IMAGE_EXPORT, host, pid, path), NS_XLINK);
                            e3.setAttribute("LOCTYPE", "URL");
                            e3.removeAttribute("OTHERLOCTYPE");
                        }
                    }
                }
            }
        }
    }

    /**
     * Take a tiff-inputstream and give back a jpeg-outputstream
     *
     * @param inputStream
     * @param outputStream
     * @throws IOException
     */
    public static void convertTifToJpg(InputStream inputStream, OutputStream outputStream) throws IOException{
        BufferedImage tiff = ImageIO.read(inputStream);
        ImageIO.write(tiff, "JPEG", outputStream);
    }
}
