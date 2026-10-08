package com.team.blog.account.application;

import com.team.blog.account.domain.Friendship;
import com.team.blog.account.domain.FriendshipStatus;
import com.team.blog.account.infra.FriendListQueryRepository;
import com.team.blog.account.infra.FriendshipRepository;
import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.media.application.ProfileImageKeys;
import com.team.blog.media.application.ProfileImageQuery;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.FriendAccepted;
import com.team.blog.shared.event.FriendRequested;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 친구 맺기 (FR-054~056, C-FRIEND-1, R-27, contracts/events.md §1).
 *
 * <ul>
 *   <li>요청·수락({@link #requestOrAccept}): 대상은 읽을 수 있는 블로그 주인(없음·탈퇴 유예·익명 처리 → 404), 자기 자신 400 {@code
 *       CANNOT_FRIEND_SELF}, {@code AccountStatusGuard(ACCOUNT_WRITE)}(이메일 인증 불필요). 새 행이면 {@link
 *       FriendRequested}, PENDING → ACCEPTED면 {@link FriendAccepted}, 변화 없으면 이벤트 없음(EV-4).
 *   <li>거절·취소·끊기({@link #remove}): 행 삭제, 상대에게 알리지 않고 이벤트도 없다. 관계가 없어도 같은 결과.
 *   <li>목록: 본인 것만(주소에 회원 값이 없다), 커서 {@code (시각 µs, 상대 ID)}, 페이지당 SQL 1번 + 프로필 사진 1번. 친구 목록의 최근 활동은
 *       목록 쿼리가 함께 읽는다(US8).
 * </ul>
 */
@Service
public class FriendshipService {

    static final int PAGE_SIZE = 20;

    private final MemberQueryService memberQuery;
    private final FriendshipRepository friendships;
    private final FriendListQueryRepository lists;
    private final AccountStatusGuard statusGuard;
    private final ProfileImageQuery profileImageQuery;
    private final ImageUrlResolver imageUrlResolver;
    private final CursorCodec cursorCodec;
    private final ApplicationEventPublisher events;
    private final LastActiveQueryService lastActiveQuery;
    private final Clock clock;

    public FriendshipService(
            MemberQueryService memberQuery,
            FriendshipRepository friendships,
            FriendListQueryRepository lists,
            AccountStatusGuard statusGuard,
            ProfileImageQuery profileImageQuery,
            ImageUrlResolver imageUrlResolver,
            CursorCodec cursorCodec,
            ApplicationEventPublisher events,
            LastActiveQueryService lastActiveQuery,
            Clock clock) {
        this.memberQuery = memberQuery;
        this.friendships = friendships;
        this.lists = lists;
        this.statusGuard = statusGuard;
        this.profileImageQuery = profileImageQuery;
        this.imageUrlResolver = imageUrlResolver;
        this.cursorCodec = cursorCodec;
        this.events = events;
        this.lastActiveQuery = lastActiveQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public FriendshipView view(long me, String handle) {
        long other = target(handle);
        return withLastActive(me, other, state(me, other));
    }

    @Transactional
    public FriendshipView requestOrAccept(long me, String handle) {
        long other = target(handle);
        if (other == me) {
            throw new BusinessRuleException(AccountReasonCode.CANNOT_FRIEND_SELF);
        }
        statusGuard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        Optional<FriendshipRepository.UpsertResult> result = friendships.requestOrAccept(me, other);
        Instant now = clock.instant();
        result.ifPresent(
                changed -> {
                    if (changed.inserted()) {
                        events.publishEvent(new FriendRequested(me, other, now));
                    } else if (changed.status() == FriendshipStatus.ACCEPTED) {
                        events.publishEvent(new FriendAccepted(other, me, now));
                    }
                });
        return withLastActive(me, other, state(me, other));
    }

    @Transactional
    public FriendshipView remove(long me, String handle) {
        long other = target(handle);
        if (other == me) {
            return new FriendshipView(FriendshipState.SELF, other);
        }
        statusGuard.requireActive(me, ActionKind.ACCOUNT_WRITE);
        friendships.delete(me, other);
        return new FriendshipView(FriendshipState.NONE, other);
    }

    @Transactional(readOnly = true)
    public FriendListPage listFriends(long me, String cursor) {
        return page(ListScope.myFriends(), me, cursor, true);
    }

    @Transactional(readOnly = true)
    public FriendListPage listReceivedRequests(long me, String cursor) {
        return page(ListScope.friendRequests(), me, cursor, false);
    }

    private FriendListPage page(ListScope scope, long me, String cursor, boolean friends) {
        Instant afterAt = null;
        Long afterOtherId = null;
        if (cursor != null && !cursor.isEmpty()) {
            CursorPayload payload = cursorCodec.decode(cursor, scope);
            afterAt = Instant.EPOCH.plus(payload.longAt(0), ChronoUnit.MICROS);
            afterOtherId = payload.longAt(1);
        }
        List<FriendListQueryRepository.Row> rows =
                friends
                        ? lists.friends(me, afterAt, afterOtherId, PAGE_SIZE + 1)
                        : lists.receivedRequests(me, afterAt, afterOtherId, PAGE_SIZE + 1);
        boolean more = rows.size() > PAGE_SIZE;
        List<FriendListQueryRepository.Row> pageRows = more ? rows.subList(0, PAGE_SIZE) : rows;
        Instant now = clock.instant();
        Map<Long, ProfileImageKeys> photos =
                profileImageQuery.currentKeysOf(
                        pageRows.stream().map(FriendListQueryRepository.Row::otherId).toList());
        List<FriendListItem> items =
                pageRows.stream()
                        .map(
                                row ->
                                        new FriendListItem(
                                                row.otherId(),
                                                row.handle(),
                                                row.nickname(),
                                                Optional.ofNullable(photos.get(row.otherId()))
                                                        .map(ProfileImageKeys::display)
                                                        .map(imageUrlResolver::publicUrl)
                                                        .orElse(null),
                                                row.at(),
                                                lastActiveQuery
                                                        .bucket(row.visibleLastActiveAt(), now)
                                                        .orElse(null)))
                        .toList();
        String next = null;
        if (more) {
            FriendListQueryRepository.Row last = pageRows.getLast();
            next = cursorCodec.encode(scope, List.of(micros(last.at()), last.otherId()), Map.of());
        }
        return new FriendListPage(items, next);
    }

    /** 친구면 최근 활동(조건을 만족할 때만)을 붙인다. */
    private FriendshipView withLastActive(long me, long other, FriendshipState state) {
        return new FriendshipView(
                state,
                other,
                state == FriendshipState.FRIENDS
                        ? lastActiveQuery.lastActiveFor(me, other).orElse(null)
                        : null);
    }

    private FriendshipState state(long me, long other) {
        if (me == other) {
            return FriendshipState.SELF;
        }
        Optional<Friendship> row = friendships.find(me, other);
        if (row.isEmpty()) {
            return FriendshipState.NONE;
        }
        if (row.get().isAccepted()) {
            return FriendshipState.FRIENDS;
        }
        return row.get().getRequestedBy() == me
                ? FriendshipState.REQUEST_SENT
                : FriendshipState.REQUEST_RECEIVED;
    }

    private long target(String handle) {
        return memberQuery
                .findReadableBlogOwner(handle)
                .orElseThrow(() -> new NotFoundException("friend target not found"))
                .id();
    }

    private static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }
}
