package com.team.blog.notification.application;

import com.team.blog.shared.application.withdraw.WithdrawalPurgeStep;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>임시 구현</b> — 탈퇴 정리 order 70: 알림 정리 (015 contracts/purge-steps.md §2, 011 notification-sql §11
 * ①~④).
 *
 * <p>이 단계의 주인은 <b>011-notification</b>(011 tasks T056 {@code NotificationWithdrawalPurgeStep})이다.
 * 011이 머지되기 전에도 015 정리 작업의 필수 단계(order 70)가 비지 않도록 015가 같은 SQL로 채워 둔다. 011이 진짜 클래스를 만들면 {@link
 * ConditionalOnMissingClass}로 이 Bean은 등록되지 않는다 — 그때 이 파일을 지운다.
 */
@Component
@ConditionalOnMissingClass("com.team.blog.notification.application.NotificationWithdrawalPurgeStep")
public class InterimNotificationWithdrawalPurgeStep implements WithdrawalPurgeStep {

    private static final Logger log =
            LoggerFactory.getLogger(InterimNotificationWithdrawalPurgeStep.class);

    private final JdbcClient jdbc;

    public InterimNotificationWithdrawalPurgeStep(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(long memberId) {
        // ① 받은 알림
        int received =
                jdbc.sql("DELETE FROM notification WHERE receiver_id = :m")
                        .param("m", memberId)
                        .update();
        // ② 남의 묶음에서 빼기 — 네 문장을 따로 (한 문장 CTE는 UPDATE가 DELETE 전 상태를 본다).
        // 011 SQL의 = ANY(:ids)는 JdbcClient 이름 매개변수가 목록을 펼치므로 IN (:ids)로 쓴다(같은 뜻)
        List<Long> ids =
                jdbc.sql(
                                """
                                SELECT id FROM notification
                                 WHERE id IN (SELECT notification_id FROM notification_actor
                                               WHERE actor_id = :m)
                                 ORDER BY id FOR UPDATE
                                """)
                        .param("m", memberId)
                        .query(Long.class)
                        .list();
        jdbc.sql("DELETE FROM notification_actor WHERE actor_id = :m")
                .param("m", memberId)
                .update();
        int emptied = 0;
        if (!ids.isEmpty()) {
            jdbc.sql(
                            """
                            UPDATE notification n
                               SET actor_count = (SELECT count(*) FROM notification_actor x
                                                   WHERE x.notification_id = n.id),
                                   last_actor_id = (SELECT x.actor_id FROM notification_actor x
                                                     WHERE x.notification_id = n.id
                                                     ORDER BY x.created_at DESC, x.actor_id DESC
                                                     LIMIT 1)
                             WHERE n.id IN (:ids)
                            """)
                    .param("ids", ids)
                    .update();
            emptied =
                    jdbc.sql("DELETE FROM notification WHERE id IN (:ids) AND actor_count = 0")
                            .param("ids", ids)
                            .update();
        }
        // ③ 내가 행동한 하나짜리 (ix_notification_last_actor)
        int acted =
                jdbc.sql("DELETE FROM notification WHERE last_actor_id = :m AND group_key IS NULL")
                        .param("m", memberId)
                        .update();
        // ④ 끄기 설정
        jdbc.sql("DELETE FROM notification_mute WHERE member_id = :m")
                .param("m", memberId)
                .update();
        log.info(
                "탈퇴 알림 정리(임시): memberId={} received={} groups={} emptied={} acted={}",
                memberId,
                received,
                ids.size(),
                emptied,
                acted);
    }
}
