package com.team.blog.support.fixture;

import com.team.blog.support.MemberFixtures;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 글 테스트 데이터 (004 T021). 002 엔드포인트 없이 JdbcTemplate으로 {@code post}·{@code post_draft}를 직접 넣는다. 회원은
 * 001 {@link MemberFixtures}로 만든다.
 *
 * <pre>{@code
 * long author = members().member().create();
 * long id = posts().create(author, PostFixtures.State.PUBLISHED_PRIVATE);
 * long draft = posts().post(author).title("임시").contentMd("본문").create();
 * long editing = posts().post(author).published(Visibility.PUBLIC).draft("고친 제목", "고친 본문").create();
 * }</pre>
 *
 * CHECK {@code ck_post_public_at}·{@code ck_post_published}·{@code ck_post_edited_at}·{@code
 * ck_member_withdrawn}을 만족하는 값을 만든다(004 data-model §1).
 */
public final class PostFixtures {

    /** 권한 매트릭스의 글 상태 (42 §5-1). */
    public enum State {
        /** 발행 · 전체 공개 ({@code published_at}·{@code first_public_at} 채움). */
        PUBLISHED_PUBLIC,
        /** 발행 · 나만 보기 ({@code first_public_at} NULL). */
        PUBLISHED_PRIVATE,
        /** 발행 · 전체 공개 + 작업본({@code post_draft}) 있음 = 수정 중. */
        EDITING,
        /** 임시글. */
        DRAFT,
        /** 휴지통 (발행 · 전체 공개였던 글 + {@code deleted_at}). */
        TRASHED,
        /** 관리자 숨김 (발행 · 전체 공개 + {@code hidden_at}·{@code hidden_by}). */
        HIDDEN,
        /** 발행 · 전체 공개인데 작성자가 탈퇴 유예({@code member.status = WITHDRAWN}). 작성자 회원을 바꾼다. */
        AUTHOR_WITHDRAWN
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final MemberFixtures members;

    public PostFixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.members = new MemberFixtures(jdbc);
    }

    /** 그 상태의 글을 만들고 글 번호를 돌려준다. {@link State#AUTHOR_WITHDRAWN}은 작성자를 탈퇴 유예로 바꾼다. */
    public long create(long authorId, State state) {
        return switch (state) {
            case PUBLISHED_PUBLIC -> post(authorId).published("PUBLIC").create();
            case PUBLISHED_PRIVATE -> post(authorId).published("PRIVATE").create();
            case EDITING ->
                    post(authorId).published("PUBLIC").draft("고치는 중인 제목", "고치는 중인 본문").create();
            case DRAFT -> post(authorId).create();
            case TRASHED -> post(authorId).published("PUBLIC").trashed().create();
            case HIDDEN -> post(authorId).published("PUBLIC").hidden().create();
            case AUTHOR_WITHDRAWN -> {
                long id = post(authorId).published("PUBLIC").create();
                withdraw(authorId);
                yield id;
            }
        };
    }

