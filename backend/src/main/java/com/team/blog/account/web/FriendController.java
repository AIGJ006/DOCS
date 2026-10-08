package com.team.blog.account.web;

import com.team.blog.account.application.FriendListPage;
import com.team.blog.account.application.FriendshipService;
import com.team.blog.account.web.dto.FriendItemResponse;
import com.team.blog.account.web.dto.FriendListResponse;
import com.team.blog.account.web.dto.FriendRequestItemResponse;
import com.team.blog.account.web.dto.FriendshipViewResponse;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 친구 API (FR-054~056). 관계는 {@code /api/members/{handle}/friend}, 목록은 본인 것만 {@code
 * /api/me/friends}·{@code /api/me/friend-requests} — 다른 회원의 친구 목록을 보는 경로는 없다. 모두 로그인 필요.
 */
@RestController
public class FriendController {

    private final FriendshipService friendshipService;

    public FriendController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    /** {@code getFriendship} */
    @GetMapping("/api/members/{handle}/friend")
    public FriendshipViewResponse view(@CurrentUser Long me, @PathVariable String handle) {
        return FriendshipViewResponse.of(friendshipService.view(me, handle));
    }

    /** {@code requestOrAcceptFriendship} — 요청 / 받은 요청 수락 / 변화 없음. */
    @PutMapping("/api/members/{handle}/friend")
    public FriendshipViewResponse requestOrAccept(
            @CurrentUser Long me, @PathVariable String handle) {
        return FriendshipViewResponse.of(friendshipService.requestOrAccept(me, handle));
    }

    /** {@code removeFriendship} — 거절 / 취소 / 끊기. 알리지 않는다. */
    @DeleteMapping("/api/members/{handle}/friend")
    public FriendshipViewResponse remove(@CurrentUser Long me, @PathVariable String handle) {
        return FriendshipViewResponse.of(friendshipService.remove(me, handle));
    }

    /** {@code listMyFriends} */
    @GetMapping("/api/me/friends")
    public FriendListResponse<FriendItemResponse> friends(
            @CurrentUser Long me, @RequestParam(required = false) String cursor) {
        FriendListPage page = friendshipService.listFriends(me, cursor);
        return new FriendListResponse<>(
                page.items().stream().map(FriendItemResponse::of).toList(), page.nextCursor());
    }

    /** {@code listReceivedFriendRequests} */
    @GetMapping("/api/me/friend-requests")
    public FriendListResponse<FriendRequestItemResponse> requests(
            @CurrentUser Long me, @RequestParam(required = false) String cursor) {
        FriendListPage page = friendshipService.listReceivedRequests(me, cursor);
        return new FriendListResponse<>(
                page.items().stream().map(FriendRequestItemResponse::of).toList(),
                page.nextCursor());
    }
}
