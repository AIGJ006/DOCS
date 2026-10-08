package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.MemberDisplay;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.SqlCounter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 회원 표시 정보 (007 T007, data-model §5). */
class MemberDisplayQueryIT extends IntegrationTestBase {

    @Autowired MemberQueryService memberQuery;

    @Test
    void 상태별_표시와_SQL_한_번() {
        long active = members().member().handle("active1").nickname("활동").create();
        long suspended = members().member().create();
        members().suspend(suspended, Instant.now().plus(7, ChronoUnit.DAYS), "시험");
        long withdrawn = members().member().handle("gone1").nickname("떠남").status("WITHDRAWN").create();
        long anonymized = members().member().deleted().create();

        Map<Long, MemberDisplay> displays;
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            displays = memberQuery.findDisplays(List.of(active, suspended, withdrawn, anonymized, 999_999L));
            assertThat(scope.count()).isEqualTo(1);
        }

        assertThat(displays).containsOnlyKeys(active, suspended, withdrawn, anonymized);
        assertThat(displays.get(active)).isEqualTo(new MemberDisplay(active, "active1", "활동", false));
        assertThat(displays.get(suspended).withdrawn()).isFalse();
        assertThat(displays.get(withdrawn)).isEqualTo(new MemberDisplay(withdrawn, "gone1", "떠남", true));
        assertThat(displays.get(anonymized)).isEqualTo(new MemberDisplay(anonymized, null, null, true));
    }

    @Test
    void 빈_입력은_SQL_없이_빈_맵() {
        try (SqlCounter.Scope scope = SqlCounter.start()) {
            assertThat(memberQuery.findDisplays(List.of())).isEmpty();
            assertThat(scope.count()).isZero();
        }
    }
}
