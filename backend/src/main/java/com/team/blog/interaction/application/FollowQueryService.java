package com.team.blog.interaction.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.interaction.application.FollowListCursor.Position;
import com.team.blog.interaction.infra.FollowRepository;
import com.team.blog.interaction.infra.FollowRepository.FollowRow;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.ListScope;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팔로우 수·목록·팔로우 여부 (010 T019·T027·T034, contracts/follow-sql.md §6, research R5·R6). 다른 모듈(005 블로그
 * 머리말·010 피드·011 알림)이 쓰는 공개 Service다 — {@code follow} 테이블은 interaction 모듈 밖에서 직접 읽지 않는다(예외는 피드 카드
 * SQL, plan Complexity Tracking).
 */
@Service
@Transactional(readOnly = true)
public class FollowQueryService {

    private final FollowRepository follows;
    private final MemberQueryService members;
    private final FollowListCursor cursors;
    private final ImageUrlResolver imageUrls;
    private final FollowProperties properties;

    public FollowQueryService(
            FollowRepository follows,
            MemberQueryService members,
            FollowListCursor cursors,
            ImageUrlResolver imageUrls,
            FollowProperties properties) {
        this.follows = follows;
        this.members = members;
        this.cursors = cursors;
        this.imageUrls = imageUrls;
        this.properties = properties;
    }

    /**
     * 블로그 머리말의 팔로우 세 칸 (005 {@code BlogHeaderView} 확장).
     *
     * @param followerCount 팔로워 수 (탈퇴 유예 회원 제외)
     * @param followingCount 팔로잉 수 (탈퇴 유예 회원 제외)
     * @param followedByMe 보는 사람이 주인을 팔로우 중인가 (비회원·내 블로그면 {@code false})
     */
    public record HeaderStats(long followerCount, long followingCount, boolean followedByMe) {}

    /**
     * 목록 항목 (contracts {@code FollowListItem}). {@code isMe}는 JSON 이름을 그대로 쓴다.
     *
     * @param handle 블로그 주소
     * @param nickname 닉네임
     * @param profileImageUrl 작은 프로필 사진 주소 (없으면 {@code null})
     * @param bio 소개 원문 (화면은 첫 줄만)
     * @param followedByMe 보는 사람이 이 회원을 팔로우 중인가 (비회원은 {@code false})
     * @param isMe 보는 사람 자신인가
     */
    public record FollowListItem(
            String handle,
            String nickname,
            String profileImageUrl,
            String bio,
            boolean followedByMe,
            @JsonProperty("isMe") boolean isMe) {}

    /**
     * 목록 한 페이지 (contracts {@code FollowListPage}).
     *
     * @param nextCursor 더 있으면 다음 페이지 위치, 끝이면 {@code null}
     */
    public record FollowListPage(List<FollowListItem> items, String nextCursor) {}

    /** 팔로우 중인가 (PK {@code EXISTS} 1번). */
    public boolean isFollowing(long followerId, long followeeId) {
        return follows.exists(followerId, followeeId);
    }

    /** SQL: 수 2번 + 팔로우 여부 1번(로그인했고 내 블로그가 아닐 때만). */
    public HeaderStats headerStats(long ownerId, Viewer viewer) {
        long followerCount = follows.countFollowers(ownerId);
        long followingCount = follows.countFollowing(ownerId);
        boolean followedByMe =
                viewer != null
                        && viewer.isAuthenticated()
                        && viewer.id() != ownerId
                        && follows.exists(viewer.id(), ownerId);
        return new HeaderStats(followerCount, followingCount, followedByMe);
    }

    /** 팔로우한 사람이 한 명이라도 있는가 (피드 빈 화면 문구, 010 T027). */
    public boolean hasFollowing(long memberId) {
        return follows.hasFollowing(memberId);
    }

    /** 이 회원의 팔로워 번호 전부 — 탈퇴 유예 회원 제외 (다른 기능용, contracts §6). */
    public List<Long> followerIdsOf(long memberId) {
        return follows.followerIdsOf(memberId);
    }

    /**
     * 팔로워 목록 (누구나, 010 T034). SQL: 주인 1번 + 목록 1번 + 팔로우 여부 1번(로그인했고 항목이 있을 때만).
     *
     * @throws NotFoundException 없는 주소·탈퇴 유예·익명 처리
     */
    public FollowListPage followers(String handle, String cursor, Viewer viewer) {
        return list(handle, cursor, viewer, true);
    }

    /** 팔로잉 목록. {@link #followers}와 같은 규칙. */
    public FollowListPage following(String handle, String cursor, Viewer viewer) {
        return list(handle, cursor, viewer, false);
    }

    private FollowListPage list(String handle, String cursor, Viewer viewer, boolean followers) {
        BlogOwner owner =
                members.findReadableBlogOwner(handle)
                        .orElseThrow(() -> new NotFoundException("follow list owner not found"));
        ListScope scope =
                followers
                        ? ListScope.followers(owner.handle())
                        : ListScope.following(owner.handle());
        Position after = cursors.decode(cursor, scope);
        int pageSize = properties.listPageSize();
        List<FollowRow> rows =
                followers
                        ? follows.pageFollowers(
                                owner.id(),
                                after == null ? null : after.createdAt(),
                                after == null ? null : after.memberId(),
                                pageSize + 1)
                        : follows.pageFollowing(
                                owner.id(),
                                after == null ? null : after.createdAt(),
                                after == null ? null : after.memberId(),
                                pageSize + 1);
        boolean more = rows.size() > pageSize;
        List<FollowRow> page = more ? rows.subList(0, pageSize) : rows;
        Long viewerId = viewer != null && viewer.isAuthenticated() ? viewer.id() : null;
        Set<Long> followed =
                viewerId == null || page.isEmpty()
                        ? Set.of()
                        : follows.followedAmong(
                                viewerId, page.stream().map(FollowRow::memberId).toList());
        List<FollowListItem> items =
                page.stream()
                        .map(
                                row ->
                                        new FollowListItem(
                                                row.handle(),
                                                row.nickname(),
                                                imageUrls.publicUrl(row.profileKey()),
                                                row.bio(),
                                                followed.contains(row.memberId()),
                                                viewerId != null && viewerId == row.memberId()))
                        .toList();
        String nextCursor = null;
        if (more) {
            FollowRow last = page.get(page.size() - 1);
            nextCursor = cursors.encode(scope, last.createdAt(), last.memberId());
        }
        return new FollowListPage(items, nextCursor);
    }
}
