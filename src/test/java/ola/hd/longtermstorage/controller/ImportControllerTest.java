package ola.hd.longtermstorage.controller;

import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_PW;
import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_ROLE;
import static ola.hd.longtermstorage.test.tools.OlahdTesttools.TEST_USER;

import java.io.File;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.service.PidService;
import ola.hd.longtermstorage.test.tools.OlahdTesttools;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.Assert;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class ImportControllerTest {

    @Autowired
    private ImportController importController;

    @Autowired
    private PidService pidService;

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PW, roles = TEST_ROLE)
    public void testImportData() throws Exception {
        File testzip = OlahdTesttools.createTestOcrdzip();
        HttpServletRequest request = OlahdTesttools.createZipUploadRequest(testzip);
        Principal user = SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> importData = importController.importArchive(request, user);

        String pid = ((ResponseMessage)importData.getBody()).getPid();
        Assert.notNull(pid, "Pid of import-response must not be null");
        pidService.deletePid(pid);
    }
}
