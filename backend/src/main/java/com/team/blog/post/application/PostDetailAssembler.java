package com.team.blog.post.application;

import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.post.application.port.AuthorFollowStatusQuery;
import com.team.blog.post.application.port.PostCategoryPathQuery;
import com.team.blog.post.application.port.PostCategoryPathQuery.CategoryPath;
import com.team.blog.post.application.port.PostLikeStatusQuery;
import com.team.blog.post.application.port.PostTagNamesQuery;
import com.team.blog.post.domain.Visibility;
import com.team.blog.post.infra.PostDetailRow;
import com.team.blog.shared.security.Viewer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 상세 행 → 응답 형태 (005 T035).
 *
 * <ul>
 *   <li>{@code displayedAt} = 공개 글이면 {@code first_public_at}, 그 밖은 {@code published_at}(research
 *       R-13).
 *   <li>{@code hasCodeBlock} = 본문에 {@code <pre><code}가 있는가.
 *   <li>주소는 002 {@link PostUrls}, 프로필 사진은 001 {@link ImageUrlResolver}로만 만든다.
 *   <li>좋아요·팔로우 포트는 비회원·작성자 본인에게는 부르지 않는다(SQL 절약, 결과가 늘 {@code false}).
 *   <li>부가 정보(태그·카테고리·좋아요·팔로우·작업본) 조회가 실패하면 기본값 + 경고 로그로 상세는 계속 보여 준다(원칙 V, research R-30).
 *   <li>작성자 본인에게만 {@code authorView}(작업본 유무·저장 시각, 숨김 여부·사유)를 채운다(005 T055, FR-038·039). 작업본 저장 시각은
 *       002 {@link PostDraftQueryService#findSavedAt}({@code post_draft.updated_at}, 본문은 읽지 않음)로
 *       읽는다 — 자동 저장은 Redis 버퍼를 거쳐 최대 약 1분 늦게 반영될 수 있다.
 * </ul>
 */
@Component
public class PostDetailAssembler {

    private static final Logger log = LoggerFactory.getLogger(PostDetailAssembler.class);

    private final ImageUrlResolver imageUrls;
    private final PostTagNamesQuery tagNames;
    private final PostLikeStatusQuery likeStatus;
    private final AuthorFollowStatusQuery followStatus;
    private final PostDraftQueryService drafts;
    private final PostCategoryPathQuery categoryPaths;

    public PostDetailAssembler(
            ImageUrlResolver imageUrls,
            PostTagNamesQuery tagNames,
            PostLikeStatusQuery likeStatus,
            AuthorFollowStatusQuery followStatus,
            PostDraftQueryService drafts,
            PostCategoryPathQuery categoryPaths) {
        this.imageUrls = imageUrls;
        this.tagNames = tagNames;
        this.likeStatus = likeStatus;
        this.followStatus = followStatus;
        this.drafts = drafts;
        this.categoryPaths = categoryPaths;
    }

    public PostDetailView toView(PostDetailRow row, Viewer viewer) {
        boolean isAuthor = viewer.isAuthorOf(row.authorId());
        boolean others = viewer.isAuthenticated() && !isAuthor;
        return new PostDetailView(
                row.id(),
                row.status().name(),
                null,
                PostUrls.of(row.handle(), row.id()),
                row.visibility().name(),
                row.title(),
                row.contentHtml(),
                hasCodeBlock(row.contentHtml()),
                displayedAt(row),
                row.firstPublicAt(),
                row.publishedAt(),
                row.editedAt(),
                tags(row.id()),
                category(row.id()),
                row.likeCount(),
                row.viewCount(),
                row.commentCount(),
                new PostDetailView.Author(
                        row.handle(),
                        row.nickname(),
                        imageUrls.publicUrl(row.profileKey()),
                        row.bio()),
                new PostDetailView.ViewerFlags(
                        viewer.isAuthenticated(),
                        isAuthor,
                        others && liked(row.id(), viewer.id()),
                        others && following(viewer.id(), row.authorId()),
                        viewer.emailVerified(),
                        viewer.isAdmin()),
                isAuthor ? authorView(row) : null);
    }

    /** 작성자 본인에게만 주는 상태 정보 (FR-038·039). 작업본 조회가 실패하면 "작업본 없음"으로 둔다. */
    private PostDetailView.AuthorView authorView(PostDetailRow row) {
        Optional<Instant> savedAt = guard(() -> drafts.findSavedAt(row.id()), "작업본");
        Instant draftSavedAt = savedAt == null ? null : savedAt.orElse(null);
        return new PostDetailView.AuthorView(
                draftSavedAt != null, draftSavedAt, row.isHidden(), row.hiddenReason());
    }

    /** 화면 날짜 기준 (research R-13). */
    static Instant displayedAt(PostDetailRow row) {
        return row.visibility() == Visibility.PUBLIC ? row.firstPublicAt() : row.publishedAt();
    }

    static boolean hasCodeBlock(String contentHtml) {
        return contentHtml != null && contentHtml.contains("<pre><code");
    }

    private List<String> tags(long postId) {
        List<String> names = guard(() -> tagNames.namesInOrder(postId), "태그");
        return names == null ? List.of() : names;
    }

    /** 017 카테고리 경로. 분류 없음·조회 실패면 {@code null}(원칙 V). */
    private CategoryPath category(long postId) {
        Optional<CategoryPath> path = guard(() -> categoryPaths.pathOf(postId), "카테고리");
        return path == null ? null : path.orElse(null);
    }

    private boolean liked(long postId, long memberId) {
        return guard(() -> likeStatus.isLikedBy(postId, memberId), "좋아요 여부");
    }

    private boolean following(long followerId, long followeeId) {
        return guard(() -> followStatus.isFollowing(followerId, followeeId), "팔로우 여부");
    }

    private boolean guard(BooleanSupplier query, String what) {
        return Boolean.TRUE.equals(guard((Supplier<Boolean>) query::getAsBoolean, what));
    }

    /** 부가 정보 조회 실패는 상세를 막지 않는다 — 기본값({@code null})으로 이어간다. */
    private <T> T guard(Supplier<T> query, String what) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            log.warn("글 상세의 {} 조회가 실패해 기본값으로 응답합니다: {}", what, e.toString());
            return null;
        }
    }
}
