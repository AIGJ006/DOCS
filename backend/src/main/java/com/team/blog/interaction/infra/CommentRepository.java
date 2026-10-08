package com.team.blog.interaction.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 댓글 쓰기 저장소 (007 T014, research R6·R7·R9). 잠금은 항상 "최상위 → 답글" 순서로 잡는다 — 교착이 없다. 로그·예외 메시지에 내용을 남기지
 * 않는다.
 */
@Repository
public class CommentRepository {

    private static final String SELECT = "SELECT " + CommentRow.COLUMNS + " FROM comment";

    private final JdbcClient jdbc;

    public CommentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 새 댓글. {@code created_at}·{@code updated_at}은 DB 기본값(트랜잭션 시작 시각). */
    public CommentRow insert(
            long postId, long authorId, Long parentId, Long replyToMemberId, String content) {
        return jdbc.sql(
                        "INSERT INTO comment (post_id, author_id, parent_id, reply_to_member_id,"
                                + " content) VALUES (:postId, :authorId, :parentId, :replyTo,"
                                + " :content) RETURNING "
                                + CommentRow.COLUMNS)
                .param("postId", postId)
                .param("authorId", authorId)
                .param("parentId", parentId)
                .param("replyTo", replyToMemberId)
                .param("content", content)
                .query(CommentRow::map)
                .single();
    }

    /** 잠금 없이 한 행. */
    public Optional<CommentRow> find(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(CommentRow::map)
                .optional();
    }

    /** 잠금 없이 내 댓글 (미리 확인용). */
    public Optional<CommentRow> findOwned(long id, long me) {
        return jdbc.sql(SELECT + " WHERE id = :id AND author_id = :me")
                .param("id", id)
                .param("me", me)
                .query(CommentRow::map)
                .optional();
    }

    public Optional<CommentRow> findForUpdate(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(CommentRow::map)
                .optional();
    }

    /** 내 댓글을 잠근다. 남의 댓글·없는 댓글은 빈 값(같은 404). */
    public Optional<CommentRow> findOwnedForUpdate(long id, long me) {
        return jdbc.sql(SELECT + " WHERE id = :id AND author_id = :me FOR UPDATE")
                .param("id", id)
                .param("me", me)
                .query(CommentRow::map)
                .optional();
    }

    /** 답글 작성: 최상위 행 공유 잠금 (삭제의 {@code FOR UPDATE}와 하나씩, FR-014). 그 글의 최상위가 아니면 빈 값. */
    public Optional<CommentRow> lockRootShared(long rootId, long postId) {
        return jdbc.sql(
                        SELECT
                                + " WHERE id = :id AND post_id = :postId AND parent_id IS NULL"
                                + " FOR SHARE")
                .param("id", rootId)
                .param("postId", postId)
                .query(CommentRow::map)
                .optional();
    }

    /** 대상 답글 공유 잠금 (최상위 다음). */
    public Optional<CommentRow> lockShared(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id FOR SHARE")
                .param("id", id)
                .query(CommentRow::map)
                .optional();
    }

    /** 삭제: 최상위 행 배타 잠금. */
    public Optional<CommentRow> lockRootForUpdate(long rootId) {
        return jdbc.sql(SELECT + " WHERE id = :id AND parent_id IS NULL FOR UPDATE")
                .param("id", rootId)
                .query(CommentRow::map)
                .optional();
    }

    public int countReplies(long rootId) {
        return jdbc.sql("SELECT count(*) FROM comment WHERE parent_id = :id")
                .param("id", rootId)
                .query(Integer.class)
                .single();
    }

    /** 답글 있는 최상위를 "삭제된 자리"로 — 내용을 비운다(FR-031). */
    public void markDeletedPlaceholder(long id, Instant now) {
        jdbc.sql("UPDATE comment SET content = '', deleted_at = :now WHERE id = :id")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM comment WHERE id = :id").param("id", id).update();
    }

    public CommentRow updateContent(long id, String content, Instant now) {
        return jdbc.sql(
                        "UPDATE comment SET content = :content, updated_at = GREATEST(:now,"
                                + " created_at) WHERE id = :id RETURNING "
                                + CommentRow.COLUMNS)
                .param("content", content)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("id", id)
                .query(CommentRow::map)
                .single();
    }

    /** 숨김 기록 (014가 {@code CommentModerationService}로 부름). */
    public void hide(long id, long adminId, String reason, Instant now) {
        jdbc.sql(
                        "UPDATE comment SET hidden_at = :now, hidden_by = :admin, hidden_reason ="
                                + " :reason WHERE id = :id")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("admin", adminId)
                .param("reason", reason)
                .param("id", id)
                .update();
    }

    public void unhide(long id) {
        jdbc.sql(
                        "UPDATE comment SET hidden_at = NULL, hidden_by = NULL, hidden_reason = NULL"
                                + " WHERE id = :id")
                .param("id", id)
                .update();
    }

