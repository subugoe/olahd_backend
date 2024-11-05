package de.ocrd.olahd.utils;

import de.ocrd.olahd.Constants;
import de.ocrd.olahd.exceptions.BagitChecksumException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.DatatypeConverter;

/**
 * Validate bagit manifests: manifest-sha512.txt and tagmanifest-sha512.txt.
 *
 * The implementation from the bagit library is very slow so here is an alternative implementation. In my tests it took
 * for 40_000 files about 3 secs instead of 6 Minutes like in the LibraryOfCongress bagit-java implementation
 */
public class BagitManifestValidation {

    /** Path to unzipped bag */
    private File bagdir;

    public BagitManifestValidation(Path bagdir) {
        super();
        this.bagdir = bagdir.toFile();
    }

    /**
     * Validate checksums in manifest-sha512.txt and tagmanifest-sha512.txt;
     *
     * @param throwErrorOnMismatch throw an exception when an error occurs instead of only return the List of errors
     * @return list of errors or empty list if checksums are valid
     * @throws BagitChecksumException optionally when a missing checksum is found
     */
    public List<String> validate(boolean throwErrorOnMismatch) {
        List<String> errors = new ArrayList<>();
        if (new File(bagdir, Constants.TAG_MANIFEST_NAME).exists()) {
            try {
                errors.addAll(validateManifestFiles(Constants.TAG_MANIFEST_NAME, true));
            } catch (IOException e) {
                throw new RuntimeException("Unexpected error when validating tag manifest", e);
            }
        } else {
            errors.add("Tag-Manifest: '" + Constants.TAG_MANIFEST_NAME + "' not found");
        }

        if (throwErrorOnMismatch && !errors.isEmpty()) {
            throw new BagitChecksumException(errors);
        }

        if (new File(bagdir, Constants.PAYLOAD_MANIFEST_NAME).exists()) {
            try {
                errors.addAll(validateManifestFiles(Constants.PAYLOAD_MANIFEST_NAME, true));
            } catch (IOException e) {
                throw new RuntimeException("Unexpected error when validating payload manifest", e);
            }
        } else {
            errors.add("Tag-Manifest: '" + Constants.PAYLOAD_MANIFEST_NAME + "' not found");
        }
        if (throwErrorOnMismatch && !errors.isEmpty()) {
            throw new BagitChecksumException(errors);
        } else {
            return errors;
        }
    }

    private String calculateSHA512(File file) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Unexpected error when creating sha512-hash");
        }
        byte[] array = Files.readAllBytes(file.toPath());
        byte[] digest = md.digest(array);
        return DatatypeConverter.printHexBinary(digest).toLowerCase();
    }

    private List<String> validateManifestFiles(String manifestName, boolean returnOnerror) throws IOException {
        List<String> errors = new ArrayList<>();
        File manifestFile = new File(bagdir, manifestName);

        try (BufferedReader br = new BufferedReader(new FileReader(manifestFile))) {
            String st;
            while ((st = br.readLine()) != null) {
                String[] parts = st.split("\\s+");
                String hashFromHashFile = parts[0];
                String fileToCheckPath = parts[1];
                File fileToCheck = new File(bagdir, fileToCheckPath);
                String calculatedHash = calculateSHA512(fileToCheck);
                if (!calculatedHash.equals(hashFromHashFile)) {
                    errors.add(
                        "Checksum of file '" + fileToCheckPath + "' in manifest '" + manifestName + "' does not match"
                    );
                    if (returnOnerror) {
                        return errors;
                    }
                }
            }
            return errors;
        }
    }
}