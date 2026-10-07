package com.team.blog.post.infra;

import com.team.blog.post.domain.EmptyDraftPolicy;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 배치용 네이티브 SQL (002 T115, data-model §1-1, EV §3). 다시 렌더링(T116)과 빈 임시글 정리(T117)가 쓴다. 새 인덱스는 만들지
 * 않는다.
 */
@Repository
public class PostMaintenanceRepository {

    private final JdbcClient jdbc;

    public PostMaintenanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 다시 렌더링 대상 한 묶음. {@code editVersion}은 읽은 순간의 버전(갱신 조건). */
    public record RerenderTarget(long id, long authorId, String contentMd, long editVersion) {}

    /** 규칙 버전이 {@code current}보다 낮은 발행 글을 {@code afterId} 다음부터 번호 순으로. */
    public List<RerenderTarget> findRerenderTargets(int current, long afterId, int limit) {
        return jdbc.sql(
                        "SELECT id, author_id, content_md, edit_version FROM post"
                                + " WHERE status = 'PUBLISHED' AND render_version < :cur"
                                + " AND id > :afterId ORDER BY id LIMIT :limit")
                .param("cur", current)
                .param("afterId", afterId)
                .param("limit", limit)
                .query(
                        (rs, i) ->
                                new RerenderTarget(
                                        rs.getLong("id"),
                                        rs.getLong("author_id"),
                                        rs.getString("content_md"),
                                        rs.getLong("edit_version")))
                .list();
    }

    /** 다시 렌더링해야 할 발행 글 수 (지표용). */
    public long countRerenderTargets(int current) {
        return jdbc.sql(
                        "SELECT count(*) FROM post WHERE status = 'PUBLISHED' AND render_version"
                                + " < :cur")
                .param("cur", current)
                .query(Long.class)
                .single();
    }

    /**
     * 렌더링 결과를 넣는다. 읽은 뒤 사용자가 다시 발행했으면({@code edit_version} 바뀜) 0행 — 이번 회차는 건너뛴다. {@code
     * updated_at}· {@code edited_at}·{@code edit_version}은 건드리지 않는다.
     *
     * @return 바뀐 행 수 (0 또는 1)
     */
    public int updateRendered(long id, String html, String excerpt, int current, long readVersion) {
        return jdbc.sql(
                        "UPDATE post SET content_html = :html, excerpt = :excerpt,"
                                + " render_version = :cur WHERE id = :id"
                                + " AND edit_version = :readVersion AND render_version < :cur")
                .param("html", html)
                .param("excerpt", excerpt)
                .param("cur", current)
                .param("id", id)
                .param("readVersion", readVersion)
                .update();
    }

    /**
     * 지울 빈 임시글 후보를 잠근다 (트랜잭션 안에서). 만든 지도 고친 지도 {@code age}가 지났고, 제목·본문이 {@link EmptyDraftPolicy}의
     * 공백 문자뿐인 휴지통 밖 임시글을 {@code afterId} 다음부터 번호 순으로. 다른 트랜잭션이 잡은 행은 건너뛴다.
     */
    public List<Long> lockEmptyDraftCandidates(Duration age, long afterId, int limit) {
        return jdbc.sql(
                        "SELECT p.id FROM post p WHERE p.status = 'DRAFT' AND "
                                + EmptyDraftPolicy.SQL_IS_EMPTY
                                + " AND p.created_at < now() - make_interval(secs => :age)"
                                + " AND p.updated_at < now() - make_interval(secs => :age)"
                                + " AND p.deleted_at IS NULL AND p.id > :afterId"
                                + " ORDER BY p.id LIMIT :limit"
                                + " FOR UPDATE SKIP LOCKED")
                .param("ws", EmptyDraftPolicy.sqlWhitespace())
                .param("age", (double) age.toSeconds())
                .param("afterId", afterId)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /** 완전 삭제 (딸린 행은 FK {@code ON DELETE CASCADE}). */
    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM post WHERE id = :id").param("id", id).update();
    }
}
