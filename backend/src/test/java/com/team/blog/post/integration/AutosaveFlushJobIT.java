package com.team.blog.post.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.application.AutosaveFlushJob;
import com.team.blog.post.infra.RedisAutosaveStore;
import com.team.blog.post.support.AuthoringFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 1분 반영 배치 (002 T067, FR-007 ③, EV §3, B-3 ③·⑥). 배치 메서드를 직접 부른다(테스트 프로필은 주기를 길게 둔다). */
class AutosaveFlushJobIT extends IntegrationTestBase {

    private static final String BASE = "http://localhost:9000/blog";
    private static final String KEY = "images/2026/10/0b6e1f5c-2a3d-4f7e-9c1a-5d8e7f6a1b2c.webp";
    private static final Instant SAVED_AT = Instant.parse("2026-10-07T05:03:12.123456Z");

    @Autowired AutosaveFlushJob job;
    @Autowired RedisAutosaveStore store;

    private AuthoringFixtures fixtures() {
        return new AuthoringFixtures(jdbc, redis);
    }

    @Test
    void dirty_임시글은_post에_반영하고_키는_남기고_dirty에서_뺀다() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).title("옛").contentMd("옛").editVersion(2).create();
        fixtures().putAutosave(postId, me, "새 제목", "새 본문", 5, SAVED_AT, true);

        job.flush();

        Map<String, Object> row =
                jdbc.queryForMap(
                        "SELECT title, content_md, edit_version, updated_at FROM post WHERE id = ?",
                        postId);
        assertThat(row)
                .containsEntry("title", "새 제목")
                .containsEntry("content_md", "새 본문")
                .containsEntry("edit_version", 5L);
        assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(SAVED_AT);
        assertThat(fixtures().autosaveHash(postId)).containsEntry("version", "5");
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void DB가_더_새로우면_덮지_않는다() throws Exception {
        long me = members().member().create();
        long postId =
                fixtures().posts().post(me).title("DB").contentMd("DB").editVersion(7).create();
        fixtures().putAutosave(postId, me, "옛 Redis", "옛 Redis", 5, SAVED_AT, true);

        job.flush();

        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("DB");
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void 발행_글은_작업본에_UPSERT하고_발행본은_그대로() throws Exception {
        long me = members().member().create();
        long postId = fixtures().publishedWithWorkingCopy(me, "옛 작업본", "옛 작업본");
        AuthoringFixtures.Snapshot before = fixtures().snapshot(postId).orElseThrow();
        fixtures().putAutosave(postId, me, "새 작업본", "새 작업본", 4, SAVED_AT, true);

        job.flush();

        assertThat(fixtures().snapshot(postId)).contains(before);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT title, edit_version FROM post_draft WHERE post_id = ?",
                                postId))
                .containsEntry("title", "새 작업본")
                .containsEntry("edit_version", 4L);
    }

    @Test
    void 발행본_버전보다_작은_보관분은_작업본을_만들지_않는다() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).published("PUBLIC").editVersion(3).create();
        fixtures().putAutosave(postId, me, "옛", "옛", 3, SAVED_AT, true);

        job.flush();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM post_draft WHERE post_id = ?",
                                Long.class,
                                postId))
                .isZero();
        assertThat(fixtures().isDirty(postId)).isFalse();
    }

    @Test
    void 반영한_뒤_키_버전이_바뀌었으면_dirty를_유지한다() {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).create();
        // 배치가 v1을 읽어 반영하는 사이 다른 탭이 v2를 저장한 상황: v1 기준 정리는 dirty를 빼지 않는다
        fixtures().putAutosave(postId, me, "v2", "v2", 2, SAVED_AT, true);

        store.clearDirtyIfVersion(postId, 1);
        assertThat(fixtures().isDirty(postId)).isTrue();

        store.clearDirtyIfVersion(postId, 2);
        assertThat(fixtures().isDirty(postId)).isFalse();
        assertThat(fixtures().autosaveHash(postId)).containsEntry("version", "2");
    }

    @Test
    void 휴지통_글도_반영한다() throws Exception {
        long me = members().member().create();
        long postId = fixtures().posts().post(me).title("옛").contentMd("옛").trashed().create();
        fixtures().putAutosave(postId, me, "휴지통 전 마지막 입력", "본문", 1, SAVED_AT, true);

        job.flush();

        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("휴지통 전 마지막 입력");
    }

    @Test
    void 완전_삭제된_글이면_키와_dirty를_지운다() throws Exception {
        long me = members().member().create();
        long gone = fixtures().posts().nonexistentId();
        fixtures().putAutosave(gone, me, "없는 글", "본문", 3, SAVED_AT, true);

        job.flush();

        assertThat(fixtures().autosaveHash(gone)).isEmpty();
        assertThat(fixtures().isDirty(gone)).isFalse();
    }

    @Test
    void 한_글의_실패가_다른_글_반영을_막지_않는다() throws Exception {
        long me = members().member().create();
        long broken = fixtures().posts().post(me).create();
        // 제목 101자: varchar(100) 위반으로 이 글의 반영만 실패한다
        fixtures().putAutosave(broken, me, "가".repeat(101), "본문", 1, SAVED_AT, true);
        long ok = fixtures().posts().post(me).create();
        fixtures().putAutosave(ok, me, "정상", "본문", 1, SAVED_AT, true);

        job.flush();

        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?", String.class, ok))
                .isEqualTo("정상");
        assertThat(fixtures().isDirty(ok)).isFalse();
        assertThat(fixtures().isDirty(broken)).isTrue();
    }

    @Test
    void 반영_때_작성자_사진을_연결한다() throws Exception {
        long me = members().member().create();
        long imageId =
                jdbc.queryForObject(
                        "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes)"
                                + " VALUES (?, ?, 'image/webp', 1000) RETURNING id",
                        Long.class,
                        me,
                        KEY);
        long postId = fixtures().posts().post(me).create();
        fixtures()
                .putAutosave(postId, me, "사진", "![a](" + BASE + "/" + KEY + ")", 1, SAVED_AT, true);

        job.flush();

        assertThat(
                        jdbc.queryForList(
                                "SELECT image_id FROM post_image WHERE post_id = ?",
                                Long.class,
                                postId))
                .containsExactly(imageId);
    }

    @Test
    void ShedLock_이름은_autosave_flush() throws Exception {
        job.flush();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM shedlock WHERE name = 'autosave-flush'",
                                Long.class))
                .isEqualTo(1);
        Instant lockUntil =
                jdbc.queryForObject(
                                "SELECT lock_until FROM shedlock WHERE name = 'autosave-flush'",
                                Timestamp.class)
                        .toInstant();
        assertThat(lockUntil).isBefore(Instant.now().plus(1, ChronoUnit.SECONDS));
    }
}
