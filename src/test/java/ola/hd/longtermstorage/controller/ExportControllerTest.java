package ola.hd.longtermstorage.controller;

import java.io.File;
import java.io.IOException;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.service.ArchiveManagerService;
import ola.hd.longtermstorage.test.tools.OlahdTesttools;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class ExportControllerTest {

    @Autowired
    private ExportController exportController;

    @Autowired
    private ArchiveManagerService archiveManagerService;

    @Autowired
    private ImportController importController;

    private static boolean setUpIsDone = false;

    private static String testPid = "";

    @Before
    @WithMockUser(username = "testExportData", password = "pass", roles = "USER")
    public void setUp() throws IOException, FileUploadException {
        if (setUpIsDone) {
            return;
        }
        File testzip = OlahdTesttools.createTestOcrdzip("test.zip");
        HttpServletRequest request = OlahdTesttools.createZipUploadRequest(testzip);
        Principal user = SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> importData = importController.importData(request, user);

        testPid = ((ResponseMessage)importData.getBody()).getPid();
        OlahdTesttools.waitForArchive(testPid, archiveManagerService);
        setUpIsDone = true;
    }

    @Test
    @WithMockUser(username = "testExportData", password = "pass", roles = "USER")
    public void testExportMetsfile() throws Exception {
        ResponseEntity<InputStreamResource> res = exportController.exportMetsfile(testPid);
        String text = IOUtils.toString(res.getBody().getInputStream(), "utf-8");
        Assert.isTrue(StringUtils.isNotBlank(text), "METS-file request not successful");
    }
}
