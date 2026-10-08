package com.team.blog.media.infra.storage;

import com.team.blog.media.application.ImageProperties;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 클라이언트 (research R1·R20). 서버가 쓰는 {@link S3Client}는 {@code blog.image.storage.endpoint}(compose
 * 안 {@code http://minio:9000}), 브라우저용 서명 {@link S3Presigner}는 {@code presign-endpoint}를 쓴다 — SigV4는
 * {@code Host}를 서명하므로 브라우저가 실제로 접속하는 주소로 서명해야 한다. 둘 다 path-style, 정적 자격 증명(앱 전용 키).
 *
 * <p>HTTP 클라이언트는 JDK {@code HttpURLConnection}(Netty·Apache를 들이지 않음). 체크섬은 꼭 필요한
 * 요청(DeleteObjects)에만 붙인다 — MinIO 호환과 Presigned PUT 헤더를 단순하게 두기 위해서다.
 */
@Configuration(proxyBeanMethods = false)
public class StorageClientConfig {

    @Bean(destroyMethod = "close")
    S3Client imageS3Client(ImageProperties properties) {
        ImageProperties.Storage storage = properties.storage();
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .forcePathStyle(true)
                .credentialsProvider(credentials(storage))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(2))
                                .socketTimeout(Duration.ofSeconds(10))
                                .build())
                .overrideConfiguration(
                        o ->
                                o.apiCallTimeout(Duration.ofSeconds(20))
                                        .apiCallAttemptTimeout(Duration.ofSeconds(10)))
                .build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner imageS3Presigner(ImageProperties properties) {
        ImageProperties.Storage storage = properties.storage();
        return S3Presigner.builder()
                .endpointOverride(URI.create(storage.presignEndpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .checksumValidationEnabled(false)
                                .build())
                .build();
    }

    private static StaticCredentialsProvider credentials(ImageProperties.Storage storage) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }
}
