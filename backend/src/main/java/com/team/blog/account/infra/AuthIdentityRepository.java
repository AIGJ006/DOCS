package com.team.blog.account.infra;

import com.team.blog.account.domain.AuthIdentity;
import com.team.blog.account.domain.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** {@code auth_identity} 저장소. */
public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    /** 로그인 조회({@code uq_auth_identity}). LOCAL이면 {@code providerUserId}는 소문자 이메일. */
    Optional<AuthIdentity> findByProviderAndProviderUserId(
            Provider provider, String providerUserId);

    /** 회원당 1개({@code uq_auth_identity_member}). */
    Optional<AuthIdentity> findByMemberId(long memberId);

    /** 같은 이메일을 쓰는 모든 계정(비밀번호 찾기 안내, FR-042 — {@code ix_auth_identity_email}). */
    List<AuthIdentity> findAllByEmail(String email);
}
