package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.support.ImageApi;
import com.team.blog.support.MinioContainerSupport;
import com.team.blog.support.StorageIntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 성능 측정 (003 T089, plan Performance Goals): presign p95 200ms, complete p95 1초 (로컬 컨테이너, 각 100회).
 * 1분 한도(회원당 20번) 때문에 회원 5명이 20번씩 부른다. 결과는 quickstart §2에 적는다.
 */
class ImageUploadPerformanceIT extends StorageIntegrationTestBase {

    private static final int RUNS = 100;
    private static final int PER_MEMBER = 20;
    private static final int WARMUP = 5;

    private record Uploaded(Cookie session, long id) {}

    static long p95(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000;
    }

    @Test
    void presign_p95_200ms_complete_p95_1초() throws Exception {
        ImageApi api = new ImageApi(mockMvc);
        byte[] original = ImageCompleteIT.fixture("photo-1920.webp");
        byte[] thumb = ImageCompleteIT.fixture("thumb-640.webp");
        String body = ImageApi.postBody("image/webp", original.length, "image/webp", thumb.length);

        // 첫 호출(클래스 로딩·연결 준비)은 재지 않는다
        Cookie warm = TestLogin.loginAs(mockMvc, members().member().create());
        for (int i = 0; i < WARMUP; i++) {
            long id = ImageApi.json(api.presign(warm, body)).path("imageId").asLong();
            put(id, original, thumb);
            api.complete(warm, id);
        }

        List<Long> presignNanos = new ArrayList<>();
        List<Uploaded> uploaded = new ArrayList<>();
        Cookie session = null;
        for (int i = 0; i < RUNS; i++) {
            if (i % PER_MEMBER == 0) {
                session = TestLogin.loginAs(mockMvc, members().member().create());
            }
            long started = System.nanoTime();
            MvcResult result = api.presign(session, body);
            presignNanos.add(System.nanoTime() - started);
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            long id = ImageApi.json(result).path("imageId").asLong();
            put(id, original, thumb);
            uploaded.add(new Uploaded(session, id));
        }

        List<Long> completeNanos = new ArrayList<>();
        for (Uploaded u : uploaded) {
            long started = System.nanoTime();
            MvcResult result = api.complete(u.session(), u.id());
            completeNanos.add(System.nanoTime() - started);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        }

        long presignP95 = p95(presignNanos);
        long completeP95 = p95(completeNanos);
        System.out.printf(
                "T089 측정: presign p95 %dms, complete p95 %dms (각 %d회)%n",
                presignP95, completeP95, RUNS);
        assertThat(presignP95).as("presign p95(ms)").isLessThanOrEqualTo(200);
        assertThat(completeP95).as("complete p95(ms)").isLessThanOrEqualTo(1000);
    }

    private void put(long id, byte[] original, byte[] thumb) {
        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT storage_key, thumb_storage_key FROM image WHERE id = ?", id);
        MinioContainerSupport.putDirect((String) row.get("storage_key"), "image/webp", original);
        MinioContainerSupport.putDirect((String) row.get("thumb_storage_key"), "image/webp", thumb);
    }
}
