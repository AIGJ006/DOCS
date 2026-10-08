package com.team.blog.interaction.application;

import com.team.blog.interaction.infra.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 정리 — 팔로우 (010 T041, contracts/follow-sql.md §7, 015 contracts/purge-steps.md §2 order 65).
 *
 * <p>015가 부르는 서명: {@code int purgeByMember(long memberId)} — 015 탈퇴 정리 단계(order 65)가 탈퇴 트랜잭션 안에서
 * 부른다. 호출한 쪽 트랜잭션 안에서만 쓴다({@code MANDATORY}). 그 회원이 팔로우한·팔로우받은 관계를 양방향 모두 지운다. 이벤트는 내지 않고 회원 번호와 지운
 * 행 수만 INFO로 남긴다. 멱등이다(다시 불려도 0행).
 *
 * <p>(구현 메모) 015 {@code WithdrawalPurgeStep} 확장점은 015 소유 파일이라 010이 만들지 않는다 — 009 {@code
 * LikePurgeService}처럼 정리 메서드만 두고, 단계 클래스({@code FollowWithdrawalPurgeStep}, order 65)는 두 브랜치가 만날 때
 * 이 메서드에 위임하게 한다. 탈퇴 유예 중 제외는 수·목록·피드 SQL 조건이 맡는다.
 */
@Service
public class FollowPurgeService {

    private static final Logger log = LoggerFactory.getLogger(FollowPurgeService.class);

    /** 015 정리 단계 순서 (contracts/purge-steps.md §2). */
    public static final int WITHDRAWAL_PURGE_ORDER = 65;

    private final FollowRepository follows;

    public FollowPurgeService(FollowRepository follows) {
        this.follows = follows;
    }

    /**
     * @return 지운 팔로우 행 수 (양방향 합)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int purgeByMember(long memberId) {
        int deleted = follows.deleteAllOf(memberId);
        log.info("탈퇴 팔로우 정리: memberId={} deleted={}", memberId, deleted);
        return deleted;
    }
}
