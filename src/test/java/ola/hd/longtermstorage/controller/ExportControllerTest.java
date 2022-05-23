package ola.hd.longtermstorage.controller;

import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_LOGGER;
import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_PW;
import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_ROLE;
import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_USER;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.msg.ErrMsg;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.service.PidService;
import ola.hd.longtermstorage.test.tools.OlahdTesttools;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.HttpClientErrorException;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class ExportControllerTest {

    private Logger logger = LoggerFactory.getLogger(ExportControllerTest.class);

    @Autowired
    private ExportController exportController;

    @Autowired
    private ArchiveManagerService archiveManagerService;

    @Autowired
    private PidService pidService;

    @Autowired
    private ImportController importController;


    private static boolean setUpIsDone = false;

    private static String testPid = "";

    @Before
    public void setUp() throws IOException, FileUploadException {
        if (setUpIsDone) {
            return;
        }
        File testzip = OlahdTesttools.createTestOcrdzip();
        HttpServletRequest request = OlahdTesttools.createZipUploadRequest(testzip);
        Principal user = SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> importData = importController.importData(request, user);
        testPid = ((ResponseMessage)importData.getBody()).getPid();
        assertTrue("Setup failed, testPid not received", StringUtils.isNotBlank(testPid));

        logger.info("OCRD-ZIP Test-PID: {}", testPid);
        boolean waitResult = OlahdTesttools.waitForArchive(testPid, archiveManagerService);
        if (!waitResult) {
            TEST_LOGGER.error("Uploading archive to CDStar Demo for Tests failed");
        }
        pidService.deletePid(testPid);
        setUpIsDone = true;
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PW, roles = TEST_ROLE)
    public void testExportMetsfile() throws Exception {
        ResponseEntity<InputStreamResource> res = exportController.exportMetsfile(testPid);
        String text = IOUtils.toString(res.getBody().getInputStream(), "utf-8");
        assertTrue("METS-file request not successful", StringUtils.isNotBlank(text));
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PW, roles = TEST_ROLE)
    public void testExportFileWithNonExistingArchive() throws Exception {
        Exception exception = assertThrows(HttpClientErrorException.class, () -> {
            exportController.exportFile("not-existing", "irrelevant/path");
        });
        assertTrue(exception.getClass().equals(HttpClientErrorException.class));
        String expectedMsg = "404 " + ErrMsg.ARCHIVE_NOT_FOUND;
        assertTrue(exception.getMessage().equals(expectedMsg));
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PW, roles = TEST_ROLE)
    public void testExportFileWithNonExistingFile() throws Exception {
        Exception exception = assertThrows(HttpClientErrorException.class, () -> {
            exportController.exportFile(testPid, "non/existing/file");
        });
        assertTrue(exception.getClass().equals(HttpClientErrorException.class));
        String expectedMsg = "404 " + ErrMsg.FILE_NOT_FOUND;
        assertTrue(exception.getMessage().equals(expectedMsg));
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PW, roles = TEST_ROLE)
    public void testExportFile() throws Exception {
        String filepath = "/mets.xml";
        ResponseEntity<InputStreamResource> res = exportController.exportFile(testPid, filepath);
        String text = IOUtils.toString(res.getBody().getInputStream(), "utf-8");
        assertTrue("ExportController.exportFile() failed", res.getStatusCode().is2xxSuccessful());
        assertTrue("Exported file should not be empty", StringUtils.isNotBlank(text));
    }
}
