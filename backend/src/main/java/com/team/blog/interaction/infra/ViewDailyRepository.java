package com.team.blog.interaction.infra;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 일별 조회수 ({@code post_view_daily}, interaction 모듈 소유, 009 research R8·R9). 누가 봤는지는 담지 않는다 —
 * 글·날짜(서비스 시간대)·합계뿐이다(FR-034).
 */
@Repository
public class ViewDailyRepository {

    private final JdbcClient jdbc;

    public ViewDailyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 그날 합계에 {@code n}을 더한다 (없으면 만든다). {@code n}은 1 이상({@code ck_post_view_daily_views}). */
    public void upsert(long postId, LocalDate date, long n) {
        jdbc.sql(
                        """
                        INSERT INTO post_view_daily (post_id, view_date, views) VALUES (:p, :d, :n)
                        ON CONFLICT (post_id, view_date)
                        DO UPDATE SET views = post_view_daily.views + EXCLUDED.views
                        """)
                .param("p", postId)
                .param("d", date)
                .param("n", n)
                .update();
    }

    /** {@code date}보다 이른 날짜의 합계를 지운다 ({@code ix_post_view_daily_date}). @return 지운 행 수 */
    public int deleteOlderThan(LocalDate date) {
        return jdbc.sql("DELETE FROM post_view_daily WHERE view_date < :d")
                .param("d", date)
                .update();
    }
}
