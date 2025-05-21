package de.ocrd.olahd.operandi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.ocrd.olahd.exceptions.OperandiException;
import java.io.IOException;
import java.io.InputStream;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

/**
 * This service communicates with Operandi
 *
 * The operandi-processed workspace is finally uploaded to operandi. Therefore an account and an url is provided to
 * operandi as uploading needs authentication. The account of the user who started the job is not used because 1. the
 * password is unknown and 2. the upload is not issued directly by the user. But it is still possible to find out the
 * user of this job because for every operandi-job the pid of the to olahd uploaded workspace is stored.
 */
@Service
public class OperandiService {

    /** Base-url of operandi */
    @Value("${operandi.url:}")
    private String operandiUrl;

    /** Username of used operandi account */
    @Value("${operandi.username:}")
    private String operandiUsername;

    /** Password for operandi account */
    @Value("${operandi.password:}")
    private String operandiPassword;

    /** Username to upload with operandi processed workspaces. An admin account is used*/
    @Value("${operandi.olahd.username:}")
    private String olahdUsername;

    /** Password for olahdUsername*/
    @Value("${operandi.olahd.password:}")
    private String olahdPassword;

    /** Base-Url to upload to*/
    @Value("${operandi.olahd.url:}")
    private String olahdBaseUrl;

    /**
     * Run a workflow on a workspace and return the operandi internal job-id of the started job
     *
     * @param workflowId
     * @param workspaceId
     * @param inputFileGrp
     * @return
     */
    public String runWorkflow(String workflowId, String workspaceId, String inputFileGrp) {
        if (StringUtils.isBlank(operandiUrl)) {
            throwOperandiException("Operandi-URL not set. Aborting", null);
        }
        String url = String.format("%s/workflow/%s", operandiUrl, workflowId);
        OkHttpClient client = new OkHttpClient();

        final String payload = String.format(
            "{"
                + "\"workflow_args\": {"
                    + "\"workspace_id\": \"%s\","
                    + "\"input_file_grp\": \"%s\""
                + "},"
                + "\"sbatch_args\": {}"
            + "}",
            workspaceId, inputFileGrp
        );

        Request request = new Request.Builder()
            .url(url)
              .addHeader("Authorization", Credentials.basic(operandiUsername, operandiPassword))
              .addHeader("Content-Type", "application/json")
              .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload))
              .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throwOperandiException("Request to start operandi workflow failed", response);
            }
            String bodyString = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bodyString);

            return root.get("resource_id").asText();
        } catch (IOException e) {
            throw new OperandiException("Error handling operandi run-workflow response", e);
        }
    }

    /**
     * Upload a workspace (ocrd-zip) to Operandi and return its operandi internal id
     *
     * @param pathToZip
     * @return
     */
    public String uploadWorkspace(InputStream zipStream) {
        if (StringUtils.isBlank(operandiUrl)) {
            throwOperandiException("Operandi-URL not set. Aborting", null);
        }

        String url = String.format("%s/workspace", operandiUrl);
        OkHttpClient client = new OkHttpClient();

        RequestBody body = new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.parse("application/zip");
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                Source source = Okio.source(zipStream);
                sink.writeAll(source);
            }
        };

        MultipartBody multipartBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("workspace", "olahd-workspace.ocrd.zip", body)
            .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(operandiUsername, operandiPassword))
                .post(multipartBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throwOperandiException("Request to upload workspace to operandi failed", response);
            }
            String bodyString = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bodyString);

            return root.get("resource_id").asText();
        } catch (IOException e) {
            throw new OperandiException("Error handling operandi workspace-upload response", e);
        }
    }

    /**
     * Query the job-status of an operandi-job until it is finished or the timeout is reached
     *
     * A operandi-job is expected to either succeed or fail. If a server error occurs, this function throws a exception
     *
     * @param workflowId
     * @return true if job was successful, false otherwise.
     */
    public String getJobStatus(String jobId) {
        String url = String.format("%s/workflow-job/%s", operandiUrl, jobId);
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(operandiUsername, operandiPassword))
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpServerErrorException(
                    HttpStatus.valueOf(response.code()), "Error querying operandi workflow-job-status"
                );
            }
            String bodyString = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bodyString);
            return root.get("job_state").asText();
        } catch (IOException e) {
            throw new OperandiException("Error processing operandi workflow-job-status response", e);
        }
    }

    /**
     * Upload a workspace to olahd and return it's pid
     *
     * @param workspaceId
     * @return
     */
    public String uploadToOlahd(String workspaceId) {
        String url = String.format("%s/push_to_ola_hd?workspace_id=%s", operandiUrl, workspaceId);
        OkHttpClient client = new OkHttpClient();

        final String payload = String.format(
            "{"
                + "\"username\": \"%s\", "
                + "\"password\": \"%s\", "
                + "\"endpoint\":\"%s\""
            + "}", olahdUsername, olahdPassword, String.format("%s/api/bag", olahdBaseUrl)
        );
        Request request = new Request.Builder()
            .url(url)
              .addHeader("Authorization", Credentials.basic(operandiUsername, operandiPassword))
              .addHeader("Content-Type", "application/json")
              .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload))
              .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throwOperandiException("Request to upload workspace from operandi to olahd failed", response);
            }
            String bodyString = response.body().string();
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(bodyString);

            return root.get("pid").asText();
        } catch (IOException e) {
            throw new OperandiException("Error handling operandi to olahd upload response", e);
        }
    }

    /**
     * Delete a operandi-workspace
     *
     * @param workspaceId
     * @return
     */
    public void deleteWorkspace(String workspaceId) {
        String url = String.format("%s/workspace/%s", operandiUrl, workspaceId);
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
            .url(url)
              .addHeader("Authorization", Credentials.basic(operandiUsername, operandiPassword))
              .delete()
              .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throwOperandiException("Request to delete workspace from operandi failed", response);
            }
        } catch (IOException e) {
            throw new OperandiException("Error handling workspace delete response", e);
        }
    }

    /**
     * Facade to throw an exception after a failed operandi request
     *
     * @param msg
     * @param response
     */
    private void throwOperandiException(String msg, Response response) {
        throw new OperandiException(String.format(
            "%s. Code: %d. Text: %s",
            msg,
            response != null ? response.code() : 0,
            response != null ? response.toString() : ""
        ));

    }

}
