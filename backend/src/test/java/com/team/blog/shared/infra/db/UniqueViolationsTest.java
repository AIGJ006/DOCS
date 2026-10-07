package com.team.blog.shared.infra.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

class UniqueViolationsTest {

    private static PSQLException psql(String sqlState, String constraint) {
        PSQLException ex = mock(PSQLException.class);
        ServerErrorMessage message = mock(ServerErrorMessage.class);
        when(ex.getSQLState()).thenReturn(sqlState);
        when(ex.getServerErrorMessage()).thenReturn(message);
        when(message.getSQLState()).thenReturn(sqlState);
        when(message.getConstraint()).thenReturn(constraint);
        return ex;
    }

    @Test
    void 위반한_UNIQUE_제약_이름을_꺼낸다() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException(
                        "dup", new RuntimeException("wrapped", psql("23505", "uq_member_handle")));
        assertThat(UniqueViolations.constraintName(ex)).contains("uq_member_handle");
        assertThat(UniqueViolations.isViolationOf(ex, "uq_member_handle")).isTrue();
        assertThat(UniqueViolations.isViolationOf(ex, "uq_member_nickname")).isFalse();
    }

    @Test
    void UNIQUE_위반이_아니면_비어_있다() {
        assertThat(
                        UniqueViolations.constraintName(
                                new DataIntegrityViolationException(
                                        "fk", psql("23503", "fk_auth_identity_member"))))
                .isEmpty();
        assertThat(UniqueViolations.constraintName(new SQLException("x", "23505"))).isEmpty();
        assertThat(UniqueViolations.constraintName(new IllegalStateException())).isEmpty();
        assertThat(UniqueViolations.constraintName(null)).isEmpty();
    }
}
