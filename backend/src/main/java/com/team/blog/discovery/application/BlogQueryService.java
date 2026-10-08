package com.team.blog.discovery.application;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.post.infra.PostQueryRepository;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
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
 *   <li>프로필 사진은 001 {@link ProfileImageQuery} + {@link ImageUrlResolver}로만 만든다 — member·image를 직접
 *       읽지 않는다.
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

    public BlogQueryService(
            MemberQueryService members,
            PostQueryRepository postQueries,
            ProfileImageQuery profileImages,
            ImageUrlResolver imageUrls,
            PostListService lists) {
        this.members = members;
        this.postQueries = postQueries;
        this.profileImages = profileImages;
        this.imageUrls = imageUrls;
        this.lists = lists;
    }

    /**
     * @throws NotFoundException 없는 주소·탈퇴 유예·익명 처리된 회원 (이유 구분 없음)
     */
    public BlogOwner requireOwner(String handle) {
        return members.findReadableBlogOwner(handle)
                .orElseThrow(() -> new NotFoundException("blog owner not found: " + handle));
    }

    /** 머리말 — SQL은 주인 1번 + 글 수 1번 + 사진 1번. */
    public BlogHeaderView getHeader(String handle, Viewer viewer) {
        BlogOwner owner = requireOwner(handle);
        return new BlogHeaderView(
                owner.handle(),
                owner.nickname(),
                owner.bio(),
                imageUrls.publicUrl(displayImageKey(owner.id())),
                postQueries.countListedByAuthor(viewer, owner.id()),
                viewer.isAuthorOf(owner.id()));
    }

    /** 블로그 글 목록 — SQL은 주인 1번 + 카드 1번. 커서 범위는 {@code blog:{handle}}이다. */
    public CursorPage<PostCardView> listPosts(String handle, String cursor, Viewer viewer) {
        BlogOwner owner = requireOwner(handle);
        return lists.page(
                ListScope.blog(owner.handle()), CardFilter.author(owner.id()), cursor, viewer);
    }

    /** 작은 프로필 사진 키 (썸네일, 없으면 원본). 사진이 없으면 {@code null}. */
    private String displayImageKey(long ownerId) {
        return profileImages.currentKeys(ownerId).map(keys -> keys.display()).orElse(null);
    }
}
