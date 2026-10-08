package com.team.blog.account.support;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트용 탈퇴 정리 단계 order 85 (015 T042 {@code US3_3}). 불린 시점의 회원 상태를 기록하고(단계 순서 확인), 지정한 회원이면 예외를 던진다(한
 * 단계 실패 → 그 회원 전체 롤백). 기본은 아무것도 바꾸지 않으므로 다른 테스트에 영향이 없다. 컨텍스트를 함께 쓰므로 테스트마다 {@link #reset()}한다.
 */
@Profile("test")
@Component
public class WithdrawalPurgeProbe implements WithdrawalPurgeStep {

    /** 불린 시점에 본 것: 앞 단계(10·50)는 끝났고 뒤 단계(90)는 아직이어야 한다. */
    public record Seen(long memberId, long posts, long authIdentities, boolean nicknamePresent) {}

    private final JdbcTemplate jdbc;
    private final Set<Long> failFor = ConcurrentHashMap.newKeySet();
    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    public WithdrawalPurgeProbe(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 85;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        Long posts =
                jdbc.queryForObject(
                        "SELECT count(*) FROM post WHERE author_id = ?", Long.class, memberId);
        Long identities =
                jdbc.queryForObject(
                        "SELECT count(*) FROM auth_identity WHERE member_id = ?",
                        Long.class,
                        memberId);
        Boolean nickname =
                jdbc.queryForObject(
                        "SELECT nickname IS NOT NULL FROM member WHERE id = ?",
                        Boolean.class,
                        memberId);
        seen.add(new Seen(memberId, posts, identities, Boolean.TRUE.equals(nickname)));
        if (failFor.contains(memberId)) {
            throw new IllegalStateException("테스트 단계 실패");
        }
    }

    public void failFor(long memberId) {
        failFor.add(memberId);
    }

    public List<Seen> seen() {
        return List.copyOf(seen);
    }

    public void reset() {
        failFor.clear();
        seen.clear();
    }
}