    /** 회원을 탈퇴 유예로 바꾼다 ({@code ck_member_withdrawn}: status와 withdrawn_at을 함께). */
    public void withdraw(long memberId) {
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()),
                memberId);
    }

    /** 어느 글 번호와도 겹치지 않는 번호 (없는 글). */
    public long nonexistentId() {
        Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM post", Long.class);
        return max + 1_000_000L;
    }

    public Builder post(long authorId) {
        return new Builder(authorId);
    }

    public final class Builder {
        private final long authorId;
        private final int n = SEQ.incrementAndGet();
        private String title;
        private String contentMd;
        private String contentHtml;
        private String status = "DRAFT";
        private String visibility = "PUBLIC";
        private long editVersion;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant publishedAt;
        private Instant firstPublicAt;
        private Instant editedAt;
        private Instant deletedAt;
        private Instant hiddenAt;
        private Long hiddenBy;
        private String draftTitle;
        private String draftContentMd;
        private Long draftVersion;

        private Builder(long authorId) {
            this.authorId = authorId;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder contentMd(String contentMd) {
            this.contentMd = contentMd;
            return this;
        }

        public Builder contentHtml(String contentHtml) {
            this.contentHtml = contentHtml;
            return this;
        }

        /** 임시글의 공개 범위(발행 때 쓸 값) 또는 발행 글의 공개 범위. */
        public Builder visibility(String visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder editVersion(long editVersion) {
            this.editVersion = editVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * 발행 글로 만든다: {@code published_at} = 하루 전, {@code PUBLIC}이면 {@code first_public_at}도 같은 값,
         * {@code edit_version} = 1(최초 발행 +1).
         */
        public Builder published(String visibility) {
            Instant at = Instant.now().minus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
            this.status = "PUBLISHED";
            this.visibility = visibility;
            this.publishedAt = at;
            this.firstPublicAt = "PUBLIC".equals(visibility) ? at : null;
            this.editVersion = Math.max(editVersion, 1);
            return this;
        }

        /** 최초 공개 시각을 직접 준다 (비공개로 바꾼 뒤에도 남는 과거 값 등). */
        public Builder firstPublicAt(Instant firstPublicAt) {
            this.firstPublicAt = firstPublicAt;
            return this;
        }

        /** 다시 발행한 시각 ({@code ck_post_edited_at}: {@code published_at} 이후). */
        public Builder editedAt(Instant editedAt) {
            this.editedAt = editedAt;
            return this;
        }

        /** 작업본({@code post_draft})을 함께 만든다. 버전은 글 버전 + 1. */
        public Builder draft(String title, String contentMd) {
            this.draftTitle = title;
            this.draftContentMd = contentMd;
            return this;
        }

        public Builder draftVersion(long draftVersion) {
            this.draftVersion = draftVersion;
            return this;
        }

        /** 휴지통으로 옮긴 상태 (1시간 전). */
        public Builder trashed() {
            this.deletedAt = Instant.now().minus(Duration.ofHours(1));
            return this;
        }

        /** 관리자가 숨긴 상태. 숨긴 관리자가 없으면 만든다. */
        public Builder hidden() {
            this.hiddenAt = Instant.now().minus(Duration.ofHours(1));
            return this;
        }

        public long create() {
            boolean published = "PUBLISHED".equals(status);
            String t = title != null ? title : (published ? "발행 글 " + n : "");
            String md = contentMd != null ? contentMd : (published ? "본문 " + n : "");
            String html =
                    contentHtml != null ? contentHtml : (published ? "<p>" + md + "</p>" : "");
            Instant now = Instant.now();
            Instant created = createdAt != null ? createdAt : now;
            Instant updated = updatedAt != null ? updatedAt : created;
            Long hider = hiddenAt == null ? null : (hiddenBy != null ? hiddenBy : admin());
            Long id =
                    jdbc.queryForObject(
                            "INSERT INTO post (author_id, title, content_md, content_html, excerpt,"
                                    + " status, visibility, edit_version, published_at,"
                                    + " first_public_at, edited_at, created_at, updated_at,"
                                    + " deleted_at, hidden_at, hidden_by, hidden_reason)"
                                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                                    + " RETURNING id",
                            Long.class,
                            authorId,
                            t,
                            md,
                            html,
                            published ? md : null,
                            status,
                            visibility,
                            editVersion,
                            ts(publishedAt),
                            ts(firstPublicAt),
                            ts(editedAt),
                            ts(created),
                            ts(updated),
                            ts(deletedAt),
                            ts(hiddenAt),
                            hider,
                            hiddenAt == null ? null : "SPAM");
            if (draftTitle != null || draftContentMd != null) {
                long v = draftVersion != null ? draftVersion : editVersion + 1;
                jdbc.update(
                        "INSERT INTO post_draft (post_id, title, content_md, edit_version)"
                                + " VALUES (?, ?, ?, ?)",
                        id,
                        draftTitle == null ? "" : draftTitle,
                        draftContentMd == null ? "" : draftContentMd,
                        v);
            }
            return id;
        }

        private long admin() {
            Long existing =
                    jdbc.query(
                            "SELECT id FROM member WHERE role = 'ADMIN' AND status = 'ACTIVE'"
                                    + " ORDER BY id LIMIT 1",
                            rs -> rs.next() ? rs.getLong(1) : null);
            return existing != null ? existing : members.member().role("ADMIN").create();
        }
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
