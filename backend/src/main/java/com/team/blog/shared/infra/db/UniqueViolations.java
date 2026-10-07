package com.team.blog.shared.infra.db;

import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

/**
 * DB UNIQUE 위반(PostgreSQL SQLSTATE {@code 23505})에서 위반한 제약 이름을 꺼낸다. 각 기능은 제약 이름을 칸별 이유 코드로 바꾼다
 * (R-09: {@code uq_auth_identity} → {@code EMAIL_ALREADY_REGISTERED}, {@code uq_member_handle} →
 * {@code HANDLE_DUPLICATE}, {@code uq_member_nickname} → {@code NICKNAME_DUPLICATE} 등).
 *
 * <p>JdbcTemplate({@code DataIntegrityViolationException} → {@code PSQLException})과 JPA(… →
 * Hibernate {@code ConstraintViolationException} → {@code PSQLException}) 모두 원인 사슬에서 찾는다.
 */
public final class UniqueViolations {

    public static final String UNIQUE_VIOLATION = "23505";

    private UniqueViolations() {}

    /** UNIQUE 위반이면 제약(또는 UNIQUE 인덱스) 이름, 아니면 빈 값. */
    public static Optional<String> constraintName(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql && UNIQUE_VIOLATION.equals(psql.getSQLState())) {
                ServerErrorMessage message = psql.getServerErrorMessage();
                return Optional.ofNullable(message == null ? null : message.getConstraint());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return Optional.empty();
    }

    /** 주어진 제약의 UNIQUE 위반인가. */
    public static boolean isViolationOf(Throwable error, String constraint) {
        return constraintName(error).filter(constraint::equals).isPresent();
    }
}
