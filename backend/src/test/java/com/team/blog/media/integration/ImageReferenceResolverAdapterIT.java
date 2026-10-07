package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.infra.ImageReferenceResolverAdapter;
import com.team.blog.shared.application.markdown.OwnedImage;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 사진 판별 임시 어댑터 (002 T025, 12 §6, 23 §2-4). 공개 주소는 테스트 기본값 {@code http://localhost:9000/blog}. */
class ImageReferenceResolverAdapterIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final String THUMB =
            "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c_thumb.webp";
    private static final String OLD = "images/2025/01/11111111-2222-4333-8444-555555555555.png";

    @Autowired ImageReferenceResolverAdapter adapter;

    @Test
    void 공개_주소와_키_모양이_맞을_때만_키를_돌려준다() {
        assertThat(adapter.keyOf(BASE + "/" + KEY)).contains(KEY);
        assertThat(adapter.keyOf(BASE + "/" + THUMB)).contains(THUMB);
        assertThat(adapter.keyOf(BASE + "/" + OLD)).contains(OLD);
        assertThat(adapter.keyOf("https://other.example/blog/" + KEY)).isEmpty();
        assertThat(adapter.keyOf(BASE + "/files/" + KEY)).isEmpty();
        assertThat(adapter.keyOf(BASE + "/" + KEY + "?x=1")).isEmpty();
        assertThat(
                        adapter.keyOf(
                                BASE + "/images/2026/13/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp"))
                .isEmpty();
        assertThat(adapter.keyOf(BASE + "/images/2026/10/not-a-uuid.webp")).isEmpty();
        assertThat(adapter.keyOf(BASE + "/" + KEY.replace(".webp", ".svg"))).isEmpty();
        assertThat(adapter.keyOf(BASE + "blog/" + KEY)).isEmpty();
        assertThat(adapter.keyOf(null)).isEmpty();
    }

    @Test
    void 옛_주소_목록도_판별한다() {
        assertThat(
                        ImageReferenceResolverAdapter.parseKey(
                                "https://old.example/" + KEY,
                                List.of(BASE, "https://old.example/")))
                .contains(KEY);
    }

    @Test
    void 올린_회원의_사진만_한_번의_조회로_찾는다() {
        long author = members().member().create();
        long other = members().member().create();
        insertImage(author, KEY, THUMB);
        insertImage(author, OLD, null);
        String othersKey = "images/2026/09/99999999-8888-4777-8666-555555555555.jpg";
        insertImage(other, othersKey, null);

        try (SqlCounter.Scope scope = SqlCounter.start()) {
            var owned = adapter.findOwned(List.of(KEY, OLD, othersKey, KEY), author);
            assertThat(owned)
                    .containsOnlyKeys(KEY, OLD)
                    .containsEntry(KEY, new OwnedImage(KEY, THUMB))
                    .containsEntry(OLD, new OwnedImage(OLD, null));
            assertThat(scope.count()).isEqualTo(1);
        }
        assertThat(adapter.findOwned(List.of(), author)).isEmpty();
    }

    @Test
    void 공개_주소는_지금_설정값으로_만든다() {
        assertThat(adapter.publicUrlOf(KEY)).isEqualTo(BASE + "/" + KEY);
    }

    private void insertImage(long uploader, String key, String thumb) {
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes) VALUES (?, ?, ?, 'image/webp', 1000)",
                uploader,
                key,
                thumb);
    }
}
