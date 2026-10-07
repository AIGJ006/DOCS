package com.team.blog.media.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 현재 프로필 사진 = uq_image_profile_current 조건(PROFILE·ATTACHED·detached_at IS NULL). */
class ProfileImageQueryIntegrationTest extends IntegrationTestBase {

    @Autowired ProfileImageQuery profileImageQuery;

    private void image(
            long uploader,
            String key,
            String thumb,
            String purpose,
            String status,
            boolean detached) {
        jdbc.update(
                "INSERT INTO image (uploader_id, storage_key, thumb_storage_key, content_type,"
                        + " size_bytes, status, purpose, detached_at)"
                        + " VALUES (?, ?, ?, 'image/png', 100, ?, ?, "
                        + (detached ? "now()" : "NULL")
                        + ")",
                uploader,
                key,
                thumb,
                status,
                purpose);
    }

    @Test
    void 현재_프로필_사진의_원본과_썸네일_키() {
        long me = members().member().create();
        image(me, "profile/old.png", null, "PROFILE", "ATTACHED", true);
        image(me, "profile/temp.png", null, "PROFILE", "TEMP", false);
        image(me, "post/1.png", "post/1-thumb.webp", "POST", "ATTACHED", false);
        image(me, "profile/now.png", null, "PROFILE", "ATTACHED", false);

        assertThat(profileImageQuery.currentKeys(me))
                .contains(new ProfileImageKeys("profile/now.png", null));
        assertThat(profileImageQuery.currentKeys(me).orElseThrow().display())
                .isEqualTo("profile/now.png");
    }

    @Test
    void 사진이_없으면_빈_값() {
        long me = members().member().create();
        image(me, "profile/old.png", null, "PROFILE", "ATTACHED", true);
        assertThat(profileImageQuery.currentKeys(me)).isEmpty();
    }

    @Test
    void 여러_회원을_SQL_1번으로_읽는다() {
        long a = members().member().create();
        long b = members().member().create();
        long c = members().member().create();
        image(a, "profile/a.png", "profile/a-thumb.webp", "PROFILE", "ATTACHED", false);
        image(b, "profile/b.png", null, "PROFILE", "ATTACHED", false);

        Map<Long, ProfileImageKeys> keys;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            keys = profileImageQuery.currentKeysOf(List.of(a, b, c));
            assertThat(scope.count()).isEqualTo(1);
        }
        assertThat(keys)
                .containsOnlyKeys(a, b)
                .containsEntry(a, new ProfileImageKeys("profile/a.png", "profile/a-thumb.webp"))
                .containsEntry(b, new ProfileImageKeys("profile/b.png", null));
        assertThat(keys.get(a).display()).isEqualTo("profile/a-thumb.webp");
        assertThat(profileImageQuery.currentKeysOf(List.of())).isEmpty();
    }
}
