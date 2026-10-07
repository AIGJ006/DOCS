package com.team.blog.shared.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.support.IntegrationTestBase;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Flyway V1(= docs/51 SQL 블록) + V2(shedlock)를 빈 DB에 적용한 결과가 51 §검증 결과의 카탈로그 수치와 같은지 확인한다. */
class FlywayBaselineIntegrationTest extends IntegrationTestBase {

    /** 업무 테이블만 (Flyway 이력·ShedLock 제외). */
    private static final String BUSINESS =
            "n.nspname = 'public' AND c.relname NOT IN ('flyway_schema_history', 'shedlock')";

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void 업무_테이블_20개와_shedlock() {
        assertThat(
                        count(
                                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                                        + " WHERE c.relkind = 'r' AND "
                                        + BUSINESS))
                .isEqualTo(20);
        assertThat(
                        count(
                                "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename = 'shedlock'"))
                .isEqualTo(1);
    }

    @Test
    void 업무_컬럼_148개() {
        assertThat(
                        count(
                                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                                        + " AND table_name NOT IN ('flyway_schema_history', 'shedlock')"))
                .isEqualTo(148);
    }

    @Test
    void 제약_PK_20_FK_40_UNIQUE_9_CHECK_52() {
        String base =
                "SELECT count(*) FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid"
                        + " JOIN pg_namespace n ON n.oid = c.relnamespace WHERE "
                        + BUSINESS
                        + " AND k.contype = ";
        assertThat(count(base + "'p'")).isEqualTo(20);
        assertThat(count(base + "'f'")).isEqualTo(40);
        assertThat(count(base + "'u'")).isEqualTo(9);
        assertThat(count(base + "'c'")).isEqualTo(52);
    }

    @Test
    void 별도_인덱스_42개_UNIQUE_인덱스_5개_GIN_4개() {
        String standalone =
                "FROM pg_index i JOIN pg_class ic ON ic.oid = i.indexrelid"
                        + " JOIN pg_class c ON c.oid = i.indrelid JOIN pg_namespace n ON n.oid = c.relnamespace"
                        + " JOIN pg_am am ON am.oid = ic.relam WHERE "
                        + BUSINESS
                        + " AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = i.indexrelid)";
        assertThat(count("SELECT count(*) " + standalone)).isEqualTo(42);
        assertThat(count("SELECT count(*) " + standalone + " AND i.indisunique")).isEqualTo(5);
        assertThat(count("SELECT count(*) " + standalone + " AND am.amname = 'gin'")).isEqualTo(4);
    }

    @Test
    void pg_trgm_확장이_있다() {
        assertThat(count("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'"))
                .isEqualTo(1);
    }

    @Test
    void 시각_컬럼은_모두_timestamptz() {
        String cols =
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'"
                        + " AND table_name NOT IN ('flyway_schema_history', 'shedlock') AND data_type = ";
        assertThat(count(cols + "'timestamp without time zone'")).isZero();
        assertThat(count(cols + "'timestamp with time zone'")).isEqualTo(41);
    }

    @Test
    void uq_member_nickname은_대소문자를_무시하고_중복을_막는다() {
        jdbc.update("INSERT INTO member (handle, nickname) VALUES ('kim_one', 'Kim')");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO member (handle, nickname) VALUES ('kim_two', 'kim')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"))
                .hasMessageContaining("uq_member_nickname");
    }

    @Test
    void ck_friendship_order는_a가_b보다_작아야_한다() {
        long a = members().member().create();
        long b = members().member().create();
        long small = Math.min(a, b);
        long big = Math.max(a, b);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO friendship (member_a_id, member_b_id, requested_by)"
                                                + " VALUES (?, ?, ?)",
                                        big,
                                        small,
                                        big))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_friendship_order");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO friendship (member_a_id, member_b_id, requested_by)"
                                                + " VALUES (?, ?, ?)",
                                        small,
                                        small,
                                        small))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_friendship_order");
    }

    @Test
    void uq_image_profile_current는_회원당_현재_프로필_사진_하나만_허용한다() {
        long m = members().member().create();
        String insert =
                "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height,"
                        + " status, purpose) VALUES (?, ?, 'image/webp', 1000, 256, 256, 'ATTACHED', 'PROFILE')";
        jdbc.update(insert, m, "profile/a.webp");
        assertThatThrownBy(() -> jdbc.update(insert, m, "profile/b.webp"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23505"))
                .hasMessageContaining("uq_image_profile_current");
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
