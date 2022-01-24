package ola.hd.longtermstorage.controller;

import java.io.File;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;

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

import ola.hd.longtermstorage.domain.ResponseMessage;
import ola.hd.longtermstorage.test.tools.OlahdTesttools;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
public class ImportControllerTest {

    @Autowired
    private ImportController importController;

    @Test
    @WithMockUser(username = "testImportData", password = "pass", roles = "USER")
    public void testImportData() throws Exception {
        File testzip = OlahdTesttools.createTestOcrdzip("test.zip");
        HttpServletRequest request = OlahdTesttools.createZipUploadRequest(testzip);
        Principal user = SecurityContextHolder.getContext().getAuthentication();
        ResponseEntity<?> importData = importController.importData(request, user);

        Assert.notNull(((ResponseMessage)importData.getBody()).getPid(),
                "Pid of import-response must not be null");
    }
}
