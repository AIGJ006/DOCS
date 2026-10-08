package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.infra.storage.ImageStorage;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 저장소 구현 (003 T009, contracts/storage.md §1). 실제 pgsty/silo 컨테이너 + 앱 전용 키. */
class S3ImageStorageIT extends StorageIntegrationTestBase {

    private static final byte[] BODY = "0123456789abcdef".repeat(64).getBytes();

    @Autowired ImageStorage storage;

    private static String newKey(String ext) {
        return "images/2026/10/" + UUID.randomUUID() + "." + ext;
    }

    @Test
    void 준비한_주소로_정확한_크기와_형식을_PUT하면_200이고_head가_머리_정보를_준다() {
        String key = newKey("webp");
        ImageStorage.UploadTarget target =
                storage.prepareUpload(key, "image/webp", BODY.length, Duration.ofMinutes(5));

        assertThat(target.method()).isEqualTo("PUT");
        assertThat(target.headers())
                .containsEntry("Content-Type", "image/webp")
                .containsEntry("Cache-Control", ImageStorage.CACHE_CONTROL);
        assertThat(target.url())
                .startsWith(MinioContainerSupport.endpoint() + "/blog/" + key + "?")
                .contains("X-Amz-Signature=");
        assertThat(StorageHttp.put(target, BODY)).isEqualTo(200);

        ImageStorage.StoredObject head = storage.head(key).orElseThrow();
        assertThat(head.size()).isEqualTo(BODY.length);
        assertThat(head.contentType()).isEqualTo("image/webp");
        assertThat(head.cacheControl()).isEqualTo(ImageStorage.CACHE_CONTROL);
        assertThat(storage.head(newKey("webp"))).isEmpty();
    }

    @Test
    void 서명보다_큰_본문이나_다른_Content_Type은_403() {
        String key = newKey("webp");
        ImageStorage.UploadTarget target =
                storage.prepareUpload(key, "image/webp", 100, Duration.ofMinutes(5));

        assertThat(StorageHttp.put(target, BODY)).isEqualTo(403);
        Map<String, String> png = new HashMap<>(target.headers());
        png.put("Content-Type", "image/png");
        assertThat(StorageHttp.put(target.url(), png, new byte[100])).isEqualTo(403);
        assertThat(MinioContainerSupport.exists(key)).isFalse();
    }

    @Test
    void 만료된_주소는_403() throws InterruptedException {
        String key = newKey("jpg");
        ImageStorage.UploadTarget target =
                storage.prepareUpload(key, "image/jpeg", BODY.length, Duration.ofSeconds(1));
        Thread.sleep(2500);
        assertThat(StorageHttp.put(target, BODY)).isEqualTo(403);
    }

    @Test
    void 앞부분만_읽고_전체_스트림도_연다() throws IOException {
        String key = newKey("png");
        MinioContainerSupport.putDirect(key, "image/png", BODY);

        assertThat(storage.readHead(key, 10)).isEqualTo(java.util.Arrays.copyOf(BODY, 10));
        assertThat(storage.readHead(key, 1_000_000)).isEqualTo(BODY);
        assertThat(storage.readHead(newKey("png"), 10)).isEmpty();
        try (InputStream in = storage.openStream(key)) {
            assertThat(in.readAllBytes()).isEqualTo(BODY);
        }
    }

    @Test
    void 묶음_삭제는_없는_키도_성공이고_실패한_키만_돌려준다() {
        String a = newKey("webp");
        String b = newKey("webp");
        MinioContainerSupport.putDirect(a, "image/webp", BODY);
        MinioContainerSupport.putDirect(b, "image/webp", BODY);
        // 앱 전용 키는 images/* 밖을 지울 권한이 없다
        String outside = "private/" + UUID.randomUUID() + ".webp";

        var failed = storage.deleteAll(List.of(a, b, newKey("webp"), outside));

        assertThat(failed).containsExactly(outside);
        assertThat(MinioContainerSupport.exists(a)).isFalse();
        assertThat(MinioContainerSupport.exists(b)).isFalse();
        assertThat(storage.deleteAll(List.of())).isEmpty();
    }

    @Test
    void 익명은_images_아래_객체만_읽고_목록은_못_본다() {
        String key = newKey("webp");
        MinioContainerSupport.putDirect(key, "image/webp", BODY);

        assertThat(StorageHttp.get(MinioContainerSupport.publicBaseUrl() + "/" + key).statusCode())
                .isEqualTo(200);
        assertThat(
                        StorageHttp.get(MinioContainerSupport.publicBaseUrl() + "?list-type=2")
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void 서버가_받은_바이트를_그대로_올린다() {
        String key = newKey("gif");
        storage.put(key, "image/gif", BODY.length, new java.io.ByteArrayInputStream(BODY));
        assertThat(MinioContainerSupport.read(key)).isEqualTo(BODY);
        assertThat(storage.head(key).orElseThrow().cacheControl())
                .isEqualTo(ImageStorage.CACHE_CONTROL);
    }
}
