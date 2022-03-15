package ola.hd.longtermstorage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ola.hd.longtermstorage.domain.HttpFile;
import ola.hd.longtermstorage.domain.ImportResult;
import ola.hd.longtermstorage.domain.SearchRequest;
import ola.hd.longtermstorage.domain.SearchResults;
import ola.hd.longtermstorage.msg.ErrMsg;
import ola.hd.longtermstorage.utils.Utils;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@Service
public class CdstarService implements ArchiveManagerService, SearchService {

    @Value("${cdstar.url}")
    private String url;

    @Value("${cdstar.username}")
    private String username;

    @Value("${cdstar.password}")
    private String password;

    @Value("${cdstar.vault}")
    private String vault;

    @Value("${cdstar.offlineProfile}")
    private String offlineProfile;

    @Value("${cdstar.onlineProfile}")
    private String onlineProfile;

    @Value("${cdstar.mirrorProfile}")
    private String mirrorProfile;

    @Value("${offline.mimeTypes}")
    private String offlineMimeTypes;

    /**
     * To indicate that function
     * {@linkplain #getArchiveIdFromIdentifier(String, String)} wasn't successful
     */
    private static final String NOT_FOUND = "NOT_FOUND";

    @Override
    public ImportResult importZipFile(Path extractedDir,
                                      String pid,
                                      List<AbstractMap.SimpleImmutableEntry<String, String>> metaData) throws IOException {

        String txId = null;

        try {
            // Get the transaction ID
            txId = getTransactionId();

            String onlineArchiveId = createArchive(txId, false);
            String offlineArchiveId = createArchive(txId, true);

            uploadData(extractedDir, txId, onlineArchiveId, offlineArchiveId);

            // Update archive meta-data
            setArchiveMetaData(onlineArchiveId, metaData, pid, txId);
            setArchiveMetaData(offlineArchiveId, metaData, pid, txId);

            // Commit the transaction
            commitTransaction(txId);

            // Meta-data for PID
            List<AbstractMap.SimpleImmutableEntry<String, String>> pidMetaData = new ArrayList<>();
            pidMetaData.add(new AbstractMap.SimpleImmutableEntry<>("ONLINE-URL", url + vault + "/" + onlineArchiveId + "?with=files,meta"));
            pidMetaData.add(new AbstractMap.SimpleImmutableEntry<>("OFFLINE-URL", url + vault + "/" + offlineArchiveId + "?with=files,meta"));

            return new ImportResult(onlineArchiveId, offlineArchiveId, pidMetaData);
        } catch (Exception ex) {
            if (txId != null) {
                rollbackTransaction(txId);
            }

            throw ex;
        }
    }

    @Override
    public ImportResult importZipFile(Path extractedDir, String pid,
                                      List<AbstractMap.SimpleImmutableEntry<String, String>> metaData,
                                      String prevPid) throws IOException {

        String txId = null;

        try {
            // Get the online archive of the previous version
            String prevOnlineArchiveId = getArchiveIdFromIdentifier(prevPid, onlineProfile);
            String prevOfflineArchiveId = getArchiveIdFromIdentifier(prevPid, offlineProfile);
            if (prevOnlineArchiveId.equals(NOT_FOUND) && prevOfflineArchiveId.equals(NOT_FOUND)) {
                throw new HttpClientErrorException(
                        HttpStatus.BAD_REQUEST, "Previous version with PID " + prevPid + " was not found.");
            }

            // Get the transaction ID
            txId = getTransactionId();

            String onlineArchiveId = createArchive(txId, false);
            String offlineArchiveId = createArchive(txId, true);

            uploadData(extractedDir, txId, onlineArchiveId, offlineArchiveId);

            // Update archive meta-data of current version
            setArchiveMetaData(onlineArchiveId, metaData, pid, txId);
            setArchiveMetaData(offlineArchiveId, metaData, pid, txId);

            // Delete the previous version on the hard drive
            // Only store the latest version on the hard drive
            if (!prevOnlineArchiveId.equals(NOT_FOUND)) {
                deleteArchive(prevOnlineArchiveId, txId);
            }

            // Commit the transaction
            commitTransaction(txId);

            // Meta-data for PID
            List<AbstractMap.SimpleImmutableEntry<String, String>> pidMetaData = new ArrayList<>();
            pidMetaData.add(new AbstractMap.SimpleImmutableEntry<>("ONLINE-URL", url + vault + "/" + onlineArchiveId + "?with=files,meta"));
            pidMetaData.add(new AbstractMap.SimpleImmutableEntry<>("OFFLINE-URL", url + vault + "/" + offlineArchiveId + "?with=files,meta"));

            return new ImportResult(onlineArchiveId, offlineArchiveId, pidMetaData);

        } catch (IOException ex) {
            if (txId != null) {
                rollbackTransaction(txId);
            }

            throw ex;
        }
    }

