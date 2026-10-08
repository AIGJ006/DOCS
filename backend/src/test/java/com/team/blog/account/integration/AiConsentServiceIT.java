package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.account.application.AiConsentView;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.support.IntegrationTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AI 동의 기록 (013 T018, research R10, data-model §1). */
class AiConsentServiceIT extends IntegrationTestBase {

    @Autowired private AiConsentService consent;

    private void row(long memberId, String type, String version) {
        jdbc.update(
                "INSERT INTO member_agreement (member_id, type, version, agreed_at) VALUES (?, ?, ?, ?)",
                memberId,
                type,
                version,
                Timestamp.from(Instant.parse("2026-09-02T01:00:00Z")));
    }

    private List<Map<String, Object>> rows(long memberId) {
        return jdbc.queryForList(
                "SELECT type, version FROM member_agreement WHERE member_id = ? ORDER BY type",
                memberId);
    }

    @Test
    void 행이_없으면_동의하지_않음() {
        long m = members().member().create();
        assertThat(consent.isConsented(m)).isFalse();
        assertThat(consent.view(m)).isEqualTo(new AiConsentView(false, null, "2026-10-08", null));
    }

    @Test
    void 현재_버전_행이면_동의() {
        long m = members().member().create();
        row(m, "AI", "2026-10-08");
        assertThat(consent.isConsented(m)).isTrue();
        AiConsentView view = consent.view(m);
        assertThat(view.agreed()).isTrue();
        assertThat(view.version()).isEqualTo("2026-10-08");
        assertThat(view.agreedAt()).isEqualTo(Instant.parse("2026-09-02T01:00:00Z"));
    }

    @Test
    void 옛_버전_행이면_동의하지_않음() {
        long m = members().member().create();
        row(m, "AI", "2026-09-01");
        assertThat(consent.isConsented(m)).isFalse();
        AiConsentView view = consent.view(m);
        assertThat(view.agreed()).isFalse();
        assertThat(view.version()).isEqualTo("2026-09-01");
        assertThat(view.currentVersion()).isEqualTo("2026-10-08");
    }

    @Test
    void 현재_버전으로_동의하면_같은_PK_행_하나() {
        long m = members().member().create();
        row(m, "AI", "2026-09-01");
        AiConsentView view = consent.agree(m, "2026-10-08");
        assertThat(view.agreed()).isTrue();
        assertThat(view.agreedAt()).isAfter(Instant.parse("2026-09-02T01:00:00Z"));
        consent.agree(m, "2026-10-08");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM member_agreement WHERE member_id = ? AND type = 'AI'",
                                Long.class,
                                m))
                .isEqualTo(1L);
        assertThat(consent.isConsented(m)).isTrue();
    }

    @Test
    void 다른_버전이면_VALIDATION_FAILED_AGREEMENT_VERSION_MISMATCH() {
        long m = members().member().create();
        assertThatThrownBy(() -> consent.agree(m, "2026-09-01"))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        e -> {
                            assertThat(e.reasonCode().code()).isEqualTo("VALIDATION_FAILED");
                            FieldError error = e.errors().get(0);
                            assertThat(error.field()).isEqualTo("version");
                            assertThat(error.code()).isEqualTo("AGREEMENT_VERSION_MISMATCH");
                        });
        assertThat(rows(m)).isEmpty();
    }

    @Test
    void 철회는_두_번_해도_성공하고_약관_처리방침_행은_그대로() {
        long m = members().member().create();
        row(m, "TERMS", "2026-10-07");
        row(m, "PRIVACY", "2026-10-07");
        consent.agree(m, "2026-10-08");
        assertThat(consent.revoke(m).agreed()).isFalse();
        assertThat(consent.revoke(m)).isEqualTo(new AiConsentView(false, null, "2026-10-08", null));
        assertThat(rows(m)).extracting(r -> r.get("type")).containsExactly("PRIVACY", "TERMS");
    }
}
