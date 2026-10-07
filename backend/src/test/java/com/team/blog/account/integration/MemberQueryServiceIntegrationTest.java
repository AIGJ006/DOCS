package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.MemberAccessInfo;
import com.team.blog.account.application.MemberQueryService;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Role;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 다른 모듈(004 Viewer, 005 블로그, 002 새 글)이 쓰는 회원 읽기 API. */
class MemberQueryServiceIntegrationTest extends IntegrationTestBase {

    @Autowired MemberQueryService memberQueryService;

    @Test
    void findAccessInfo는_역할_상태_인증_여부() {
        long user = members().member().emailVerified(false).create();
        long admin = members().member().role("ADMIN").create();
        long suspended = members().member().create();
        members().suspend(suspended, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");

        assertThat(memberQueryService.findAccessInfo(user))
                .contains(new MemberAccessInfo(Role.USER, MemberStatus.ACTIVE, false));
        assertThat(memberQueryService.findAccessInfo(admin))
                .contains(new MemberAccessInfo(Role.ADMIN, MemberStatus.ACTIVE, true));
        assertThat(memberQueryService.findAccessInfo(suspended))
                .contains(new MemberAccessInfo(Role.USER, MemberStatus.SUSPENDED, true));
        assertThat(memberQueryService.findAccessInfo(987_654_321L)).isEmpty();
    }

    @Test
    void findAccessInfo는_익명_처리된_회원을_없는_회원으로_본다() {
        long deleted = members().member().deleted().create();
        assertThat(memberQueryService.findAccessInfo(deleted)).isEmpty();
    }

    @Test
    void findReadableBlogOwner는_주소로_주인을_찾는다() {
        long id = members().member().handle("kim755030").nickname("김블로그").create();
        jdbc.update("UPDATE member SET bio = '안녕하세요' WHERE id = ?", id);

        assertThat(memberQueryService.findReadableBlogOwner("kim755030"))
                .contains(new BlogOwner(id, "kim755030", "김블로그", "안녕하세요"));
    }

    @Test
    void 대문자_주소는_normalizeHandle로_소문자로_바꿔_비교한다() {
        long id = members().member().handle("kim755030").nickname("김블로그").create();

        assertThat(MemberQueryService.normalizeHandle("Kim755030")).isEqualTo("kim755030");
        assertThat(MemberQueryService.normalizeHandle("GO-Kim_1")).isEqualTo("go-kim_1");
        // 005는 대문자면 먼저 301로 보내므로 조회 자체는 저장된 값과 정확히 같은 주소만 찾는다
        assertThat(memberQueryService.findReadableBlogOwner("KIM755030")).isEmpty();
        assertThat(
                        memberQueryService.findReadableBlogOwner(
                                MemberQueryService.normalizeHandle("KIM755030")))
                .map(BlogOwner::id)
                .contains(id);
    }

    @Test
    void 탈퇴_유예_익명_처리_없는_주소는_빈_값() {
        members().member().handle("withdrawn1").status("WITHDRAWN").create();
        members().member().handle("deleted01").deleted().create();

        assertThat(memberQueryService.findReadableBlogOwner("withdrawn1")).isEmpty();
        assertThat(memberQueryService.findReadableBlogOwner("deleted01")).isEmpty();
        assertThat(memberQueryService.findReadableBlogOwner("nobody99")).isEmpty();
        assertThat(memberQueryService.findReadableBlogOwner("")).isEmpty();
        assertThat(memberQueryService.findReadableBlogOwner(null)).isEmpty();
    }

    @Test
    void 정지된_회원의_블로그는_읽을_수_있다() {
        long id = members().member().handle("suspended1").create();
        members().suspend(id, null, "정지");
        assertThat(memberQueryService.findReadableBlogOwner("suspended1")).isPresent();
    }

    @Test
    void defaultVisibility는_회원의_새_글_기본_공개_범위() {
        long pub = members().member().create();
        long priv = members().member().defaultVisibility("PRIVATE").create();
        assertThat(memberQueryService.defaultVisibility(pub)).isEqualTo("PUBLIC");
        assertThat(memberQueryService.defaultVisibility(priv)).isEqualTo("PRIVATE");
    }
}