    private String getTransactionId() throws IOException {

        String transactionUrl = url + "_tx/";

        OkHttpClient client = new OkHttpClient();

        RequestBody txBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("timeout", "300")
                .build();

        Request request = new Request.Builder()
                .url(transactionUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(txBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String bodyString = response.body().string();

                    // Parse the returned JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(bodyString);
                    return root.get("id").asText();
                }
            }

            // Cannot get the transaction ID? Abort!
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot start the upload transaction.");
        }
    }

    private void uploadData(Path extractedDir, String txId, String onlineArchiveId, String offlineArchiveId) throws IOException {

        String onlineBaseUrl = url + vault + "/" + onlineArchiveId;
        String offlineBaseUrl = url + vault + "/" + offlineArchiveId;

        List<String> offlineTypes = Arrays.asList(offlineMimeTypes.split(";"));
        Tika tika = new Tika();

        Files.walk(extractedDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    String onlineUrl = onlineBaseUrl + "/" +
                            URLEncoder.encode(extractedDir.relativize(path).toString(), StandardCharsets.UTF_8);
                    String offlineUrl = offlineBaseUrl + "/" +
                            URLEncoder.encode(extractedDir.relativize(path).toString(), StandardCharsets.UTF_8);

                    String mimeType;
                    try {

                        // Try to figure out the correct MIME type
                        mimeType = tika.detect(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    // If the MIME type is unrecognizable
                    if (mimeType == null || mimeType.isEmpty()) {
                        mimeType = "application/octet-stream";
                    }

                    File file = path.toFile();

                    try {
                        // Offline file?
                        if (offlineTypes.contains(mimeType)) {

                            // Only send to offline archive
                            sendRequest(offlineUrl, txId, file, mimeType, true);
                        } else {

                            // For other files, send to both archives
                            sendRequest(offlineUrl, txId, file, mimeType, true);
                            sendRequest(onlineUrl, txId, file, mimeType, false);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void sendRequest(String url, String txId, File file, String mimeType, boolean isOffline) throws IOException {
        OkHttpClient client = new OkHttpClient();

        // Request to upload a file
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(username, password))
                .addHeader("Content-Type", mimeType)
                .addHeader("X-Transaction", txId)
                .put(RequestBody.create(MediaType.parse(mimeType), file))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong, throw the exception
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot send data to CDSTAR. URL: " + url);
            }
        }

        // After a file is uploaded, set proper meta-data for dc:type
        String storage = "online";
        if (isOffline) {
            storage = "offline";
        }

        String payload = String.format("{\"dc:type\":\"%s\"}", storage);

        Request metaRequest = new Request.Builder()
                .url(url + "?meta")
                .addHeader("Authorization", Credentials.basic(username, password))
                .addHeader("X-Transaction", txId)
                .put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload))
                .build();

        try (Response response = client.newCall(metaRequest).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong, throw the exception
                throw new HttpServerErrorException(
                        HttpStatus.valueOf(response.code()), "Cannot set meta-data for a file. URL: " + url);
            }
        }
    }

    private void commitTransaction(String txId) throws IOException {

        String txUrl = url + "_tx/" + txId;

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .build();

        Request request = new Request.Builder()
                .url(txUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(RequestBody.create(null, ""))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong here
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot commit the transaction");
            }
        }
    }

    private void rollbackTransaction(String txId) throws IOException {

        String txUrl = url + "_tx/" + txId;

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(txUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .delete()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong here
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when rolling back the transaction");
            }
        }
    }

    private String createArchive(String txId, boolean isOffline) throws IOException {
        String fullUrl = url + vault;
        String profile = onlineProfile;

        if (isOffline) {
            profile = offlineProfile;
        }

        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("profile", profile)
                .build();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .addHeader("X-Transaction", txId)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String location = response.header("Location");

                // Extract the archive ID from the location string
                String archive = "";
                if (location != null) {
                    int lastSlashIndex = location.lastIndexOf('/');
                    archive = location.substring(lastSlashIndex + 1);
                }

                return archive;
            }

            // Something is wrong, throw the exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot create archive");
        }
    }

    private void setArchiveMetaData(String archiveId, List<AbstractMap.SimpleImmutableEntry<String, String>> metaData,
                                    String pid, String txId) throws IOException {
        String fullUrl = url + vault + "/" + archiveId;
        OkHttpClient client = new OkHttpClient();

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        // Extract proper meta-data from bagit-info.txt
        if (metaData != null) {
            for (AbstractMap.SimpleImmutableEntry<String, String> item : metaData) {
                String key = item.getKey().toLowerCase();
                switch (key) {
                    case "bagging-date":
                    case "dc.date":
                        builder.addFormDataPart("meta:dc:date", item.getValue());
                        break;
                    case "ocrd-identifier":
                    case "dc.identifier":
                        builder.addFormDataPart("meta:dc:identifier", item.getValue());
                        break;
                    case "dc.title":
                        builder.addFormDataPart("meta:dc:title", item.getValue());
                        break;
                    case "dc.publisher":
                        builder.addFormDataPart("meta:dc:publisher", item.getValue());
                        break;
                    case "dc.rights":
                        builder.addFormDataPart("meta:dc:rights", item.getValue());
                        break;
                    case "dc.contributor":
                        builder.addFormDataPart("meta:dc:contributor", item.getValue());
                        break;
                    case "dc.coverage":
                        builder.addFormDataPart("meta:dc:coverage", item.getValue());
                        break;
                    case "dc.creator":
                        builder.addFormDataPart("meta:dc:creator", item.getValue());
                        break;
                    case "dc.description":
                        builder.addFormDataPart("meta:dc:description", item.getValue());
                        break;
                    case "dc.format":
                        builder.addFormDataPart("meta:dc:format", item.getValue());
                        break;
                    case "dc.language":
                        builder.addFormDataPart("meta:dc:language", item.getValue());
                        break;
                    case "dc.subject":
                        builder.addFormDataPart("meta:dc:subject", item.getValue());
                        break;
                    case "dc.type":
                        builder.addFormDataPart("meta:dc:type", item.getValue());
                        break;
                    case "dc.source":
                        builder.addFormDataPart("meta:dc:source", item.getValue());
                        break;
                    case "dc.relation":
                        builder.addFormDataPart("meta:dc:relation", item.getValue());
                        break;
                }
            }
        }

        // Set identifier
        if (pid != null) {
            builder.addFormDataPart("meta:dc:identifier", pid);
        }

        RequestBody requestBody = builder.build();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .addHeader("X-Transaction", txId)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong, throw the exception
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot set archive meta-data");
            }
        }
    }

    @Override
    public void deleteArchive(String archiveId, String txId) throws IOException {
        String fullUrl = url + vault + "/" + archiveId;

        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .delete();

        if (txId != null) {
            builder.addHeader("X-Transaction", txId);
        }

        Request request = builder.build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {

                // Something is wrong, throw the exception
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot delete archive");
            }
        }
    }

    @Override
    public Response export(String identifier, String type, boolean isInternal) throws IOException {

        String archiveId;

        // If it's an internal ID, just take it
        if (isInternal) {
            archiveId = identifier;
        } else {
            // Otherwise get the internal ID from the public ID
            // Quick export?
            if (type.equals("quick")) {
                archiveId = getArchiveIdFromIdentifier(identifier, onlineProfile);
            } else {
                // Full export
                archiveId = getArchiveIdFromIdentifier(identifier, mirrorProfile);
            }
        }

        // Archive not found
        if (archiveId.equals(NOT_FOUND)) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
        }

        // The archive can't be exported because it is still on tape
        if (!isArchiveOpen(archiveId)) {
            throw new HttpClientErrorException(HttpStatus.CONFLICT, "The archive is still on tape. Please make a full export request first.");
        }

        return exportArchive(archiveId);
    }

    @Override
    public void downloadFiles(String archiveId, String[] paths, OutputStream outputStream) throws IOException {

        // Set the base URL up to the archive level
        String baseUrl = url + vault + "/" + archiveId;

        // TODO: check the archive state. Throw an exception if the archive is in "archived" state

        OkHttpClient client = new OkHttpClient();

        // Build the GET request
        Request.Builder requestBuilder = new Request.Builder()
                .addHeader("Authorization", Credentials.basic(username, password))
                .get();

        // Open the stream for zip file
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            // For each requested file
            for (String path : paths) {

                // Build the complete URL
                String fullUrl = baseUrl + "/" + path;

                // Build the request with the complete URL
                Request request = requestBuilder.url(fullUrl).build();

                // Execute the request
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {

                        // Stream the response
                        try (InputStream inputStream = response.body().byteStream()) {

                            // Add new entry to the zip. Use full path as entry name so that the sub-directory can be created
                            ZipEntry zipEntry = new ZipEntry(path);
                            zipOutputStream.putNextEntry(zipEntry);

                            // Write the response to the zip stream
                            byte[] bytes = new byte[1024];
                            int length;
                            while ((length = inputStream.read(bytes)) != -1) {
                                zipOutputStream.write(bytes, 0, length);
                            }
                        } catch (IOException ex) {

                            // Catch the exception here so that if something's wrong with 1 file, the whole process still runs
                            ex.printStackTrace();
                        }
                    }
                } catch (IOException ex) {

                    // Catch the exception here so that if something's wrong with 1 file, the whole process still runs
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void moveFromTapeToDisk(String identifier) throws IOException {

        // Get cold-archive ID from the PID/PPN
        String archiveId = getArchiveIdFromIdentifier(identifier, offlineProfile);

        // Change the profile of the archive to a mirror profile
        if (!archiveId.equals(NOT_FOUND)) {
            updateProfile(archiveId, mirrorProfile);
        } else {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
        }
    }

    @Override
    public void moveFromDiskToTape(String identifier) throws IOException {

        // Get mirror-archive ID from the PID/PPN
        String archiveId = getArchiveIdFromIdentifier(identifier, mirrorProfile);

        // Change the profile of the archive to a cold profile
        if (!archiveId.equals(NOT_FOUND)) {
            updateProfile(archiveId, offlineProfile);
        } else {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
        }
    }

    private void updateProfile(String archiveId, String newProfile) throws IOException {
        String fullUrl = url + vault + "/" + archiveId;
        OkHttpClient client = new OkHttpClient();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("profile", newProfile)
                .build();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {

                // Something is wrong, throw the exception
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot update archive profile");
            }
        }
    }

    /* TODO: todo: this is not reliable, because we have archives(don't know why) without pid in
     * cdstar-metadata. Must be changed: ask mongodb for archive-id from pid*/
    private String getArchiveIdFromIdentifier(String identifier, String profile) throws IOException {
        String fullUrl = url + vault;

        // Search for archive with specified identifier (PPN, PID)
        String query = String.format("dcIdentifier:\"%s\" AND profile:%s", identifier, profile);

        HttpUrl httpUrl = HttpUrl.parse(fullUrl).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("order", "-modified")
                .addQueryParameter("limit", "1")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {

            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String bodyString = response.body().string();

                    // Parse the returned JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(bodyString);
                    JsonNode hits = root.get("hits");
                    JsonNode firstElement = hits.get(0);

                    // Archive found
                    if (firstElement != null) {
                        return firstElement.get("id").asText();
                    } else {
                        return NOT_FOUND;
                    }

                }
            }

            // Cannot get the archive ID? Throw the exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when getting the archive with the identifier " + identifier);
        }
    }

    private Response exportArchive(String archiveId) throws IOException {
        String fullUrl = url + vault + "/" + archiveId;

        // Construct the URL
        HttpUrl httpUrl = HttpUrl.parse(fullUrl).newBuilder()
                .addQueryParameter("export", "zip")
                .build();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();

        if (response.isSuccessful()) {
            return response;
        }

        // Cannot export the archive? Throw the exception
        throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Cannot export the archive " + archiveId);
    }

    private boolean isArchiveOpen(String archiveId) throws IOException {
        String fullUrl = url + vault + "/" + archiveId;

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String bodyString = response.body().string();

                    // Parse the returned JSON
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(bodyString);
                    String state = root.get("state").asText();

                    // Open-state archive
                    return state.equals("open") || state.equals("locked");

                }
            }

            // Cannot get the archive state? Throw the exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when getting the archive state.");
        }
    }

    @Override
    public SearchResults search(SearchRequest searchRequest) throws IOException {
        String fullUrl = url + vault;

        // Search on online storage only
        String query = String.format("(%s) AND (dcType:online OR profile:%s)", searchRequest.getQuery(), onlineProfile);

        // Construct the URL
        HttpUrl httpUrl = HttpUrl.parse(fullUrl).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("limit", searchRequest.getLimit() + "")
                .addQueryParameter("scroll", searchRequest.getScroll())
                .build();

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    String bodyString = response.body().string();

                    // Parse the returned JSON
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(bodyString, SearchResults.class);
                }
            }

            // Cannot search? Throw exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when performing search.");
        }
    }

    @Override
    public boolean isArchiveOnDisk(String identifier) throws IOException {

        String archiveId = getArchiveIdFromIdentifier(identifier, mirrorProfile);

        if (archiveId.equals(NOT_FOUND)) {
            return false;
        }

        return isArchiveOpen(archiveId);
    }

    @Override
    public String getArchiveInfo(String id, boolean withFile, int limit, int offset) throws IOException {
        String fullUrl = url + vault + "/" + id + "?with=meta";

        if (withFile) {
            fullUrl += ",files";
            fullUrl += "&limit=" + limit;
            fullUrl += "&offset=" + offset;
        }

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }

            if (response.code() == HttpStatus.NOT_FOUND.value()) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
            }

            // Cannot search? Throw exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when getting archive info.");
        }
    }

    @Override
    public HttpFile getFile(String id, String path, boolean infoOnly) throws IOException {
        String fullUrl = url + vault + "/" + id + "/" + path;

        if (infoOnly) {
            fullUrl += "?info";
        }

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                if (response.body() != null) {

                    HttpFile httpFile = new HttpFile(response.body().bytes());

                    Headers headers = response.headers();
                    httpFile.addHeaders("Content-Type", headers.get("Content-Type"));
                    httpFile.addHeaders("Content-Length", headers.get("Content-Length"));

                    return httpFile;
                }
            }

            if (response.code() == HttpStatus.NOT_FOUND.value()) {
                throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.FILE_NOT_FOUND);
            }

            if (response.code() == HttpStatus.CONFLICT.value()) {
                throw new HttpClientErrorException(HttpStatus.CONFLICT, "Cannot get the file because it is on tape.");
            }

            // Cannot search? Throw exception
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()), "Error when getting file info.");
        }
    }

    @Override
    public Map<String, String> getBagInfoTxt(String pid) throws IOException {
        String archiveId = this.getArchiveIdFromIdentifier(pid, onlineProfile);
        if (archiveId.equals(NOT_FOUND)) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
        }

        String fullUrl = url + vault + "/" + archiveId + "/bag-info.txt";
        Request request = new Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                return Utils.readBagInfoToMap(body);
            } else if (response.code() == HttpStatus.CONFLICT.value()) {
                throw new HttpClientErrorException(HttpStatus.CONFLICT,
                    "Cannot get the file because it is on tape.");
            } else {
                // Cannot search? Throw exception
                throw new HttpServerErrorException(HttpStatus.valueOf(response.code()),
                        "Error when getting bag-info.txt");
            }
        }
    }


    @Override
    public Response exportFile(String pid, String path) throws IOException{
        String archiveId = getArchiveIdFromIdentifier(pid, onlineProfile);
        if (archiveId.equals(NOT_FOUND)) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.ARCHIVE_NOT_FOUND);
        }

        String fullUrl = url + vault + "/" + archiveId + "/" + path;
        HttpUrl httpUrl = HttpUrl.parse(fullUrl).newBuilder().build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .addHeader("Authorization", Credentials.basic(username, password))
                .get()
                .build();

        Response response = new OkHttpClient().newCall(request).execute();

        if (response.isSuccessful()) {
            return response;
        } else if (Integer.valueOf(response.code()).equals(404)) {
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, ErrMsg.FILE_NOT_FOUND);
        } else {
            throw new HttpServerErrorException(HttpStatus.valueOf(response.code()),
                    "Cannot export file from archive " + archiveId);
        }

    }
}