    /** 댓글마다 숨김 여부 (014 처리됨 목록). 없는 번호는 결과에 없다. 빈 입력은 SQL 없이 빈 맵. */
    public Map<Long, Boolean> hiddenOf(Collection<Long> ids) {
        Map<Long, Boolean> result = new HashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT id, hidden_at IS NOT NULL AS hidden FROM comment WHERE id IN (:ids)")
                .param("ids", ids)
                .query(
                        (org.springframework.jdbc.core.RowCallbackHandler)
                                rs -> result.put(rs.getLong("id"), rs.getBoolean("hidden")));
        return result;
    }

    /** 트랜잭션이 끝날 때까지 이 키로 줄을 세운다 (10초 중복 방지, research R7). */
    public void advisoryLock(long key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", key).query().singleRow();
    }

    /** 같은 작성자·글·내용·자리(부모·대상)로 창 안에 만든 댓글 (가장 최근). */
    public Optional<CommentRow> findRecentDuplicate(
            long me,
            long postId,
            String content,
            Long parentId,
            Long replyToMemberId,
            Duration window) {
        return jdbc.sql(
                        SELECT
                                + " WHERE author_id = :me AND post_id = :postId AND content ="
                                + " :content AND parent_id IS NOT DISTINCT FROM CAST(:parentId AS"
                                + " bigint) AND reply_to_member_id IS NOT DISTINCT FROM"
                                + " CAST(:replyTo AS bigint) AND deleted_at IS NULL AND created_at >"
                                + " now() - CAST(:window AS interval) ORDER BY id DESC LIMIT 1")
                .param("me", me)
                .param("postId", postId)
                .param("content", content)
                .param("parentId", parentId)
                .param("replyTo", replyToMemberId)
                .param("window", window.toMillis() + " milliseconds")
                .query(CommentRow::map)
                .optional();
    }

    /** 중복 방지 잠금 키: SHA-256(작성자|글|내용|대상)의 앞 8바이트. */
    public static long dedupeKey(long me, long postId, String content, Long replyToCommentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash =
                    digest.digest(
                            (me + "|" + postId + "|" + replyToCommentId + "|" + content)
                                    .getBytes(StandardCharsets.UTF_8));
            long key = 0;
            for (int i = 0; i < 8; i++) {
                key = (key << 8) | (hash[i] & 0xFF);
            }
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- 탈퇴 정리 (contracts/events.md §2-2, 21 §11 SQL 2-a~2-d) ----

    /** 2-a 글별 감소량: 그 회원의 정상(삭제·숨김 아님) 댓글 수. */
    public List<PostCount> countNormalByPost(long memberId) {
        return jdbc.sql(
                        "SELECT post_id, count(*) AS n FROM comment WHERE author_id = :m AND"
                                + " deleted_at IS NULL AND hidden_at IS NULL GROUP BY post_id")
                .param("m", memberId)
                .query((rs, n) -> new PostCount(rs.getLong("post_id"), rs.getInt("n")))
                .list();
    }

    /** 2-b 남의 답글이 있는 내 최상위를 자리로. */
    public int placeholderRootsWithOthersReplies(long memberId, Instant now) {
        return jdbc.sql(
                        "UPDATE comment SET content = '', deleted_at = COALESCE(deleted_at, :now)"
                                + " WHERE author_id = :m AND parent_id IS NULL AND EXISTS (SELECT 1"
                                + " FROM comment r WHERE r.parent_id = comment.id AND r.author_id"
                                + " <> :m)")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .param("m", memberId)
                .update();
    }

    /** 2-c 나머지 내 댓글 삭제 (내 최상위 자리만 남긴다). 지운 답글의 부모 번호를 돌려준다. */
    public List<Long> deleteRestOf(long memberId) {
        return jdbc.sql(
                        "DELETE FROM comment WHERE author_id = :m AND NOT (parent_id IS NULL AND"
                                + " deleted_at IS NOT NULL) RETURNING parent_id")
                .param("m", memberId)
                .query((rs, n) -> (Long) rs.getObject("parent_id", Long.class))
                .list();
    }

    /** 2-d 빈 자리 정리 (내 최상위 자리 + 2-c에서 지운 답글의 부모). */
    public int deleteEmptyPlaceholders(long memberId, List<Long> parentIds) {
        StringBuilder ids = new StringBuilder("{");
        for (Long id : parentIds) {
            if (id == null) {
                continue;
            }
            if (ids.length() > 1) {
                ids.append(',');
            }
            ids.append(id);
        }
        return jdbc.sql(
                        "DELETE FROM comment c WHERE c.parent_id IS NULL AND c.deleted_at IS NOT"
                                + " NULL AND (c.author_id = :m OR c.id = ANY(CAST(:ids AS"
                                + " bigint[]))) AND NOT EXISTS (SELECT 1 FROM comment r WHERE"
                                + " r.parent_id = c.id)")
                .param("m", memberId)
                .param("ids", ids.append('}').toString())
                .update();
    }

    /** 글 하나의 개수. */
    public record PostCount(long postId, int count) {}
}
