package ola.hd.longtermstorage.controller.importarchive;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import ola.hd.longtermstorage.domain.TrackingInfo;

/**
 * Collection of parameters(information) for a Bag-Import
 */
public class BagImportParams {

    /** Path to extracted OCRD-ZIP */
    Path destination;

    /** PID of OCRD-ZIP */
    String pid;

    /** The form parameters of the POST request */
    FormParams formParams;

    /** Info from bag-info.txt of the OCRD-ZIP*/
    List<AbstractMap.SimpleImmutableEntry<String, String>> bagInfos;

    /** Info of ongoing Import to be stored in the MongoDB */
    TrackingInfo info;

    /** Parent of destination. For cleanup after successful import*/
    Path tempDir;

    /** URL to webnotifier for sending requests*/
    String webnotifierUrl;

    public BagImportParams(Path destination, String pid, FormParams formParams,
        List<SimpleImmutableEntry<String, String>> bagInfos, TrackingInfo info, Path tempDir, String webnotifierUrl
    ) {
        super();
        this.destination = destination;
        this.pid = pid;
        this.formParams = formParams;
        this.bagInfos = bagInfos;
        this.info = info;
        this.tempDir = tempDir;
        this.webnotifierUrl = webnotifierUrl;
    }
}
