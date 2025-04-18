package de.ocrd.olahd.service;

import de.ocrd.olahd.domain.HttpFile;
import de.ocrd.olahd.domain.ImportResult;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import okhttp3.Response;

public interface ArchiveManagerService {

    /**
     * Import a new ZIP file to the system
     *
     * @param extractedDir The path to the folder where the ZIP file was extracted
     * @param pid          The PID which was assigned for this file
     * @param metaData     The list of meta-data of this ZIP
     * @return Meta-data from the import process (e.g. URL to archive on disk / tape)
     * @throws IOException Thrown if something's wrong when connecting to different services
     */
    ImportResult importZipFile(Path extractedDir,
                               String pid,
                               List<AbstractMap.SimpleImmutableEntry<String, String>> metaData) throws IOException;

    /**
     * Import a new version of a work
     *
     * @param extractedDir The path to the folder where the ZIP file was extracted
     * @param pid          The PID which was assigned for this file
     * @param metaData     The list of meta-data of this ZIP
     * @param prevPid      The PID of the previous version
     * @return Meta-data from the import process (e.g. URL to archive on disk / tape)
     * @throws IOException Thrown if something's wrong when connecting to different services
     */
    ImportResult importZipFile(Path extractedDir,
                               String pid,
                               List<AbstractMap.SimpleImmutableEntry<String, String>> metaData,
                               String prevPid) throws IOException;

    /**
     * Export an archive from the hard drive or tape.
     *
     * @param identifier The identifier of the archive
     * @param type       Full export or quick export
     * @param isInternal To indicate if the identifier is an internal ID or not (PID, PPN,...)
     * @return The {@link Response} object to get the stream and close it properly.
     * @throws IOException Thrown if something's wrong when connecting to the archive system
     */
    Response export(String identifier, String type, boolean isInternal) throws IOException;

    /**
     * Get a list of files from the archive manager, pack them all in a zip file and return to the
     * user.
     *
     * @param archiveId    The internal ID of the archive.
     * @param files        The list of files to be downloaded.
     * @param outputStream The stream to write the output to.
     * @param isInternal   To indicate if the identifier is an internal ID (mongodb) or not (PID,
     *                     PPN,...)
     */
    void downloadFiles(String archiveId, String[] files, OutputStream outputStream, boolean isInternal) throws IOException;

    /**
     * Move an archive from a tape to a hard drive
     *
     * @param identifier The public identifier of the archive (PID, PPN,...)
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    void moveFromTapeToDisk(String identifier) throws IOException;

    /**
     * Move an archive from a hard drive to a tape
     *
     * @param identifier The public identifier of the archive (PID, PPN,...)
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    void moveFromDiskToTape(String identifier) throws IOException;

    /**
     * Check if an archive was moved from tape to disk so that it can be exported.
     *
     * This checks if an archive is available on the mirror-profile. An archive is on the mirror profile only if it
     * was moved from tape to disk. Archives only available online (hot) will return false
     *
     *
     * @param identifier The public identifier of the archive (PID, PPN,...)
     * @return True if the archive is ready on the hard drive, false otherwise.
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    boolean isArchiveOnDisk(String identifier) throws IOException;

    /**
     * Get information about the archive
     *
     * @param id          The ID of the archive
     * @param withFile    Should all files in the archive be returned or not
     * @param limit       Number of files to be returned in one call
     * @param offset      Number of files to skip from the beginning
     * @param internalId  Is the id a PID (false) or a cdstar-archive-id
     *
     * @return A JSON contains information about the archive
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    String getArchiveInfo(String id, boolean withFile, int limit, int offset, boolean internalId) throws IOException;

    /**
     * Get information about a file in the specified archive
     *
     * @param id       The internal ID of the archive
     * @param path     Path to the file
     * @param infoOnly If true, return only the meta-data. Return the file otherwise
     * @param internalId  Is the id a PID (false) or a cdstar-archive-id
     *
     * @return An object wrapping necessary headers and an byte array of the result,
     *         either it's a string (file info) or the actual file
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    HttpFile getFile(String id, String path, boolean infoOnly, boolean internalId) throws IOException;

    /**
     * Get bag-info.txt from OCRD-ZIP for provided id, converted into a Map.
     *
     * @param pid: PID or PPA of archive
     * @return Map with key-value-pairs of bag-info.txt
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    Map<String, String> getBagInfoTxt(String pid) throws IOException;

    /**
     * Get the file from the archive via it's PID/PPA and path.
     *
     * This method searches in Online-Profile and Mirror-Profile, so it should not be used for images or large
     * files which might be only available in offline profile.
     *
     * @param id    identifier of archive (PID/PPA)
     * @param path  Path to the file inside archive
     * @return The {@link Response} object to get the stream and close it properly.
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    Response exportFile(String id, String path) throws IOException;

    /**
     * Reads the METS from the archive via it's PID/PPA
     *
     * @param id    identifier of archive (PID/PPA)
     * @return The {@link Response} object to get the stream and close it properly.
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    Response exportMets(String id) throws IOException;

    /**
     * Delete an archive
     *
     * @param archiveId    The internal ID of the archive
     * @param txId         Transaction ID. Can be null
     * @throws IOException Thrown if something's wrong when connecting to the archive services
     */
    void deleteArchive(String archiveId, String txId) throws IOException;

    /**
     * Read all filegroups from the METS
     *
     * This function downloads the METS from Cdstar and then reads all USE attributes from all `fileGrp`-elements inside
     * the `fileSec`
     *
     * @param pid
     * @return List of File-Groups
     * @throws IOException
     */
    public List<String> readFilegroups(String pid) throws IOException;
}
