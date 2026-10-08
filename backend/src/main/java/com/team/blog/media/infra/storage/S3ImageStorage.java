package com.team.blog.media.infra.storage;

import com.team.blog.media.application.ImageProperties;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * AWS SDK v2 저장소 구현 (003 T015). 로그에는 키 개수·상태 코드만 남기고 서명 주소·비밀값은 남기지 않는다.
 *
 * <ul>
 *   <li>{@link #prepareUpload}: {@code Content-Type}·{@code Content-Length}·{@code Cache-Control}을
 *       서명한다. 신고와 다른 크기·형식의 PUT은 저장소가 403으로 거부한다(점검 4번)
 *   <li>{@link #deleteAll}: {@code DeleteObjects} 1,000키씩. 응답의 오류 키만 실패로 돌려준다(없는 키는 성공)
 *   <li>연결 실패·시간 초과·5xx → {@link StorageUnavailableException}
 * </ul>
 */
@Component
public class S3ImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ImageStorage.class);
    private static final int DELETE_BATCH = 1000;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public S3ImageStorage(
            S3Client imageS3Client, S3Presigner imageS3Presigner, ImageProperties properties) {
        this.s3 = imageS3Client;
        this.presigner = imageS3Presigner;
        this.bucket = properties.storage().bucket();
    }

    @Override
    public UploadTarget prepareUpload(String key, String contentType, long size, Duration ttl) {
        PutObjectRequest put =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(size)
                        .cacheControl(CACHE_CONTROL)
                        .build();
        PresignedPutObjectRequest presigned =
                presigner.presignPutObject(b -> b.signatureDuration(ttl).putObjectRequest(put));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Cache-Control", CACHE_CONTROL);
        return new UploadTarget(
                presigned.url().toString(), "PUT", Map.copyOf(headers), presigned.expiration());
    }

    @Override
    public Optional<StoredObject> head(String key) {
        try {
            HeadObjectResponse head = s3.headObject(b -> b.bucket(bucket).key(key));
            return Optional.of(
                    new StoredObject(
                            head.contentLength(), head.contentType(), head.cacheControl()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw unavailable("head", e);
        } catch (SdkClientException e) {
            throw unavailable("head", e);
        }
    }

    @Override
    public byte[] readHead(String key, int maxBytes) {
        try {
            ResponseBytes<GetObjectResponse> bytes =
                    s3.getObjectAsBytes(
                            b -> b.bucket(bucket).key(key).range("bytes=0-" + (maxBytes - 1)));
            return bytes.asByteArray();
        } catch (NoSuchKeyException e) {
            return new byte[0];
        } catch (S3Exception e) {
            if (e.statusCode() == 404 || e.statusCode() == 416) {
                return new byte[0];
            }
            throw unavailable("readHead", e);
        } catch (SdkClientException e) {
            throw unavailable("readHead", e);
        }
    }

    @Override
    public InputStream openStream(String key) {
        try {
            return s3.getObject(b -> b.bucket(bucket).key(key));
        } catch (NoSuchKeyException e) {
            throw new NoSuchElementException("저장소에 객체가 없습니다");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new NoSuchElementException("저장소에 객체가 없습니다");
            }
            throw unavailable("openStream", e);
        } catch (SdkClientException e) {
            throw unavailable("openStream", e);
        }
    }

    @Override
    public Set<String> deleteAll(Collection<String> keys) {
        List<String> distinct =
                new ArrayList<>(new LinkedHashSet<>(keys.stream().filter(k -> k != null).toList()));
        Set<String> failed = new LinkedHashSet<>();
        for (int from = 0; from < distinct.size(); from += DELETE_BATCH) {
            List<String> batch =
                    distinct.subList(from, Math.min(distinct.size(), from + DELETE_BATCH));
            try {
                List<ObjectIdentifier> ids =
                        batch.stream().map(k -> ObjectIdentifier.builder().key(k).build()).toList();
                DeleteObjectsResponse response =
                        s3.deleteObjects(
                                b ->
                                        b.bucket(bucket)
                                                .delete(
                                                        Delete.builder()
                                                                .objects(ids)
                                                                .quiet(true)
                                                                .build()));
                for (S3Error error : response.errors()) {
                    if (!"NoSuchKey".equals(error.code())) {
                        failed.add(error.key());
                    }
                }
            } catch (S3Exception | SdkClientException e) {
                log.warn(
                        "저장소 묶음 삭제 실패: keys={}, error={}",
                        batch.size(),
                        e.getClass().getSimpleName());
                failed.addAll(batch);
            }
        }
        if (!failed.isEmpty()) {
            log.warn("저장소에서 지우지 못한 객체가 있습니다: {}개", failed.size());
        }
        return failed;
    }

    @Override
    public void put(String key, String contentType, long size, InputStream content) {
        try {
            s3.putObject(
                    b ->
                            b.bucket(bucket)
                                    .key(key)
                                    .contentType(contentType)
                                    .contentLength(size)
                                    .cacheControl(CACHE_CONTROL),
                    RequestBody.fromInputStream(content, size));
        } catch (S3Exception | SdkClientException e) {
            throw unavailable("put", e);
        }
    }

    private static StorageUnavailableException unavailable(String operation, Exception e) {
        int status = e instanceof S3Exception s3e ? s3e.statusCode() : 0;
        log.warn(
                "저장소 호출 실패: op={}, status={}, error={}",
                operation,
                status,
                e.getClass().getSimpleName());
        return new StorageUnavailableException(e);
    }
}
