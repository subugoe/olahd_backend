package ola.hd.longtermstorage.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.List;
import ola.hd.longtermstorage.Constants;
import ola.hd.longtermstorage.controller.importarchive.FormParams;
import org.junit.Test;

public class TestBaginfoConfig {

    @Test
    public void testBagconfigCreation() {
        // Arrange
        String fulltextFtype = "test-ftype";
        String workIdentifier = "test-workIdentifier";
        String imageFilegrp = "test-imageFgrp";
        String fulltextFilegrp = "test-ffilegrp";
        String prevPid = "test-prevpid";
        String importer = "test-importer";
        Boolean isGt = true;
        List<SimpleImmutableEntry<String, String>> baginfoConf = new ArrayList<>();
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FTYPE, fulltextFtype));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_WORK_IDENTIFIER, workIdentifier));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IMAGE_FILEGRP, imageFilegrp));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP, fulltextFilegrp));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_PREV_PID, prevPid));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IMPORTER, importer));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IS_GT, isGt.toString()));

        // Act
        BaginfoConfig config = BaginfoConfig.create(baginfoConf);

        // Assert
        assertEquals(config.getFulltextFileGrp(), fulltextFilegrp);
        assertEquals(config.getWorkIdentifier(), workIdentifier);
        assertEquals(config.getImageFileGrp(), imageFilegrp);
        assertEquals(config.getFulltextFtype(), fulltextFtype);
        assertEquals(config.getPrevPid(), prevPid);
        assertEquals(config.getImporter(), importer);
        assertEquals(config.getGt(), isGt);
    }

    @Test
    public void testFormParametersSubstitution() {
        // Arrange
        String ftype = "test-ftype";
        String imageFilegrp = "test-imageFgrp";
        String fulltextFilegrp = "test-ffilegrp";
        String prevPid = "test-prevpid";
        Boolean isGt = true;
        List<SimpleImmutableEntry<String, String>> baginfoConf = new ArrayList<>();
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FTYPE, ftype));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IMAGE_FILEGRP, imageFilegrp));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP, fulltextFilegrp));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_PREV_PID, prevPid));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IS_GT, isGt.toString()));
        FormParams formParams = new FormParams();
        String formFtype = "form-ftype";
        String formImageFilegrp = "form-imageFgrp";
        String formFulltextFilegrp = "form-ffilegrp";
        String formPrevPid = "form-prevpid";
        Boolean formIsGt = false;
        formParams.setFulltextFilegrp(formFulltextFilegrp);
        formParams.setImageFilegrp(formImageFilegrp);
        formParams.setFulltextFtype(formFtype);
        formParams.setPrev(formPrevPid);
        formParams.setIsGt(formIsGt);

        // Act
        BaginfoConfig config = BaginfoConfig.create(baginfoConf).considerFormParams(formParams);

        // Assert
        assertEquals(config.getFulltextFileGrp(), formFulltextFilegrp);
        assertNotEquals(config.getFulltextFileGrp(), fulltextFilegrp);

        assertEquals(config.getImageFileGrp(), formImageFilegrp);
        assertNotEquals(config.getImageFileGrp(), imageFilegrp);

        assertEquals(config.getFulltextFtype(), formFtype);
        assertNotEquals(config.getFulltextFtype(), ftype);

        assertEquals(config.getPrevPid(), formPrevPid);
        assertNotEquals(config.getPrevPid(), prevPid);

        assertEquals(config.getGt(), formIsGt);
        assertNotEquals(config.getGt(), isGt);
    }

    @Test
    public void testDefaults() {
        // Act
        BaginfoConfig config = BaginfoConfig.create(new ArrayList<>());

        // Assert
        assertEquals(config.getFulltextFileGrp(), Constants.DEFAULT_FULLTEXT_FILEGRP);
        assertEquals(config.getImageFileGrp(), Constants.DEFAULT_IMAGE_FILEGRP);
        assertEquals(config.getFulltextFtype(), Constants.DEFAULT_FULLTEXT_FTYPE);

    }

    @Test
    public void testDefaults2() {
        // Arrange
        String fulltextFtype = "test-ftype";
        String imageFilegrp = "test-imageFgrp";
        String fulltextFilegrp = "test-ffilegrp";

        List<SimpleImmutableEntry<String, String>> baginfoConf = new ArrayList<>();
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FTYPE, fulltextFtype));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_IMAGE_FILEGRP, imageFilegrp));
        baginfoConf.add(new SimpleImmutableEntry<>(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP, fulltextFilegrp));

        // Act
        BaginfoConfig config = BaginfoConfig.create(baginfoConf);

        // Assert
        assertNotEquals(config.getFulltextFileGrp(), Constants.DEFAULT_FULLTEXT_FILEGRP);
        assertNotEquals(config.getImageFileGrp(), Constants.DEFAULT_IMAGE_FILEGRP);
        assertNotEquals(config.getFulltextFtype(), Constants.DEFAULT_FULLTEXT_FTYPE);

    }
}
