import AgreementMeta from '../components/AgreementMeta';
import { useCurrentAgreements } from '../features/auth/useCurrentAgreements';
import '../features/auth/auth.css';

/**
 * 이용약관 (07 §3-1, FR-011). 본문은 팀이 확정하기 전 자리 표시 문구다.
 */
export default function TermsPage() {
  const { agreements, failed } = useCurrentAgreements();
  return (
    <main className="auth-page document-page">
      <h1>이용약관</h1>
      <AgreementMeta document={agreements?.terms} failed={failed} />
      <p className="placeholder-note">이 문서는 팀이 확정하기 전의 자리 표시 문구예요.</p>
      <section>
        <h2>1. 목적</h2>
        <p>이 약관은 블로그 서비스 이용에 필요한 회원과 서비스의 권리·의무를 정해요.</p>
      </section>
      <section>
        <h2>2. 계정</h2>
        <p>
          회원은 이메일 또는 소셜 계정으로 가입해요. 계정 정보는 본인만 쓰고, 다른 사람에게 넘기지
          않아요.
        </p>
      </section>
      <section>
        <h2>3. 게시물</h2>
        <p>
          회원이 쓴 글의 권리는 회원에게 있어요. 법령이나 운영 정책에 어긋나는 게시물은 숨겨질 수
          있어요.
        </p>
      </section>
      <section>
        <h2>4. 이용 제한과 탈퇴</h2>
        <p>
          운영 정책을 어기면 이용이 제한될 수 있어요. 회원은 언제든 탈퇴할 수 있고, 탈퇴 신청 후
          30일 동안은 되돌릴 수 있어요.
        </p>
      </section>
    </main>
  );
}
