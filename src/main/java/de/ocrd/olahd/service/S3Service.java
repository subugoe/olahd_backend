package de.ocrd.olahd.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Service
public class S3Service {

    private final static String bucketName = "olahds";

    @Value("${s3.key.access}")
    private String accessKey;

    @Value("${s3.key.secret}")
    private String secretKey;

    @Value("${s3.url}")
    private String url;

    private S3Client client = null;

    /**
     * Get the iiif-manifest for a pid
     *
     * The InputStream has to be closed by the caller
     *
     * @param pid
     * @return
     */
    public String getManifest(String pid) {
        String path = String.format("iiif/%s.json", pid);
        return getFile(path);
    }

    private String getFile(String path) {
        GetObjectRequest req = GetObjectRequest.builder().bucket(bucketName).key(path).build();
        ResponseBytes<GetObjectResponse> object = this.getClient().getObject(req, ResponseTransformer.toBytes());
        return object.asString(StandardCharsets.UTF_8);
    }

    public S3Client getClient() {
        if (this.client == null) {
            AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
            try {
                client = S3Client.builder()
                    .region(Region.EU_CENTRAL_1)
                    .credentialsProvider(StaticCredentialsProvider.create(creds))
                    .endpointOverride(new URI(url))
                    .forcePathStyle(true)
                    .build();
            } catch (URISyntaxException e) {
                throw new RuntimeException("Error creating S3 Client. Provided url has error(s)", e);
            }
        }
        return client;
    }
}
