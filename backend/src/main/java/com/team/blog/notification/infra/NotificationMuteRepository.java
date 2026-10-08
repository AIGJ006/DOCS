package com.team.blog.notification.infra;

import com.team.blog.notification.domain.MutableType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 끈 알림 종류 ({@code notification_mute}, 011 contracts §2 ④·§9). 행이 있으면 꺼짐, 기본(행 없음)은 켜짐.
 *
 * <p>설정 저장의 {@code type = ANY(:muted)}는 {@link JdbcClient} 이름 매개변수가 목록을 펼치므로 {@code NOT IN
 * (:muted)}로 쓴다(같은 뜻, 015 임시 정리 단계와 같은 방식). 빈 목록이면 그 회원의 행을 모두 지운다.
 */
@Repository
public class NotificationMuteRepository {

    private final JdbcClient jdbc;

    public NotificationMuteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 그 회원이 그 종류를 껐는가. */
    public boolean isMuted(long memberId, MutableType type) {
        return Boolean.TRUE.equals(
                jdbc.sql(
                                "SELECT EXISTS (SELECT 1 FROM notification_mute"
                                        + " WHERE member_id = :m AND type = :t)")
                        .param("m", memberId)
                        .param("t", type.name())
                        .query(Boolean.class)
                        .single());
    }

    /** 그 회원이 끈 종류. */
    public Set<MutableType> mutedTypes(long memberId) {
        List<String> rows =
                jdbc.sql("SELECT type FROM notification_mute WHERE member_id = :m")
                        .param("m", memberId)
                        .query(String.class)
                        .list();
        Set<MutableType> result = EnumSet.noneOf(MutableType.class);
        rows.forEach(t -> result.add(MutableType.valueOf(t)));
        return result;
    }

    /** 끈 종류를 {@code muted}로 맞춘다 (호출한 쪽 트랜잭션 안). 이미 꺼져 있던 행의 {@code created_at}은 그대로 둔다. */
    public void replace(long memberId, Set<MutableType> muted, Instant now) {
        if (muted.isEmpty()) {
            jdbc.sql("DELETE FROM notification_mute WHERE member_id = :m")
                    .param("m", memberId)
                    .update();
            return;
        }
        List<String> names = muted.stream().map(Enum::name).toList();
        jdbc.sql("DELETE FROM notification_mute WHERE member_id = :m AND type NOT IN (:muted)")
                .param("m", memberId)
                .param("muted", names)
                .update();
        for (String type : names) {
            jdbc.sql(
                            "INSERT INTO notification_mute (member_id, type, created_at)"
                                    + " VALUES (:m, :t, :now) ON CONFLICT DO NOTHING")
                    .param("m", memberId)
                    .param("t", type)
                    .param("now", Timestamp.from(now))
                    .update();
        }
    }
}
