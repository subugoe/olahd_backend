package ola.hd.longtermstorage.test.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletRequest;
import ola.hd.longtermstorage.controller.ExportControllerTest;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.security.core.token.Sha512DigestUtils;

/**
 * Collection of helper-functions for tests
 *
 */
public class OlahdTesttools {

    private OlahdTesttools() {
    }

    public final static String TEST_USER = "testuser";
    public final static String TEST_PW = "testpw";
    public final static String TEST_ROLE = "USER";

    public static final Logger TEST_LOGGER = LoggerFactory.getLogger(OlahdTesttools.class);

    /**
     * Create zip-file-post-request
     *
     * @param file
     * @return
     */
    public static HttpServletRequest createZipUploadRequest(File file) {
        MockMultipartHttpServletRequest res = new MockMultipartHttpServletRequest();
        String name = file.getName();
        String bndry = "!!gc0p4Jq0M2Yt08jU534c0p??";
        res.setContentType("multipart/form-data; boundary=" + bndry);
        try {
            byte[] data = FileUtils.readFileToByteArray(file);
            res.setContent(addBoundary(data, bndry, "application/zip", name));
            res.addFile(new MockMultipartFile(name, name, "application/zip", data));
        } catch (IOException e) {
            throw new RuntimeException("error creating zip-test-request", e);
        }
        return res;
    }

    /**
     * Surrounds byte-data with boundary
     *
     * @param data - byte-data vor request
     * @param bndry - boundary-string
     * @param contentType -
     * @param fName
     * @return
     */
    private static byte[] addBoundary(byte[] data, String bndry, String contentType, String fName){
        String start = new StringBuilder()
                .append("--")
                .append(bndry)
                .append("\r\n Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fName)
                .append("\"\r\nContent-type: ")
                .append(contentType)
                .append("\r\n\r\n")
                .toString();
        String end = new StringBuilder()
                .append("\r\n--")
                .append(bndry)
                .append("--")
                .toString();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(start.getBytes());
            baos.write(data);
            baos.write(end.getBytes());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("error adding boundary to byte-array", e);
        }
    }

    /**
     * Create HttpServletRequest for use in test-functions
     *
     * @return
     */
    public static MockHttpServletRequest getMockRequest() {
        return null;
    }

    /**
     * Creates a minimal ocrdzip in tmp-dir. Currently not completely valid, it's for testing
     * purposes only
     *
     * @return
     */
    public static File createTestOcrdzip() {
        try {
            File res = File.createTempFile("test", ".zip");
            res.deleteOnExit();
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(res));

            List<Pair<String, String>> checksums = new ArrayList<>();

            String fName = "mets.xml";
            String content = "METS-testfile";
            addDataBagitEntry(out, checksums, fName, content);
            out.closeEntry();

            out.putNextEntry(new ZipEntry("bagit.txt"));
            out.write("BagIt-Version: 1.0\n".getBytes());
            out.write("Tag-File-Character-Encoding: UTF-8".getBytes());
            out.closeEntry();

            out.putNextEntry(new ZipEntry("bag-info.txt"));
            out.write(String.format("Bagging-Date: %s\n",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).getBytes());
            out.write("BagIt-Profile-Identifier: https://ocr-d.de/bagit-profile.json\n".getBytes());
            out.write("Ocrd-Identifier: this-is-just-a-test\n".getBytes());
            out.write("DC.title: Just a test\n".getBytes());
            // Ocrd-Base-Version-Checksum is mandatory according to website, but i dont understand
            // purpose and content, so skipping it for now. https://ocr-d.de/en/spec/ocrd_zip
            // out.write("Ocrd-Base-Version-Checksum: \n".getBytes());
            out.write("DC.creator: Made Up\n".getBytes());
            out.closeEntry();

            out.putNextEntry(new ZipEntry("manifest-sha512.txt"));
            for (var pair : checksums) {
                out.write(pair.getLeft().getBytes());
                out.write(" ".getBytes());
                out.write(pair.getRight().getBytes());
                out.write("\n".getBytes());
            }
            out.closeEntry();

            out.close();

            return res;
        } catch (IOException e) {
            throw new RuntimeException("Error creating Test-Ocrd-Zipfile", e);
        }
    }

    private static void addDataBagitEntry(ZipOutputStream zos, List<Pair<String, String>> cSums, String name, String content) throws IOException {
        String path = "data/mets.xml";
        zos.putNextEntry(new ZipEntry(String.format("data/%s", name)));
        zos.write(content.getBytes());
        String checksum = Sha512DigestUtils.shaHex(content);
        cSums.add(new ImmutablePair<>(checksum, path));
    }


    /**
     * Intended for use in {@linkplain ExportControllerTest}, but could be used elsewhere as well.
     * Test-data is stored, but it needs some time to be accessible after insert. This function
     * queries the provided PID several times and sleeps in between
     *
     * @param testPid PID which should be reachable in database
     * @param timeout max wait this seconds for archive. Method finishes after 10 secs in any case
     */
    public static boolean waitForArchive(String pid, ArchiveManagerService archiveManagerService) {
        try {
            Method method = archiveManagerService.getClass()
                .getDeclaredMethod("getArchiveIdFromIdentifier", String.class, String.class);
            method.setAccessible(true);

            int i = 20;
            while (i > 0) {
                Object r = method.invoke(archiveManagerService, pid, "default");
                if (r != null && !r.toString().equals("NOT_FOUND")) {
                    /* It is possible that parts of archive are already available and others not.
                     * Therefore wait another 2 seconds*/
                    Thread.sleep(2000);
                    return true;
                }
                Thread.sleep(1000);
                i--;
            }
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Error in " + OlahdTesttools.class.getSimpleName() +
                    ".waitForArchive()", e);
        }
    }
}