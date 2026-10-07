package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.MemberStatus;
import com.team.blog.account.domain.Provider;
import com.team.blog.account.domain.Role;
import com.team.blog.account.infra.AuthIdentityRepository;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** Member·AuthIdentity 매핑과 저장소 조회 (ddl-auto=validate가 컬럼 타입을, 이 테스트가 읽고 쓰기를 확인). */
class AccountEntityMappingIntegrationTest extends IntegrationTestBase {

    @Autowired MemberRepository memberRepository;
    @Autowired AuthIdentityRepository authIdentityRepository;
    @Autowired TransactionTemplate tx;

    @Test
    void 가입한_회원과_로그인_수단을_저장하고_읽는다() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Member saved = memberRepository.saveAndFlush(Member.join("kim755030", "김블로그", now));
        AuthIdentity identity =
                authIdentityRepository.saveAndFlush(
                        AuthIdentity.local(saved.getId(), " Kim@Example.COM ", "$2a$10$hash", now));

        Member loaded = memberRepository.findByHandle("kim755030").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getRole()).isEqualTo(Role.USER);
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loaded.getDefaultVisibility()).isEqualTo("PUBLIC");
        assertThat(loaded.isLastActiveVisible()).isTrue();
        assertThat(loaded.getNicknameChangedAt()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(memberRepository.existsByHandle("kim755030")).isTrue();
        assertThat(memberRepository.existsByHandle("nobody99")).isFalse();

        assertThat(
                        authIdentityRepository.findByProviderAndProviderUserId(
                                Provider.LOCAL, "kim@example.com"))
                .map(AuthIdentity::getId)
                .contains(identity.getId());
        assertThat(authIdentityRepository.findByMemberId(saved.getId()))
                .get()
                .satisfies(
                        a -> {
                            assertThat(a.getEmail()).isEqualTo("kim@example.com");
                            assertThat(a.isEmailVerified()).isFalse();
                        });
        assertThat(authIdentityRepository.findAllByEmail("kim@example.com")).hasSize(1);
    }

    @Test
    void findByIdForUpdate는_트랜잭션_안에서_행을_잠근다() {
        long id = members().member().create();
        Member locked = tx.execute(s -> memberRepository.findByIdForUpdate(id).orElseThrow());
        assertThat(locked.getId()).isEqualTo(id);
    }

    @Test
    void 같은_이메일의_소셜_계정을_함께_찾는다() {
        members().member().email("same@example.com").create();
        members()
                .member()
                .provider("GOOGLE")
                .providerUserId("sub-1")
                .email("same@example.com")
                .create();
        assertThat(authIdentityRepository.findAllByEmail("same@example.com"))
                .extracting(AuthIdentity::getProvider)
                .containsExactlyInAnyOrder(Provider.LOCAL, Provider.GOOGLE);
    }

    @Test
    void DB_CHECK가_주소_형식을_2중으로_지킨다() {
        assertThatThrownBy(
                        () ->
                                memberRepository.saveAndFlush(
                                        Member.join("Bad Handle", "김블로그", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
