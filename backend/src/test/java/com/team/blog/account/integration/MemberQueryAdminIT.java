package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AdminMemberInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.fixture.PostFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 관리자 회원 화면 조회 (014 T014). */
class MemberQueryAdminIT extends IntegrationTestBase {

    @Autowired MemberQueryService members;

    @Test
    void 주소는_대소문자를_가리지_않고_탈퇴_유예도_보인다() {
        long id = members().member().handle("kim755030").nickname("김민서").create();
        long admin = members().member().handle("admin01").role("ADMIN").create();
        long leaving = members().member().handle("leaving1").create();
        new PostFixtures(jdbc).withdraw(leaving);

        AdminMemberInfo info = members.findAdminView("KIM755030").orElseThrow();
        assertThat(info.id()).isEqualTo(id);
        assertThat(info.nickname()).isEqualTo("김민서");
        assertThat(info.role()).isEqualTo(Role.USER);
        assertThat(info.status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(info.createdAt()).isNotNull();
        assertThat(members.findAdminView("admin01").orElseThrow().role()).isEqualTo(Role.ADMIN);
        AdminMemberInfo w = members.findAdminView("leaving1").orElseThrow();
        assertThat(w.status()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(w.withdrawnAt()).isNotNull();
        assertThat(admin).isPositive();
    }

    @Test
    void 익명_처리와_없는_주소는_빈_값_번호_조회는_주소_없이() {
        long gone = members().member().handle("gone0001").deleted().create();

        assertThat(members.findAdminView("gone0001")).isEmpty();
        assertThat(members.findAdminView("nobody99")).isEmpty();
        assertThat(members.findAdminView("")).isEmpty();
        AdminMemberInfo byId = members.findAdminViewById(gone).orElseThrow();
        assertThat(byId.handle()).isNull();
        assertThat(byId.nickname()).isNull();
        assertThat(members.findAdminViewById(9_999_999L)).isEmpty();
    }
}
