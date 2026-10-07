package com.team.blog.account.infra;

import com.team.blog.account.application.policy.HandleLookup;
import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 주소 중복 조회: {@code handle = ? OR handle LIKE '{base}\_%'} 한 번 ({@code uq_member_handle} 인덱스). 탈퇴·익명
 * 처리된 회원의 주소도 포함한다(재사용 금지, FR-021).
 */
@Component
public class JdbcHandleLookup implements HandleLookup {

    private final JdbcClient jdbc;

    public JdbcHandleLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> takenWithBase(String base) {
        String pattern = escapeLike(base) + "\\_%";
        return new HashSet<>(
                jdbc.sql(
                                "SELECT handle FROM member WHERE handle = :base"
                                        + " OR handle LIKE :pattern ESCAPE '\\'")
                        .param("base", base)
                        .param("pattern", pattern)
                        .query(String.class)
                        .list());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
