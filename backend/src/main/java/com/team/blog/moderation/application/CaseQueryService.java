package com.team.blog.moderation.application;

import com.team.blog.account.application.AdminMemberInfo;
import com.team.blog.account.application.MemberDisplay;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.application.OpenSuspension;
import com.team.blog.account.application.SuspensionService;
import com.team.blog.interaction.application.CommentModerationService;
import com.team.blog.interaction.application.CommentModerationService.CommentSnapshot;
import com.team.blog.moderation.domain.CaseStatus;
import com.team.blog.moderation.domain.ReportReason;
import com.team.blog.moderation.domain.TargetState;
import com.team.blog.moderation.infra.CaseListQueryRepository;
import com.team.blog.moderation.infra.CaseListQueryRepository.HandledKey;
import com.team.blog.moderation.infra.CaseListQueryRepository.PendingKey;
import com.team.blog.moderation.infra.CaseListQueryRepository.PendingRow;
import com.team.blog.moderation.infra.CaseListQueryRepository.ReportStats;
import com.team.blog.moderation.infra.CaseRow;
import com.team.blog.moderation.infra.ReportCaseRepository;
import com.team.blog.moderation.infra.ReportRepository;
import com.team.blog.moderation.infra.ReportRow;
import com.team.blog.post.application.PostModerationService;
import com.team.blog.post.application.PostReadService;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.ReportTargetType;
import com.team.blog.shared.security.Viewer;
import com.team.blog.shared.web.cursor.CursorCodec;
import com.team.blog.shared.web.cursor.CursorPayload;
import com.team.blog.shared.web.cursor.InvalidCursorException;
import com.team.blog.shared.web.cursor.ListScope;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사건 목록·상세 (014 T034, research R5, contracts {@code listReportCases}·{@code getReportCase}).
 *
 * <ul>
 *   <li>목록: {@code PENDING} 탭(신고 수 → 최근 신고 → 번호, 커서 {@code [count, lastReportedAt, id]})·{@code
 *       HANDLED} 탭(처리 시각 → 번호, 커서 {@code [handledAt, id]}). 목록 구분 {@code admin-reports:{tab}}. 한
 *       페이지 SQL: 목록 1 + 사유별 수 1 + 회원 1 + (처리됨) 숨김 여부 1~2.
 *   <li>상세: 스냅샷·현재 상태·신고 수·사유별 수·기타 설명(신고자 없이 시각만)·{@code reportedByMe}·{@code onlyMyReport}·작성자
 *       카드. 현재 원문은 주지 않는다(FR-015).
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    /** 댓글 사건 목록 제목 = 스냅샷 내용 앞 40자. */
    static final int COMMENT_TITLE_CHARS = 40;

    private final ModerationAccess access;
    private final CaseListQueryRepository lists;
    private final ReportCaseRepository cases;
    private final ReportRepository reports;
    private final MemberQueryService members;
    private final SuspensionService suspensions;
    private final PostModerationService postModeration;
    private final CommentModerationService commentModeration;
    private final PostReadService postReadService;
    private final CursorCodec cursors;
    private final ModerationProperties properties;
    private final Clock clock;

    public CaseQueryService(
            ModerationAccess access,
            CaseListQueryRepository lists,
            ReportCaseRepository cases,
            ReportRepository reports,
            MemberQueryService members,
            SuspensionService suspensions,
            PostModerationService postModeration,
            CommentModerationService commentModeration,
            PostReadService postReadService,
            CursorCodec cursors,
            ModerationProperties properties,
            Clock clock) {
        this.access = access;
        this.lists = lists;
        this.cases = cases;
        this.reports = reports;
        this.members = members;
        this.suspensions = suspensions;
        this.postModeration = postModeration;
        this.commentModeration = commentModeration;
        this.postReadService = postReadService;
        this.cursors = cursors;
        this.properties = properties;
        this.clock = clock;
    }

    /** 목록 탭. */
    public enum Tab {
        PENDING,
        HANDLED
    }

    public CasePage list(Viewer viewer, Tab tab, String cursor) {
        access.requireAdmin(viewer);
        return listAsAdmin(tab, cursor);
    }

    /** 관리자 확인을 마친 뒤의 목록. */
    CasePage listAsAdmin(Tab tab, String cursor) {
        ListScope scope = ListScope.of("admin-reports:" + tab.name().toLowerCase());
        int size = properties.adminPageSize();
        return tab == Tab.PENDING ? pending(scope, cursor, size) : handled(scope, cursor, size);
    }

    private CasePage pending(ListScope scope, String cursor, int size) {
        PendingKey after = null;
        if (cursor != null && !cursor.isEmpty()) {
            CursorPayload p = cursors.decode(cursor, scope);
            after = new PendingKey(p.longAt(0), fromMicros(p.longAt(1)), p.longAt(2));
            if (after.count() < 1 || after.id() < 1) {
                throw new InvalidCursorException("admin-reports pending key");
            }
        }
        List<PendingRow> rows = lists.pending(after, size + 1);
        boolean more = rows.size() > size;
        List<PendingRow> page = more ? rows.subList(0, size) : rows;
        Map<Long, MemberDisplay> displays =
                members.findDisplays(
                        page.stream().map(PendingRow::targetAuthorId).distinct().toList());
        List<CaseListItem> items = new ArrayList<>();
        for (PendingRow row : page) {
            ReportTargetType type = ReportTargetType.valueOf(row.targetType());
            items.add(
                    new CaseListItem(
                            row.id(),
                            type,
                            title(type, row.snapshotTitle(), row.snapshotContent()),
                            handle(displays, row.targetAuthorId()),
                            row.count(),
                            row.reasonCounts(),
                            row.lastReportedAt(),
                            CaseStatus.PENDING,
                            null,
                            null,
                            false));
        }
        String next = null;
        if (more) {
            PendingRow last = page.get(page.size() - 1);
            next =
                    cursors.encode(
                            scope,
                            List.of(last.count(), micros(last.lastReportedAt()), last.id()),
                            null);
        }
        return new CasePage(items, next);
    }

    private CasePage handled(ListScope scope, String cursor, int size) {
        HandledKey after = null;
        if (cursor != null && !cursor.isEmpty()) {
            CursorPayload p = cursors.decode(cursor, scope);
            after = new HandledKey(fromMicros(p.longAt(0)), p.longAt(1));
        }
        List<CaseRow> rows = lists.handled(after, size + 1);
        boolean more = rows.size() > size;
        List<CaseRow> page = more ? rows.subList(0, size) : rows;
        Map<Long, ReportStats> stats = lists.stats(page.stream().map(CaseRow::id).toList());
        Set<Long> people = new HashSet<>();
        List<Long> postIds = new ArrayList<>();
        List<Long> commentIds = new ArrayList<>();
        for (CaseRow row : page) {
            people.add(row.targetAuthorId());
            if (row.handledBy() != null) {
                people.add(row.handledBy());
            }
            if (row.postId() != null) {
                postIds.add(row.postId());
            }
            if (row.commentId() != null) {
                commentIds.add(row.commentId());
            }
        }
        Map<Long, MemberDisplay> displays = members.findDisplays(people);
        Map<Long, Boolean> postsHidden = postModeration.hiddenOf(postIds);
        Map<Long, Boolean> commentsHidden = commentModeration.hiddenOf(commentIds);
        List<CaseListItem> items = new ArrayList<>();
        for (CaseRow row : page) {
            ReportStats s = stats.get(row.id());
            boolean hiddenNow =
                    row.postId() != null
                            ? postsHidden.getOrDefault(row.postId(), false)
                            : row.commentId() != null
                                    && commentsHidden.getOrDefault(row.commentId(), false);
            items.add(
                    new CaseListItem(
                            row.id(),
                            row.targetType(),
                            title(row.targetType(), row.snapshotTitle(), row.snapshotContent()),
                            handle(displays, row.targetAuthorId()),
                            s == null ? 0 : s.total(),
                            s == null ? Map.of() : s.counts(),
                            s == null ? null : s.last(),
                            row.status(),
                            row.handledAt(),
                            nickname(displays, row.handledBy()),
                            hiddenNow));
        }
        String next = null;
        if (more) {
            CaseRow last = page.get(page.size() - 1);
            next = cursors.encode(scope, List.of(micros(last.handledAt()), last.id()), null);
        }
        return new CasePage(items, next);
    }

    /**
     * 사건 상세.
     *
     * @throws NotFoundException 없는 사건
     */
    public CaseDetail detail(Viewer viewer, long caseId) {
        long admin = access.requireAdmin(viewer);
        return detailAsAdmin(admin, caseId);
    }

    /** 관리자 확인을 마친 뒤의 상세 (처리 응답에도 쓴다). */
    CaseDetail detailAsAdmin(long admin, long caseId) {
        CaseRow row = cases.find(caseId).orElseThrow(() -> new NotFoundException("사건 상세: 없음"));
        List<ReportRow> rows = reports.findByCase(caseId);
        Map<ReportReason, Integer> counts = new EnumMap<>(ReportReason.class);
        List<CaseDetail.OtherDetail> others = new ArrayList<>();
        boolean mine = false;
        boolean othersReported = false;
        for (ReportRow r : rows) {
            counts.merge(r.reason(), 1, Integer::sum);
            if (r.reason() == ReportReason.OTHER) {
                others.add(new CaseDetail.OtherDetail(r.detail(), r.createdAt()));
            }
            if (r.reporterId() == admin) {
                mine = true;
            } else {
                othersReported = true;
            }
        }
        String handledBy = null;
        if (row.handledBy() != null) {
            handledBy = nickname(members.findDisplays(List.of(row.handledBy())), row.handledBy());
        }
        return new CaseDetail(
                row.id(),
                row.targetType(),
                row.postId(),
                row.commentId(),
                row.snapshotTitle(),
                row.snapshotContent(),
                currentState(row),
                row.status(),
                row.createdAt(),
                row.handledAt(),
                handledBy,
                rows.size(),
                counts,
                others,
                mine,
                mine && !othersReported,
                author(row.targetAuthorId()));
    }

    /** 현재 상태 이름 (research R7 표). */
    TargetState currentState(CaseRow row) {
        if (row.targetType() == ReportTargetType.POST) {
            return row.postId() == null
                    ? TargetState.GONE
                    : TargetState.valueOf(postModeration.currentState(row.postId()).name());
        }
        if (row.commentId() == null) {
            return TargetState.GONE;
        }
        Optional<CommentSnapshot> snapshot = commentModeration.snapshot(row.commentId());
        if (snapshot.isEmpty()) {
            return TargetState.GONE;
        }
        CommentSnapshot c = snapshot.get();
        if (c.deleted()) {
            return TargetState.DELETED;
        }
        if (c.authorWithdrawn()) {
            return TargetState.AUTHOR_WITHDRAWN;
        }
        if (c.hidden()) {
            return TargetState.HIDDEN;
        }
        if (!postReadService.isReadable(c.postId(), Viewer.anonymous())) {
            return TargetState.POST_NOT_VISIBLE;
        }
        return TargetState.VISIBLE;
    }

    private CaseDetail.AuthorInfo author(long authorId) {
        Optional<AdminMemberInfo> info = members.findAdminViewById(authorId);
        Optional<OpenSuspension> open = suspensions.findOpen(authorId);
        Instant now = clock.instant();
        boolean suspendedNow =
                open.isPresent() && (open.get().permanent() || open.get().endsAt().isAfter(now));
        return new CaseDetail.AuthorInfo(
                info.map(AdminMemberInfo::handle).orElse(null),
                info.map(AdminMemberInfo::nickname).orElse(null),
                info.map(AdminMemberInfo::createdAt).orElse(null),
                cases.hiddenCountOfAuthor(authorId),
                suspendedNow,
                (int) suspensions.historyCount(authorId));
    }

    static String title(ReportTargetType type, String snapshotTitle, String snapshotContent) {
        if (type == ReportTargetType.POST) {
            return snapshotTitle;
        }
        if (snapshotContent == null) {
            return null;
        }
        int end =
                snapshotContent.codePointCount(0, snapshotContent.length()) <= COMMENT_TITLE_CHARS
                        ? snapshotContent.length()
                        : snapshotContent.offsetByCodePoints(0, COMMENT_TITLE_CHARS);
        return snapshotContent.substring(0, end);
    }

    private static String handle(Map<Long, MemberDisplay> displays, Long id) {
        MemberDisplay d = id == null ? null : displays.get(id);
        return d == null ? null : d.handle();
    }

    private static String nickname(Map<Long, MemberDisplay> displays, Long id) {
        MemberDisplay d = id == null ? null : displays.get(id);
        return d == null ? null : d.nickname();
    }

    static long micros(Instant at) {
        return at.getEpochSecond() * 1_000_000L + at.getNano() / 1_000;
    }

    static Instant fromMicros(long micros) {
        return Instant.ofEpochSecond(
                Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
    }
}
