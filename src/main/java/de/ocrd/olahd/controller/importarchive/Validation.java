package de.ocrd.olahd.controller.importarchive;

import de.ocrd.olahd.Constants;
import de.ocrd.olahd.exceptions.MetsInvalidException;
import de.ocrd.olahd.exceptions.MetsSchemaException;
import de.ocrd.olahd.exceptions.OcrdzipInvalidException;
import de.ocrd.olahd.utils.Utils;
import gov.loc.repository.bagit.domain.Bag;
import gov.loc.repository.bagit.domain.Metadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

/**
 * Class with functions regarding validation of the import data
 */
class Validation {

    private static Schema METS_VALIDATION_SCHEMA;

    private Validation() {}

    /**
     * Validates a bag against ocrd-zip specification: https://ocr-d.de/en/spec/ocrd_zip. This
     * function assumes path is existing and path is a valid extracted bagit. Because of that these
     * checks (validate the bagit) have to be done already.
     *
     * Many, even from ocr-d provided testdata, does not fully follow the specification. So some
     * checks are disabled for now.
     *
     * @param bag The Bag-object of this ocrdzip
     * @param bagdir Path to (unpacked) bags root
     * @param params Form parameters of the import-request
     * @throws OcrdzipInvalidException - if bag is invalid
     */
    public static void validateOcrdzip(Bag bag, Path bagdir, FormParams params) throws OcrdzipInvalidException {
        List<String> res = new ArrayList<>();
        Metadata metadata = bag.getMetadata();
//        if (!metadata.contains("BagIt-Profile-Identifier")) {
//            res.add("bag-info.txt must contain key: 'BagIt-Profile-Identifier'");
//            // this identifier has no impact of the anything regarding this bagit and it's only
//            // purpose is to reference the spec. So verification does not help ensure functionality
//        }
//        if (!metadata.contains("Ocrd-Base-Version-Checksum")) {
//            res.add("bag-info.txt must contain key: 'Ocrd-Base-Version-Checksum'");
//        } else {
//            // I don't understand the intention and function of Ocrd-Base-Version-Checksum yet, so
//            // this is unfinished here:
//            String value = metadata.get("Ocrd-Base-Version-Checksum").get(0);
//        }

        if (!metadata.contains("Ocrd-Identifier")) {
            res.add("bag-info.txt must contain key: 'Ocrd-Identifier'");
            // spec says "A globally unique identifier" but I have no idea how to verify that so
            // only presence of key is verified
            // TODO: it can at least be checked if the provided ocrd-identifier is used somewhere
            // else in the mongo-database
        }

        if (!metadata.contains(Constants.BAGINFO_KEY_METS)) {
            if (!Files.exists(bag.getRootDir().resolve("data").resolve("mets.xml"))) {
                res.add(String.format(
                    "mets.xml not found and '%s' not provided in bag-info.txt",
                    Constants.BAGINFO_KEY_METS
                ));
            }
        } else {
            String value = metadata.get(Constants.BAGINFO_KEY_METS).get(0);
            if (!Files.exists(bag.getRootDir().resolve("data").resolve(value))) {
                res.add(String.format("'%s' is set, but specified file not existing", Constants.BAGINFO_KEY_METS));
            }
        }

        if (Files.notExists(bagdir.resolve(Constants.PAYLOAD_MANIFEST_NAME))) {
            res.add(
                String.format(
                    "Ocrd-Zip must contain payloadmanifest '%s'",
                    Constants.PAYLOAD_MANIFEST_NAME
                )
            );
        }

        // validate provided filegrps are actually existing in ocrdzip
        boolean imageFgrpPresent = metadata.contains(Constants.BAGINFO_KEY_IMAGE_FILEGRP);
        boolean fullFgrpPresent = metadata.contains(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP);
        if (imageFgrpPresent || fullFgrpPresent) {
            List<String> fileGrps = new ArrayList<>(0);
            try {
                fileGrps = Files.list(bagdir.resolve("data"))
                        .filter(Files::isDirectory)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (imageFgrpPresent) {
                String imageFileGrp = metadata.get(Constants.BAGINFO_KEY_IMAGE_FILEGRP).get(0);
                if (!fileGrps.contains(imageFileGrp)) {
                    res.add(String.format("'%s' is provided, but specified File-Grp (%s) is not"
                            + " existing", Constants.BAGINFO_KEY_IMAGE_FILEGRP, imageFileGrp));
                }
            }
            if (fullFgrpPresent) {
                String fullFileGrp = metadata.get(Constants.BAGINFO_KEY_FULLTEXT_FILEGRP).get(0);
                if (!fileGrps.contains(fullFileGrp)) {
                    res.add(String.format("'%s' is provided, but specified File-Grp (%s) is not"
                            + " existing", Constants.BAGINFO_KEY_FULLTEXT_FILEGRP, fullFileGrp));
                }
            }
        }

        if (metadata.contains(Constants.BAGINFO_KEY_FTYPE)) {
            String ftype = metadata.get(Constants.BAGINFO_KEY_FTYPE).get(0);
            if (!Constants.POSSIBLE_FULLTEXT_FTYPES.contains(ftype)) {
                res.add(String.format("'%s' is provided, but value (%s) is invalid. Valid are "
                        + "following values: '%s'", Constants.BAGINFO_KEY_FTYPE, ftype, String.join(
                        ", ", Constants.POSSIBLE_FULLTEXT_FTYPES)));
            }
        }

        if (metadata.contains(Constants.BAGINFO_KEY_IS_GT)) {
            String isGt = metadata.get(Constants.BAGINFO_KEY_IS_GT).get(0);
            if (Utils.stringToBool(isGt) == null) {
                res.add(String.format("'%s' is provided, but specified value may only be 'true' or"
                        + " 'false'", Constants.BAGINFO_KEY_IS_GT));
            }
        }

        // This has to be the last command in this method
        if (!res.isEmpty()) {
            throw new OcrdzipInvalidException(res);
        }
    }

    /**
     * Validate that mets adheres to its xsd
     *
     * @param bag
     * @throws MetsInvalidException - if mets of bag is invalid
     */
    static void validateMetsfileSchema(Bag bag) {
        var metadata = bag.getMetadata();
        Path mets;
        if (metadata.contains(Constants.BAGINFO_KEY_METS)) {
            String value = metadata.get(Constants.BAGINFO_KEY_METS).get(0);
            mets = bag.getRootDir().resolve("data").resolve(value);
        } else {
            mets = bag.getRootDir().resolve("data").resolve("mets.xml");
        }
        try {
            var schema = getMetsValidationSchema();
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(mets.toFile()));
        } catch (IOException | SAXException e) {
            throw new MetsInvalidException(e.getMessage());
        }
    }

    private static Schema getMetsValidationSchema() {
        if (Validation.METS_VALIDATION_SCHEMA == null) {
            var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            InputStream is = Validation.class.getClassLoader().getResourceAsStream("validation/mets-1121.xsd");
            try {
                Validation.METS_VALIDATION_SCHEMA = factory.newSchema(new StreamSource(is));
            } catch (Exception e) {
                // This error happened to me once when loc.gov was under maintenance. Seems when generating the scheme a
                // request to loc.gov is made, which might fail. Seems nothing then waiting can be done about it
                Utils.logError("Error creating XML validation schema", e);
                throw new MetsSchemaException("Error creating XML validation schema", e);
            }
        }
        return Validation.METS_VALIDATION_SCHEMA;
    }
}
