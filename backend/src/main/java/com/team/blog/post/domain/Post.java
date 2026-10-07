package com.team.blog.post.domain;

import com.team.blog.shared.application.markdown.RenderVersion;
import com.team.blog.shared.application.markdown.RenderedContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.SQLRestriction;

/**
 * 글 ({@code post}, 002 data-model §1-1, 51 §3).
 *
 * <p><b>소유·확장 규칙</b>: 이 클래스는 002가 만들고 소유한다. 다른 기능은 자기 상태 전이 메서드만 더한다 — 004 {@code
 * changeVisibility}, 006 {@code moveToTrash}·{@code restore}, 014 숨김. 새 메서드는 자기 소유 컬럼만 바꾸고, 반응
 * 수·{@code hidden_*}처럼 다른 기능이 소유한 값은 건드리지 않는다. 발행 → 임시글 전이 메서드는 두지 않는다(P-1, FR-033).
 *
 * <ul>
 *   <li>{@code edit_version}은 앱이 관리하는 편집 버전이다. JPA {@code @Version}을 쓰지 않는다(05 J-3) — 자동 저장(Redis
 *       Lua)·1분 반영(네이티브 SQL)과 같은 번호 체계를 쓰기 때문이다.
 *   <li>반응 수 3개는 다른 기능이 SQL로 바꾸므로 {@code insertable=false, updatable=false}다(05 J-2). 엔티티로 다른 값을 고쳐
 *       저장해도 덮이지 않는다.
 *   <li>{@code @SQLRestriction("deleted_at IS NULL")}: 휴지통 글은 엔티티 조회에서 빠진다. 휴지통 포함 조회는 006 {@code
 *       TrashPostRepository}, 002의 1분 반영은 네이티브 SQL(T077)이 맡는다.
 *   <li>{@code deleted_at}·{@code hidden_*}는 매핑만 하고 002는 바꾸지 않는다.
 * </ul>
 */
@Entity
@Table(name = "post")
@SQLRestriction("deleted_at IS NULL")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private long authorId;

    @Column(nullable = false, length = 100)
    private String title;

    /** Markdown 원문, ≤ 100,000자({@code ck_post_content}). */
    @Column(name = "content_md", nullable = false, columnDefinition = "text")
    private String contentMd;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(length = 200)
    private String excerpt;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    @Column(name = "view_count", nullable = false, insertable = false, updatable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
    private int commentCount;

    @Column(name = "edit_version", nullable = false)
    private long editVersion;

    @Column(name = "render_version", nullable = false)
    private int renderVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "first_public_at")
    private Instant firstPublicAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by")
    private Long hiddenBy;

    @Column(name = "hidden_reason", length = 30)
    private String hiddenReason;

    protected Post() {}

    private Post(
            long authorId, Visibility visibility, String title, String contentMd, Instant now) {
        this.authorId = authorId;
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.title = title == null ? "" : title;
        this.contentMd = contentMd == null ? "" : contentMd;
        this.contentHtml = "";
        this.status = PostStatus.DRAFT;
        this.editVersion = 0;
        this.renderVersion = RenderVersion.CURRENT;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    /**
     * [새 글] 임시글 (data-model §2: {@code edit_version = 0}, {@code content_html = ''}). 공개 범위는 호출하는
     * 쪽이 {@code member.default_visibility}로 정한다(DB 기본값에 기대지 않음).
     */
    public static Post newDraft(
            long authorId, Visibility visibility, String title, String contentMd, Instant now) {
        return new Post(authorId, visibility, title, contentMd, now);
    }

    /**
     * 발행·다시 발행 (data-model §2 의사 코드, 05 J-1, FR-031·032). 본문·HTML·요약·썸네일·공개 범위·{@code
     * render_version}·{@code updated_at}을 바꾸고 {@code edit_version = currentVersion + 1}로 둔다. 반응
     * 수·{@code hidden_*}·{@code deleted_at}은 건드리지 않는다.
     *
     * @param currentVersion 현재 편집 버전 = max(Redis, {@code post_draft}, {@code post}) — 호출하는 쪽이 확인한
     *     값(A-6 ④)
     */
    public PublishResult publish(
            PublishCommand cmd, RenderedContent rendered, long currentVersion, Instant now) {
        Objects.requireNonNull(now, "now");
        boolean firstPublish = this.publishedAt == null;
        boolean wentPublic = false;
        if (firstPublish) {
            this.publishedAt = now;
        } else {
            this.editedAt = now;
        }
        if (cmd.visibility() == Visibility.PUBLIC && this.firstPublicAt == null) {
            this.firstPublicAt = now;
            wentPublic = true;
        }
        this.status = PostStatus.PUBLISHED;
        this.visibility = Objects.requireNonNull(cmd.visibility(), "visibility");
        this.title = cmd.title();
        this.contentMd = cmd.contentMd();
        this.contentHtml = rendered.html();
        this.excerpt = rendered.excerpt();
        this.thumbnailUrl = rendered.thumbnailUrl();
        this.renderVersion = rendered.renderVersion();
        this.editVersion = currentVersion + 1;
        this.updatedAt = now;
        return new PublishResult(
                null, publishedAt, firstPublicAt, editedAt, editVersion, firstPublish, wentPublic);
    }

    public boolean isDraft() {
        return status == PostStatus.DRAFT;
    }

    public boolean isPublished() {
        return status == PostStatus.PUBLISHED;
    }

    /** 제목·본문이 모두 비어 있는 임시글인가 ({@link EmptyDraftPolicy}, 006 13 D-2와 같은 판정). */
    public boolean isEmptyDraft() {
        return isDraft() && EmptyDraftPolicy.isEmpty(title, contentMd);
    }

    public Long id() {
        return id;
    }

    public long authorId() {
        return authorId;
    }

    public String title() {
        return title;
    }

    public String contentMd() {
        return contentMd;
    }

    public String contentHtml() {
        return contentHtml;
    }

    public String excerpt() {
        return excerpt;
    }

    public String thumbnailUrl() {
        return thumbnailUrl;
    }

    public PostStatus status() {
        return status;
    }

    public Visibility visibility() {
        return visibility;
    }

    public long viewCount() {
        return viewCount;
    }

    public int likeCount() {
        return likeCount;
    }

    public int commentCount() {
        return commentCount;
    }

    public long editVersion() {
        return editVersion;
    }

    public int renderVersion() {
        return renderVersion;
    }

    public Instant publishedAt() {
        return publishedAt;
    }

    public Instant firstPublicAt() {
        return firstPublicAt;
    }

    public Instant editedAt() {
        return editedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Instant hiddenAt() {
        return hiddenAt;
    }

    public Long hiddenBy() {
        return hiddenBy;
    }

    public String hiddenReason() {
        return hiddenReason;
    }
}
