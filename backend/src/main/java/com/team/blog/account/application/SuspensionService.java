package com.team.blog.account.application;

import com.team.blog.account.domain.MemberSuspension;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.account.infra.MemberSuspensionRepository;
import com.team.blog.shared.error.AccountStateException;
import com.team.blog.shared.error.CommonReasonCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정지 (R-23·R-31, FR-038). account가 {@code member_suspension}과 {@code member.status}를 함께 가진다.
 *
 * <ul>
 *   <li>001: 로그인 때 {@link #findOpen}·{@link #liftIfExpired}(기한 지난 정지 자동 해제) — {@link
 *       #requireNotSuspended}.
 *   <li>014(신고·숨김): {@link #suspend}·{@link #lift}를 부르고 {@code SessionTerminator}로 세션을 지운다. 지금은
 *       시그니처만 있다(specs/014에서 구현).
 * </ul>
 */
@Service
public class SuspensionService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MemberSuspensionRepository suspensions;
    private final MemberRepository members;
    private final Clock clock;
    private final ZoneId zone;

    public SuspensionService(
            MemberSuspensionRepository suspensions,
            MemberRepository members,
            Clock clock,
            ZoneId serviceZoneId) {
        this.suspensions = suspensions;
        this.members = members;
        this.clock = clock;
        this.zone = serviceZoneId;
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
        liftIfExpired(memberId, now);
        Optional<OpenSuspension> open = findOpen(memberId);
        if (open.isPresent()) {
            throw suspendedError(open.get());
        }
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

    /** 014용: 정지를 건다. TODO(specs/014): 열린 정지 하나 보장·{@code member.status = SUSPENDED}·세션 삭제. */
    public long suspend(long memberId, String reason, Instant endsAt, long suspendedBy) {
        throw new UnsupportedOperationException("정지 생성은 specs/014에서 구현한다");
    }

    /** 014용: 정지를 해제한다. TODO(specs/014). */
    public void lift(long memberId, long liftedBy) {
        throw new UnsupportedOperationException("정지 해제는 specs/014에서 구현한다");
    }

    private static OpenSuspension toView(MemberSuspension s) {
        return new OpenSuspension(s.getId(), s.getReason(), s.getStartedAt(), s.getEndsAt());
    }
}
