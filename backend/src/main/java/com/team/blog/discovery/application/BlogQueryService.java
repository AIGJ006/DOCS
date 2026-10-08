package com.team.blog.discovery.application;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.category.application.CategoryQueryService;
import com.team.blog.interaction.application.FollowQueryService;
import com.team.blog.interaction.application.FollowQueryService.HeaderStats;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import com.team.blog.tag.application.TagQueryService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 블로그 조회 (005 T048, US3, FR-019~022, research R-23·R-24).
 *
 * <ul>
 *   <li>주인은 001 {@link MemberQueryService#findReadableBlogOwner}로만 찾는다 — 없음·탈퇴 유예·익명 처리는 같은 404다.
 *       대문자 주소도 그대로 조회해 없음으로 처리한다(화면 경로가 먼저 301한다).
 *   <li>글 수(004 {@code PostQueryRepository.countListedByAuthor})·목록 조건은 홈과 같은 004 {@code
 *       VisibilityFilter}다 — <b>주인이 봐도</b> 자기 비공개·임시·숨김 글은 없다(06 V-8).
 *   <li>태그 필터(008)는 같은 목록 조건에 태그 조건만 더한다. 이름 확인은 008 {@link TagQueryService}로 한다.
 *   <li>프로필 사진은 001 {@link ProfileImageQuery} + {@link ImageUrlResolver}로만 만든다 — member·image를 직접
 *       읽지 않는다.
 *   <li>팔로워·팔로잉 수와 팔로우 여부(010)는 interaction {@link FollowQueryService#headerStats}로만 만든다 — {@code
 *       follow}를 직접 읽지 않는다.
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class BlogQueryService {

    private final MemberQueryService members;
    private final PostQueryRepository postQueries;
    private final ProfileImageQuery profileImages;
    private final ImageUrlResolver imageUrls;
    private final PostListService lists;
    private final TagQueryService tags;
    private final PostListCursor cursors;
    private final FollowQueryService follows;
    private final CategoryQueryService categories;

    public BlogQueryService(
            MemberQueryService members,
            PostQueryRepository postQueries,
            ProfileImageQuery profileImages,
            ImageUrlResolver imageUrls,
            PostListService lists,
            TagQueryService tags,
            PostListCursor cursors,
            FollowQueryService follows,
            CategoryQueryService categories) {
        this.members = members;
        this.postQueries = postQueries;
        this.profileImages = profileImages;
        this.imageUrls = imageUrls;
        this.lists = lists;
        this.tags = tags;
        this.cursors = cursors;
        this.follows = follows;
        this.categories = categories;
    }

    /**
     * @throws NotFoundException 없는 주소·탈퇴 유예·익명 처리된 회원 (이유 구분 없음)
     */
    public BlogOwner requireOwner(String handle) {
        return members.findReadableBlogOwner(handle)
                .orElseThrow(() -> new NotFoundException("blog owner not found: " + handle));
    }

    /** 머리말 — SQL은 주인 1번 + 글 수 1번 + 사진 1번 + 팔로우 수 2번 + 팔로우 여부 1번(010, 로그인했고 남의 블로그일 때만). */
    public BlogHeaderView getHeader(String handle, Viewer viewer) {
        BlogOwner owner = requireOwner(handle);
        HeaderStats stats = follows.headerStats(owner.id(), viewer);
        return new BlogHeaderView(
                owner.handle(),
                owner.nickname(),
                owner.bio(),
                imageUrls.publicUrl(displayImageKey(owner.id())),
                postQueries.countListedByAuthor(viewer, owner.id()),
                viewer.isAuthorOf(owner.id()),
                stats.followerCount(),
                stats.followingCount(),
                stats.followedByMe());
    }

    /** 블로그 글 목록 — SQL은 주인 1번 + 카드 1번. 커서 범위는 {@code blog:{handle}}이다. */
    public CursorPage<PostCardView> listPosts(String handle, String cursor, Viewer viewer) {
        return listPosts(handle, null, cursor, viewer);
    }

    /**
     * 블로그 글 목록, 태그 필터 포함 (008 T059, US5 #2). {@code tag}가 있으면 정규화된 이름이어야 하고(아니면 404 — API는 301 없음),
     * 커서 범위는 {@code blog:{handle}:tag:{name}}이다. 태그가 없으면 카드 SQL 없이 빈 페이지(커서 검사는 같다).
     *
     * @throws NotFoundException 없는 블로그, 정규화되지 않은 {@code tag}
     */
    public CursorPage<PostCardView> listPosts(
            String handle, String tag, String cursor, Viewer viewer) {
        BlogOwner owner = requireOwner(handle);
        if (tag == null) {
            return lists.page(
                    ListScope.blog(owner.handle()), CardFilter.author(owner.id()), cursor, viewer);
        }
        tags.requireCanonical(tag);
        ListScope scope = ListScope.blogTag(owner.handle(), tag);
        Optional<Long> tagId = tags.findIdByName(tag);
        if (tagId.isEmpty()) {
            cursors.decode(cursor, scope);
            return new CursorPage<>(List.of(), null);
        }
        return lists.page(scope, new CardFilter(owner.id(), tagId.get()), cursor, viewer);
    }

    /**
     * 블로그 글 목록, 카테고리 필터 (017 FR-030~FR-032). {@code category}가 이 블로그의 카테고리 번호가 아니면(형식 오류·없음·다른 블로그)
     * 404. 최상위 카테고리는 하위 글을 포함한다. 커서 범위는 {@code blog:{handle}:category:{id}}. SQL은 주인 1번 + 카테고리 1번 +
     * 카드 1번.
     *
     * @throws NotFoundException 없는 블로그, 이 블로그의 카테고리가 아닌 값
     */
    public CursorPage<PostCardView> listCategoryPosts(
            String handle, String category, String cursor, Viewer viewer) {
        BlogOwner owner = requireOwner(handle);
        List<Long> ids =
                categories
                        .findSubtreeIds(owner.id(), category)
                        .orElseThrow(
                                () -> new NotFoundException("category not found: " + category));
        ListScope scope = ListScope.blogCategory(owner.handle(), Long.parseLong(category));
        return lists.page(scope, CardFilter.authorInCategories(owner.id(), ids), cursor, viewer);
    }

    /** 작은 프로필 사진 키 (썸네일, 없으면 원본). 사진이 없으면 {@code null}. */
    private String displayImageKey(long ownerId) {
        return profileImages.currentKeys(ownerId).map(keys -> keys.display()).orElse(null);
    }
}
