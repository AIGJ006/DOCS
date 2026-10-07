package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.team.blog.shared.error.ApiException;
import com.team.blog.shared.error.CommonReasonCode;
import com.team.blog.shared.error.ReasonCode;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

/** 42 §3 ②, data-model §4-1 표: 상태 × 행동 판정. 매 호출 DB에서 읽는다(세션 값 미사용). */
class AccountStatusGuardIntegrationTest extends IntegrationTestBase {

    @Autowired AccountStatusGuard guard;

    private ReasonCode rejection(long memberId, ActionKind kind) {
        ApiException e =
                catchThrowableOfType(ApiException.class, () -> guard.requireActive(memberId, kind));
        return e == null ? null : e.reasonCode();
    }

    @Test
    void 인증_전_ACTIVE는_CONTENT_WRITE만_EMAIL_NOT_VERIFIED() {
        long id = members().member().emailVerified(false).create();
        assertThat(rejection(id, ActionKind.CONTENT_WRITE))
                .isEqualTo(CommonReasonCode.EMAIL_NOT_VERIFIED);
        assertThat(rejection(id, ActionKind.ACCOUNT_WRITE)).isNull();
        assertThat(rejection(id, ActionKind.CONTENT_CLEANUP)).isNull();
    }

    @ParameterizedTest
    @EnumSource(ActionKind.class)
    void 인증된_ACTIVE는_모두_통과(ActionKind kind) {
        long id = members().member().create();
        assertThatCode(() -> guard.requireActive(id, kind)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(ActionKind.class)
    void SUSPENDED는_모두_ACCOUNT_SUSPENDED(ActionKind kind) {
        long id = members().member().emailVerified(false).create();
        members().suspend(id, Instant.now().plus(3, ChronoUnit.DAYS), "스팸");
        assertThat(rejection(id, kind)).isEqualTo(CommonReasonCode.ACCOUNT_SUSPENDED);
    }

    @ParameterizedTest
    @EnumSource(ActionKind.class)
    void WITHDRAWN은_모두_ACCOUNT_WITHDRAWN(ActionKind kind) {
        long id = members().member().status("WITHDRAWN").emailVerified(false).create();
        assertThat(rejection(id, kind)).isEqualTo(CommonReasonCode.ACCOUNT_WITHDRAWN);
        ApiException e =
                catchThrowableOfType(ApiException.class, () -> guard.requireActive(id, kind));
        assertThat(e.status().value()).isEqualTo(403);
        assertThat(e.details()).containsEntry("action", "RESTORE");
    }

    @Test
    void 판정_우선순위는_탈퇴_유예_정지_인증_전() {
        long id = members().member().emailVerified(false).create();
        members().suspend(id, null, "영구 정지");
        assertThat(rejection(id, ActionKind.CONTENT_WRITE))
                .isEqualTo(CommonReasonCode.ACCOUNT_SUSPENDED);
        // 정지 중 탈퇴 유예로 바뀐 상태는 DB CHECK상 SUSPENDED와 동시에 둘 수 없으므로 WITHDRAWN만 확인
        jdbc.update(
                "UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", id);
        assertThat(rejection(id, ActionKind.CONTENT_WRITE))
                .isEqualTo(CommonReasonCode.ACCOUNT_WITHDRAWN);
    }

    @Test
    void DB_변경은_다음_호출에_바로_반영() {
        long id = members().member().emailVerified(false).create();
        assertThat(rejection(id, ActionKind.CONTENT_WRITE))
                .isEqualTo(CommonReasonCode.EMAIL_NOT_VERIFIED);

        jdbc.update("UPDATE auth_identity SET email_verified_at = now() WHERE member_id = ?", id);
        assertThat(rejection(id, ActionKind.CONTENT_WRITE)).isNull();

        jdbc.update("UPDATE member SET status = 'SUSPENDED' WHERE id = ?", id);
        assertThat(rejection(id, ActionKind.CONTENT_WRITE))
                .isEqualTo(CommonReasonCode.ACCOUNT_SUSPENDED);

        jdbc.update("UPDATE member SET status = 'ACTIVE' WHERE id = ?", id);
        assertThat(rejection(id, ActionKind.ACCOUNT_WRITE)).isNull();
    }

    @Test
    void 없는_회원과_익명_처리된_회원은_LOGIN_REQUIRED() {
        long deleted = members().member().deleted().create();
        assertThat(rejection(deleted, ActionKind.CONTENT_CLEANUP))
                .isEqualTo(CommonReasonCode.LOGIN_REQUIRED);
        assertThat(rejection(987_654_321L, ActionKind.ACCOUNT_WRITE))
                .isEqualTo(CommonReasonCode.LOGIN_REQUIRED);
    }

    @Test
    void 판정은_SQL_1번() {
        long id = members().member().create();
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            guard.requireActive(id, ActionKind.CONTENT_WRITE);
            assertThat(scope.count()).isEqualTo(1);
        }
    }
}
