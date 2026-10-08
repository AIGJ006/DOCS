package com.team.blog.account.application.purge;

import com.team.blog.account.application.WithdrawalPolicy;
import com.team.blog.account.application.WithdrawalProperties;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Outcome;
import com.team.blog.account.application.purge.WithdrawalPurgeRunner.Reason;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 탈퇴 30일 정리 작업 (015 T056, contracts/purge-steps.md §3, FR-021a·FR-008). 매일 {@code
 * blog.withdraw.purge.cron}(기본 03:00, 서비스 시간대)에 돈다. 서버가 여러 대여도 ShedLock {@code withdrawPurge}로 한 번만
 * 돈다.
 *
 * <ol>
 *   <li>{@code required-orders}의 단계가 하나라도 Bean으로 없으면 아무 회원도 처리하지 않고 ERROR를 남긴다(research R9).
 *   <li>유예가 지난 회원과 영구 정지 1년이 지난 회원을 각각 {@code batch-size}명까지 고른다.
 *   <li>회원마다 {@link WithdrawalPurgeRunner#purgeOne} (새 트랜잭션). 실패하면 ERROR를 남기고 다음 회원으로 간다 — 실패한 회원은
 *       다음 날 다시 대상이 된다.
 *   <li>끝에 처리·건너뜀·실패 수를 INFO로 남긴다.
 * </ol>
 */
@Component
public class WithdrawPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(WithdrawPurgeJob.class);

    /**
     * 한 번 실행한 결과.
     *
     * @param purged 정리한 회원 수
     * @param skipped 잠근 뒤 다시 보니 대상이 아니어서 건너뛴 수
     * @param failed 실패해 남은 수
     * @param aborted 필수 단계가 빠져 아무것도 하지 않았는가
     */
    public record Result(int purged, int skipped, int failed, boolean aborted) {}

    private final WithdrawalMemberRepository members;
    private final WithdrawalPurgeRunner runner;
    private final WithdrawalStepRegistry registry;
    private final WithdrawalPolicy policy;
    private final WithdrawalProperties properties;

    public WithdrawPurgeJob(
            WithdrawalMemberRepository members,
            WithdrawalPurgeRunner runner,
            WithdrawalStepRegistry registry,
            WithdrawalPolicy policy,
            WithdrawalProperties properties) {
        this.members = members;
        this.runner = runner;
        this.registry = registry;
        this.policy = policy;
        this.properties = properties;
    }

    @Scheduled(cron = "${blog.withdraw.purge.cron}", zone = "${blog.time-zone}")
    @SchedulerLock(name = "withdrawPurge", lockAtMostFor = "PT1H")
    public void scheduled() {
        run(policy.now());
    }

    /** {@code now} 기준으로 한 번 돈다. 잠금 없이 부르는 것은 테스트·운영 도구뿐이다. */
    public Result run(Instant now) {
        List<Integer> missing = registry.missingRequired(properties.purge().requiredOrders());
        if (!missing.isEmpty()) {
            log.error("탈퇴 정리 단계 누락 orders={} — 정리 작업을 건너뜁니다", missing);
            return new Result(0, 0, 0, true);
        }
        int batchSize = properties.purge().batchSize();
        int[] counts = new int[3];
        for (long id : members.findPurgeTargets(policy.purgeCutoff(now), batchSize)) {
            purge(id, Reason.GRACE_EXPIRED, counts);
        }
        for (long id :
                members.findSuspendedPurgeTargets(policy.suspendedPurgeCutoff(now), batchSize)) {
            purge(id, Reason.SUSPENDED_PERMANENT, counts);
        }
        log.info("탈퇴 정리 작업 완료 purged={} skipped={} failed={}", counts[0], counts[1], counts[2]);
        return new Result(counts[0], counts[1], counts[2], false);
    }

    private void purge(long memberId, Reason reason, int[] counts) {
        try {
            Outcome outcome = runner.purgeOne(memberId, reason);
            counts[outcome == Outcome.PURGED ? 0 : 1]++;
        } catch (WithdrawalPurgeStepException e) {
            counts[2]++;
            log.error(
                    "탈퇴 정리 실패 memberId={} step={} order={}",
                    memberId,
                    e.stepName(),
                    e.order(),
                    e.getCause());
        } catch (RuntimeException e) {
            counts[2]++;
            log.error("탈퇴 정리 실패 memberId={} step=-", memberId, e);
        }
    }
}
