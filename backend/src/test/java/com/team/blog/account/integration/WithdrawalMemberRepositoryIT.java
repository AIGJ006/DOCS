package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.infra.WithdrawalMemberRepository;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 탈퇴 상태 전이 저장소 (015 T007, data-model §2, contracts/purge-steps.md §2-1·§3). */
class WithdrawalMemberRepositoryIT extends IntegrationTestBase {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired WithdrawalMemberRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT * FROM member WHERE id = ?", id);
    }

    private void withdrawnAt(long id, Instant at) {
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = ? WHERE id = ?",
                Timestamp.from(at),
                id);
    }

    @Test
    void markWithdrawn은_ACTIVE만_두_값을_함께() {
        long active = members().member().create();
        long suspended = members().member().create();
        members().suspend(suspended, null, "영구");
        long withdrawn = members().member().status("WITHDRAWN").create();

        assertThat(repository.markWithdrawn(active, NOW)).isEqualTo(1);
        assertThat(repository.markWithdrawn(suspended, NOW)).isZero();
        assertThat(repository.markWithdrawn(withdrawn, NOW)).isZero();

        Map<String, Object> r = row(active);
        assertThat(r.get("status")).isEqualTo("WITHDRAWN");
        assertThat(((Timestamp) r.get("withdrawn_at")).toInstant()).isEqualTo(NOW);
        assertThat(((Timestamp) r.get("updated_at")).toInstant()).isEqualTo(NOW);
        assertThat(row(suspended).get("status")).isEqualTo("SUSPENDED");
    }

    @Test
    void markRestored는_익명_처리_전_WITHDRAWN만() {
        long withdrawn = members().member().status("WITHDRAWN").create();
        long deleted = members().member().deleted().create();
        long active = members().member().create();

        assertThat(repository.markRestored(withdrawn, NOW)).isEqualTo(1);
        assertThat(repository.markRestored(deleted, NOW)).isZero();
        assertThat(repository.markRestored(active, NOW)).isZero();

        assertThat(row(withdrawn).get("status")).isEqualTo("ACTIVE");
        assertThat(row(withdrawn).get("withdrawn_at")).isNull();
        assertThat(row(deleted).get("status")).isEqualTo("WITHDRAWN");
    }

    @Test
    void 상태와_신청_시각이_어긋나면_ck_member_withdrawn() {
        long id = members().member().create();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE member SET status = 'WITHDRAWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_member_withdrawn");
    }

    @Test
    void findPurgeTargets는_유예_30일_경과만_신청_순으로_batchSize만큼() {
        Instant cutoff = NOW.minus(Duration.ofDays(30));
        long older = members().member().create();
        withdrawnAt(older, NOW.minus(Duration.ofDays(40)));
        long old = members().member().create();
        withdrawnAt(old, NOW.minus(Duration.ofDays(31)));
        long exactly = members().member().create();
        withdrawnAt(exactly, cutoff);
        long recent = members().member().create();
        withdrawnAt(recent, NOW.minus(Duration.ofDays(29)));
        long deleted = members().member().deleted().create();
        jdbc.update(
                "UPDATE member SET withdrawn_at = ? WHERE id = ?",
                Timestamp.from(NOW.minus(Duration.ofDays(50))),
                deleted);
        members().member().create();

        assertThat(repository.findPurgeTargets(cutoff, 100)).containsExactly(older, old);
        assertThat(repository.findPurgeTargets(cutoff, 1)).containsExactly(older);
    }

    @Test
    void findSuspendedPurgeTargets는_열린_영구_정지_1년_경과_일반_회원만() {
        Instant cutoff = NOW.minus(Duration.ofDays(365));
        long target = permanentlySuspended(NOW.minus(Duration.ofDays(366)), "USER");
        long young = permanentlySuspended(NOW.minus(Duration.ofDays(364)), "USER");
        long admin = permanentlySuspended(NOW.minus(Duration.ofDays(400)), "ADMIN");
        long lifted = permanentlySuspended(NOW.minus(Duration.ofDays(400)), "USER");
        jdbc.update(
                "UPDATE member_suspension SET lifted_at = ? WHERE member_id = ?",
                Timestamp.from(NOW.minus(Duration.ofDays(1))),
                lifted);
        long periodic = members().member().create();
        members().suspend(periodic, NOW.plus(Duration.ofDays(10)), "기간");
        jdbc.update(
                "UPDATE member_suspension SET started_at = ? WHERE member_id = ?",
                Timestamp.from(NOW.minus(Duration.ofDays(500))),
                periodic);

        assertThat(repository.findSuspendedPurgeTargets(cutoff, 100)).containsExactly(target);
        assertThat(repository.hasOpenPermanentSuspensionBefore(target, cutoff)).isTrue();
        assertThat(repository.hasOpenPermanentSuspensionBefore(young, cutoff)).isFalse();
        assertThat(repository.hasOpenPermanentSuspensionBefore(lifted, cutoff)).isFalse();
        assertThat(repository.findSuspendedPurgeTargets(cutoff, 100)).doesNotContain(admin);
    }

    private long permanentlySuspended(Instant startedAt, String role) {
        long id = members().member().role(role).create();
        members().suspend(id, null, "영구");
        jdbc.update(
                "UPDATE member_suspension SET started_at = ? WHERE member_id = ?",
                Timestamp.from(startedAt),
                id);
        return id;
    }

    @Test
    void lockForUpdate는_상태를_돌려준다() {
        long id = members().member().status("WITHDRAWN").create();
        WithdrawalMemberRepository.LockedMember locked =
                new TransactionTemplate(transactionManager)
                        .execute(s -> repository.lockForUpdate(id).orElseThrow());
        assertThat(locked.status()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(locked.withdrawnAt()).isNotNull();
        assertThat(locked.isDeleted()).isFalse();
        java.util.Optional<WithdrawalMemberRepository.LockedMember> missing =
                new TransactionTemplate(transactionManager)
                        .execute(s -> repository.lockForUpdate(999_999L));
        assertThat(missing).isEmpty();
    }

    @Test
    void markSuspendedAsWithdrawn은_SUSPENDED만() {
        long suspended = permanentlySuspended(NOW.minus(Duration.ofDays(400)), "USER");
        long active = members().member().create();
        assertThat(repository.markSuspendedAsWithdrawn(suspended, NOW)).isEqualTo(1);
        assertThat(repository.markSuspendedAsWithdrawn(active, NOW)).isZero();
        assertThat(row(suspended).get("status")).isEqualTo("WITHDRAWN");
        assertThat(((Timestamp) row(suspended).get("withdrawn_at")).toInstant()).isEqualTo(NOW);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_suspension WHERE member_id = ? AND"
                                        + " lifted_at IS NULL",
                                Long.class,
                                suspended))
                .isEqualTo(1);
    }

    @Test
    void anonymize는_개인_정보를_비우고_주소는_남긴다() {
        long id =
                members()
                        .member()
                        .handle("kim755030")
                        .nickname("김민서")
                        .status("WITHDRAWN")
                        .lastActive(NOW, true)
                        .create();
        jdbc.update(
                "UPDATE member SET bio = '소개', nickname_changed_at = ? WHERE id = ?",
                Timestamp.from(NOW),
                id);

        repository.anonymize(id, NOW);

        Map<String, Object> r = row(id);
        assertThat(r.get("handle")).isEqualTo("kim755030");
        assertThat(r.get("nickname")).isNull();
        assertThat(r.get("bio")).isNull();
        assertThat(r.get("nickname_changed_at")).isNull();
        assertThat(r.get("last_active_at")).isNull();
        assertThat(((Timestamp) r.get("deleted_at")).toInstant()).isEqualTo(NOW);
        assertThat(r.get("status")).isEqualTo("WITHDRAWN");
        assertThat(r.get("withdrawn_at")).isNotNull();
    }

    @Test
    void anonymize는_1행이_아니면_예외() {
        long active = members().member().create();
        long deleted = members().member().deleted().create();
        // @Repository 예외 변환이 IllegalStateException을 InvalidDataAccessApiUsageException으로 감싼다
        assertThatThrownBy(() -> repository.anonymize(active, NOW))
                .hasMessageContaining("익명화 대상이 아닙니다");
        assertThatThrownBy(() -> repository.anonymize(deleted, NOW))
                .hasMessageContaining("익명화 대상이 아닙니다");
        assertThat(row(active).get("nickname")).isNotNull();
    }
}
