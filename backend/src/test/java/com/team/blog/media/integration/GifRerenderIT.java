package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.RerenderJob;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.config.CoreProperties;
import com.team.blog.support.IntegrationTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 규칙 2(작성자 GIF → 정지 장면 + 원본 링크)로 옛 발행 글을 다시 렌더링한다 (003 T074 US6, research R11). 002 {@code
 * RerenderJobIT}는 가짜 사진 판별기(다른 공개 주소)를 쓰는 설정이라 정화가 썸네일 주소를 지운다 — 실제 media 어댑터와 DB 행으로 여기서 확인한다.
 */
class GifRerenderIT extends IntegrationTestBase {

    @Autowired RerenderJob job;
    @Autowired CoreProperties core;

    @Test
    void 규칙_1_글의_작성자_GIF가_정지_장면_링크로_바뀌고_수정_흔적은_없다() {
        long me = members().member().create();
        String base = core.image().publicBaseUrl();
        String key = "images/2026/10/" + UUID.randomUUID() + ".gif";
        String thumb = key.replace(".gif", "_thumb.jpg");
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, width, height) VALUES (?, ?, ?, 'image/gif', 1000, 40, 30)",
                me,
                key,
                thumb);
        long postId =
                new AuthoringFixtures(jdbc, redis)
                        .publishedWithRenderVersion(me, 1, "![춤](" + base + "/" + key + ")");
        jdbc.update(
                "UPDATE post SET content_html = ? WHERE id = ?",
                "<p><img src=\"" + base + "/" + key + "\" alt=\"춤\" /></p>",
                postId);
        String untouchedSql =
                "SELECT edited_at, edit_version, updated_at, published_at, content_md FROM post"
                        + " WHERE id = ?";
        Map<String, Object> before = jdbc.queryForMap(untouchedSql, postId);

        job.rerender(RenderVersion.CURRENT);

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT content_html, render_version FROM post WHERE id = ?", postId);
        assertThat(row.get("render_version")).isEqualTo(RenderVersion.CURRENT);
        assertThat((String) row.get("content_html"))
                .contains("href=\"" + base + "/" + key + "\"")
                .contains("title=\"움직이는 이미지 재생\"")
                .contains("<img src=\"" + base + "/" + thumb + "\" alt=\"춤\"");
        assertThat(jdbc.queryForMap(untouchedSql, postId)).isEqualTo(before);
    }
}
