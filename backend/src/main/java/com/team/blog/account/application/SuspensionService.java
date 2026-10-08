package com.team.blog.account.application;

import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.MemberSuspension;
import com.team.blog.account.domain.Role;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.MemberSuspensionRepository;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.event.MemberSuspended;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 정지 (R-23·R-31, FR-038). account가 {@code member_suspension}과 {@code member.status}를 함께 가진다.
 *
 * <ul>
 *   <li>001: 로그인 때 {@link #findOpen}·{@link #liftIfExpired}(기한 지난 정지 자동 해제) — {@link
 *       #requireNotSuspended}.
 *   <li>014(신고·숨김·정지, T052): {@link #suspend}·{@link #lift}·{@link #history} — 관리자 회원 화면이 부른다.
 * </ul>
 */
@Service
public class SuspensionService {

    private static final Logger log = LoggerFactory.getLogger(SuspensionService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MemberSuspensionRepository suspensions;
    private final MemberRepository members;
    private final Clock clock;
    private final ZoneId zone;
    private final SessionTerminator sessions;
    private final MemberQueryService memberQueries;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public SuspensionService(
            MemberSuspensionRepository suspensions,
            MemberRepository members,
            Clock clock,
            ZoneId serviceZoneId,
            SessionTerminator sessions,
            MemberQueryService memberQueries,
            ApplicationEventPublisher events,
            TransactionTemplate tx) {
        this.suspensions = suspensions;
        this.members = members;
        this.clock = clock;
        this.zone = serviceZoneId;
        this.sessions = sessions;
        this.memberQueries = memberQueries;
        this.events = events;
        this.tx = tx;
    }

    /** 열린 정지 (기한이 지났어도 아직 해제하지 않았으면 돌려준다). */
    @Transactional(readOnly = true)
    public Optional<OpenSuspension> findOpen(long memberId) {
        return suspensions.findOpenByMemberId(memberId).map(SuspensionService::toView);
    }

    /**
     * 기한이 지난 열린 정지를 해제한다: {@code lifted_at = now}, {@code lifted_by = NULL}, {@code member.status =
     * ACTIVE} (같은 트랜잭션). 해제했으면 true.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public boolean liftIfExpired(long memberId, Instant now) {
        Optional<MemberSuspension> open = suspensions.findOpenByMemberId(memberId);
        if (open.isEmpty() || !open.get().isExpiredAt(now)) {
            return false;
        }
        open.get().lift(now, null);
        members.findById(memberId).ifPresent(member -> member.liftSuspension(now));
        return true;
    }

    /**
     * 로그인 판정: 기한 지난 정지는 해제하고, 열린 정지가 남아 있으면 403 {@code ACCOUNT_SUSPENDED} + {@code details {endsAt,
     * reason}}("정지된 계정이에요 (~기한). 사유: …", 기한 없으면 영구).
     */
    @Transactional
    public void requireNotSuspended(long memberId) {
        Instant now = clock.instant();
        Optional<MemberSuspension> open = suspensions.findOpenByMemberId(memberId);
        if (open.isEmpty()) {
            return;
        }
        if (open.get().isExpiredAt(now)) {
            // 드문 경로: 기한 지난 정지를 해제한 뒤 남은 열린 정지가 있는지 다시 본다(평소 로그인은 위 조회 1번으로 끝난다).
            liftIfExpired(memberId, now);
            Optional<OpenSuspension> remaining = findOpen(memberId);
            if (remaining.isPresent()) {
                throw suspendedError(remaining.get());
            }
            return;
        }
        throw suspendedError(toView(open.get()));
    }

    /** 정지 거부 오류 본문. */
    public AccountStateException suspendedError(OpenSuspension suspension) {
        String until =
                suspension.permanent() ? "영구" : "~" + DATE.format(suspension.endsAt().atZone(zone));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("endsAt", suspension.permanent() ? null : suspension.endsAt().toString());
        details.put("reason", suspension.reason());
        return new AccountStateException(
                CommonReasonCode.ACCOUNT_SUSPENDED,
                "정지된 계정이에요 (" + until + "). 사유: " + suspension.reason(),
                details);
    }

    /**
     * 회원을 정지한다 (014 T052, research R10, contracts/moderation-sql.md §7). 사유·기간 형식은 호출한 쪽(014)이
     * 확인한다.
     *
     * <ol>
     *   <li>읽기 확인: 없음·익명 처리 → 404, 관리자(자기 포함) → 400 {@code CANNOT_SUSPEND_ADMIN}, 탈퇴 유예 → 400
     *       {@code CANNOT_SUSPEND_WITHDRAWN}, 열린 정지(기한 지난 것은 먼저 해제) → 409 {@code ALREADY_SUSPENDED}
     *   <li>그 회원의 모든 세션 삭제 — Redis 장애면 503 {@code TEMPORARILY_UNAVAILABLE}이고 DB는 아무것도 바뀌지 않는다
     *   <li>한 트랜잭션: 회원 행 {@code FOR UPDATE} → 1을 다시 확인(동시 정지는 하나만) → 이력 INSERT + {@code status =
     *       SUSPENDED} → {@link MemberSuspended}
     *   <li>커밋 뒤 세션을 한 번 더 지운다(그 사이 새로 로그인한 세션 대비, 실패는 WARN — 남은 세션의 쓰기는 계정 상태 가드가 403)
     * </ol>
     *
     * <p>(구현 메모) plan R10은 세션 삭제를 "같은 트랜잭션 안, 커밋 전"으로 적었지만 트랜잭션 안에서 Redis를 지우지 않는 규칙(002 T119
     * {@code RedisGuard})에 맞춰 트랜잭션 <b>전</b>에 지운다. Redis 장애면 정지하지 않고 503이라는 결과는 같다.
     */
    public SuspensionRecord suspend(
            long memberId, String reason, SuspensionDuration duration, long adminId, Instant now) {
        Instant startedAt = now.truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(
                status -> {
                    Member member = findAlive(memberId, false);
                    requireSuspendable(member, startedAt);
                });
        sessions.terminateAll(memberId, Optional.empty());
        MemberSuspension saved =
                tx.execute(
                        status -> {
                            Member member = findAlive(memberId, true);
                            requireSuspendable(member, startedAt);
                            MemberSuspension created =
                                    suspensions.save(
                                            MemberSuspension.start(
                                                    memberId,
                                                    reason,
                                                    startedAt,
                                                    duration.endsAt(startedAt),
                                                    adminId));
                            member.suspend(startedAt);
                            events.publishEvent(
                                    new MemberSuspended(memberId, created.getEndsAt(), startedAt));
                            RedisGuard.runAfterCommit(() -> terminateAgain(memberId));
                            return created;
                        });
        log.info("회원 정지 memberId={} suspensionId={}", memberId, saved.getId());
        return toRecords(List.of(saved)).get(0);
    }

    /**
     * 정지를 해제한다 (014 T052): 열린 정지에 {@code lifted_at = now}, {@code lifted_by = adminId}, {@code
     * status = SUSPENDED}일 때만 {@code ACTIVE}. 열린 정지가 없으면 아무것도 바꾸지 않는다. 알림·이벤트 없음(FR-030).
     *
     * @throws NotFoundException 없는 회원·익명 처리된 회원
     */
    @Transactional
    public void lift(long memberId, long adminId, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        Member member = findAlive(memberId, true);
        List<MemberSuspension> open = suspensions.findAllOpen(memberId);
        if (open.isEmpty()) {
            return;
        }
        open.forEach(s -> s.lift(at, adminId));
        member.liftSuspension(at);
        log.info("회원 정지 해제 memberId={} count={}", memberId, open.size());
    }

    /** 정지 이력 최근 순 (최대 {@code limit}개). */
    @Transactional(readOnly = true)
    public List<SuspensionRecord> history(long memberId, int limit) {
        return toRecords(suspensions.findHistory(memberId, limit));
    }

    /** 정지 이력 수. */
    @Transactional(readOnly = true)
    public long historyCount(long memberId) {
        return suspensions.countByMemberId(memberId);
    }

    private Member findAlive(long memberId, boolean lock) {
        Optional<Member> member =
                lock ? members.findByIdForUpdate(memberId) : members.findById(memberId);
        return member.filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("정지: 없는 회원"));
    }

    private void requireSuspendable(Member member, Instant now) {
        if (member.getRole() == Role.ADMIN) {
            throw new BusinessRuleException(AccountReasonCode.CANNOT_SUSPEND_ADMIN);
        }
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new BusinessRuleException(AccountReasonCode.CANNOT_SUSPEND_WITHDRAWN);
        }
        Optional<MemberSuspension> open = suspensions.findOpenByMemberId(member.getId());
        if (open.isPresent() && open.get().isExpiredAt(now)) {
            liftIfExpired(member.getId(), now);
            open = suspensions.findOpenByMemberId(member.getId());
        }
        if (open.isPresent()) {
            throw new BusinessRuleException(AccountReasonCode.ALREADY_SUSPENDED);
        }
    }

    private void terminateAgain(long memberId) {
        try {
            sessions.terminateAll(memberId, Optional.empty());
        } catch (RuntimeException e) {
            log.warn("정지 뒤 세션 다시 삭제 실패 memberId={}: {}", memberId, e.toString());
        }
    }

    private List<SuspensionRecord> toRecords(List<MemberSuspension> rows) {
        Set<Long> admins = new HashSet<>();
        for (MemberSuspension s : rows) {
            admins.add(s.getSuspendedBy());
            if (s.getLiftedBy() != null) {
                admins.add(s.getLiftedBy());
            }
        }
        Map<Long, MemberDisplay> displays = memberQueries.findDisplays(admins);
        List<SuspensionRecord> result = new ArrayList<>();
        for (MemberSuspension s : rows) {
            result.add(
                    new SuspensionRecord(
                            s.getId(),
                            s.getReason(),
                            s.getStartedAt(),
                            s.getEndsAt(),
                            handleOf(displays, s.getSuspendedBy()),
                            s.getLiftedAt(),
                            handleOf(displays, s.getLiftedBy())));
        }
        return result;
    }

    private static String handleOf(Map<Long, MemberDisplay> displays, Long id) {
        MemberDisplay d = id == null ? null : displays.get(id);
        return d == null ? null : d.handle();
    }

    private static OpenSuspension toView(MemberSuspension s) {
        return new OpenSuspension(s.getId(), s.getReason(), s.getStartedAt(), s.getEndsAt());
    }
}
