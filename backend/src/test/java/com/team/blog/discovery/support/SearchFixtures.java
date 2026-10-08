package com.team.blog.discovery.support;

import com.team.blog.support.fixture.PostFixtures;
import java.io.StringReader;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 트렌딩·검색 테스트 데이터 (012 T008). 004 {@link PostFixtures}로 공개 글을 만들고 제목·본문·태그·최초 공개 시각·좋아요 수·조회 수·댓글
 * 작성자를 한 번에 정한다. 대량 넣기는 {@link #bulk}({@code COPY}).
 *
 * <pre>{@code
 * long id = search().post(author).title("트랜잭션 정리").content("본문").tags("spring", "jpa")
 *         .firstPublicAt(now.minus(Duration.ofHours(1))).likes(3).views(10).commenters(b, c).create();
 * search().hiddenComment(id, d);
 * }</pre>
 */
public final class SearchFixtures {

    private final JdbcTemplate jdbc;
    private final PostFixtures posts;

    public SearchFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.posts = new PostFixtures(jdbc);
    }

    public Builder post(long authorId) {
        return new Builder(authorId);
    }

    /** 보이는 댓글 하나 (작성자 {@code memberId}). */
    public long comment(long postId, long memberId) {
        return insertComment(postId, memberId, null, null);
    }

    /** 관리자가 숨긴 댓글. */
    public long hiddenComment(long postId, long memberId) {
        Long admin =
                jdbc.query(
                        "SELECT id FROM member WHERE role = 'ADMIN' ORDER BY id LIMIT 1",
                        rs -> rs.next() ? rs.getLong(1) : null);
        long hider = admin != null ? admin : memberId;
        return insertComment(postId, memberId, null, hider);
    }

    /** 삭제된 자리(답글이 있어 남은 댓글). */
    public long deletedComment(long postId, long memberId) {
        return insertComment(postId, memberId, Instant.now(), null);
    }

    private long insertComment(long postId, long memberId, Instant deletedAt, Long hiddenBy) {
        return jdbc.queryForObject(
                "INSERT INTO comment (post_id, author_id, content, deleted_at, hidden_at, hidden_by,"
                        + " hidden_reason) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class,
                postId,
                memberId,
                "댓글",
                deletedAt == null ? null : Timestamp.from(deletedAt),
                hiddenBy == null ? null : Timestamp.from(Instant.now()),
                hiddenBy,
                hiddenBy == null ? null : "SPAM");
    }

    /** 태그 이름(정규화된 값)으로 연결한다. 없으면 태그를 만든다. */
    public void tag(long postId, String... names) {
        Integer next =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(position) + 1, 0) FROM post_tag WHERE post_id = ?",
                        Integer.class,
                        postId);
        int position = next == null ? 0 : next;
        for (String name : names) {
            Long tagId =
                    jdbc.queryForObject(
                            "INSERT INTO tag (name) VALUES (?) ON CONFLICT (name)"
                                    + " DO UPDATE SET name = EXCLUDED.name RETURNING id",
                            Long.class,
                            name);
            jdbc.update(
                    "INSERT INTO post_tag (post_id, tag_id, position) VALUES (?, ?, ?)",
                    postId,
                    tagId,
                    position++);
        }
    }

    /** 대량 넣기 한 줄 (공개 글). */
    public record BulkPost(long authorId, String title, String content, Instant firstPublicAt) {}

    /**
     * {@code COPY}로 공개 글을 한꺼번에 넣는다(성능 시험용). 요약은 본문 앞 100자.
     *
     * @return 넣은 줄 수
     */
    public long bulk(List<BulkPost> rows) {
        StringBuilder csv = new StringBuilder(rows.size() * 256);
        for (BulkPost row : rows) {
            String at = row.firstPublicAt().truncatedTo(ChronoUnit.MICROS).toString();
            String excerpt =
                    row.content().length() > 100 ? row.content().substring(0, 100) : row.content();
            csv.append(row.authorId())
                    .append(',')
                    .append(quote(row.title()))
                    .append(',')
                    .append(quote(row.content()))
                    .append(',')
                    .append(quote(excerpt))
                    .append(",PUBLISHED,PUBLIC,1,")
                    .append(at)
                    .append(',')
                    .append(at)
                    .append('\n');
        }
        String sql =
                "COPY post (author_id, title, content_md, excerpt, status, visibility, edit_version,"
                        + " published_at, first_public_at) FROM STDIN WITH (FORMAT csv)";
        return jdbc.execute(
                (ConnectionCallback<Long>)
                        connection -> {
                            try {
                                return connection
                                        .unwrap(PGConnection.class)
                                        .getCopyAPI()
                                        .copyIn(sql, new StringReader(csv.toString()));
                            } catch (java.io.IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public final class Builder {
        private final long authorId;
        private String title;
        private String content;
        private final List<String> tags = new ArrayList<>();
        private Instant firstPublicAt;
        private int likes;
        private long views;
        private final List<Long> commenters = new ArrayList<>();
        private PostFixtures.State state = PostFixtures.State.PUBLISHED_PUBLIC;
        private Instant editedAt;

        private Builder(long authorId) {
            this.authorId = authorId;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder tags(String... names) {
            tags.addAll(List.of(names));
            return this;
        }

        public Builder firstPublicAt(Instant at) {
            this.firstPublicAt = at.truncatedTo(ChronoUnit.MICROS);
            return this;
        }

        /** 최초 공개를 지금부터 이만큼 전으로. */
        public Builder age(Duration age) {
            return firstPublicAt(Instant.now().minus(age));
        }

        public Builder likes(int likes) {
            this.likes = likes;
            return this;
        }

        public Builder views(long views) {
            this.views = views;
            return this;
        }

        /** 이 회원들이 댓글을 하나씩 단다. */
        public Builder commenters(long... memberIds) {
            for (long id : memberIds) {
                commenters.add(id);
            }
            return this;
        }

        /** 글 상태 (기본 발행·전체 공개). */
        public Builder state(PostFixtures.State state) {
            this.state = state;
            return this;
        }

        /** 다시 발행한 시각. */
        public Builder editedAt(Instant editedAt) {
            this.editedAt = editedAt.truncatedTo(ChronoUnit.MICROS);
            return this;
        }

        public long create() {
            PostFixtures.Builder b = posts.post(authorId);
            b =
                    switch (state) {
                        case PUBLISHED_PUBLIC, AUTHOR_WITHDRAWN -> b.published("PUBLIC");
                        case PUBLISHED_PRIVATE -> b.published("PRIVATE");
                        case DRAFT -> b;
                        case TRASHED -> b.published("PUBLIC").trashed();
                        case HIDDEN -> b.published("PUBLIC").hidden();
                        case EDITING -> b.published("PUBLIC").draft("고치는 중인 제목", "고치는 중인 본문");
                    };
            if (title != null) {
                b.title(title);
            }
            if (content != null) {
                b.contentMd(content);
            }
            Instant at = firstPublicAt;
            if (at != null && state != PostFixtures.State.DRAFT) {
                b.firstPublicAt(at);
            }
            long id = b.create();
            if (at != null && state != PostFixtures.State.DRAFT) {
                // published_at도 최초 공개 시각에 맞춘다 (ck_post_edited_at: edited_at >= published_at)
                jdbc.update(
                        "UPDATE post SET published_at = ? WHERE id = ?", Timestamp.from(at), id);
            }
            if (editedAt != null) {
                jdbc.update(
                        "UPDATE post SET edited_at = ? WHERE id = ?", Timestamp.from(editedAt), id);
            }
            if (likes != 0 || views != 0 || !commenters.isEmpty()) {
                jdbc.update(
                        "UPDATE post SET like_count = ?, view_count = ?, comment_count = ? WHERE id = ?",
                        likes,
                        views,
                        commenters.size(),
                        id);
            }
            for (long member : commenters) {
                comment(id, member);
            }
            if (!tags.isEmpty()) {
                tag(id, tags.toArray(String[]::new));
            }
            if (state == PostFixtures.State.AUTHOR_WITHDRAWN) {
                posts.withdraw(authorId);
            }
            return id;
        }
    }
}
