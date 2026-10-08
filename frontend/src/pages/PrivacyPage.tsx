import AgreementMeta from '../components/AgreementMeta';
import { useCurrentAgreements } from '../features/auth/useCurrentAgreements';
import '../features/auth/auth.css';

/**
 * 개인정보 처리방침 (07 §3-1, FR-011, H9). "친구에게 최근 활동 시점 표시" 항목을 반드시 적는다.
 * 본문은 팀이 확정하기 전 자리 표시 문구다.
 */
export default function PrivacyPage() {
  const { agreements, failed } = useCurrentAgreements();
  return (
    <main className="auth-page document-page">
      <h1>개인정보 처리방침</h1>
      <AgreementMeta document={agreements?.privacy} failed={failed} />
      <p className="placeholder-note">이 문서는 팀이 확정하기 전의 자리 표시 문구예요.</p>
      <section>
        <h2>1. 수집하는 항목</h2>
        <ul>
          <li>필수: 이메일, 블로그 주소, 닉네임, 비밀번호(암호화해 저장), 약관 동의 기록</li>
          <li>소셜 로그인: 제공자가 알려 준 이메일과 계정 식별 번호</li>
          <li>선택: 프로필 사진, 소개</li>
          <li>자동 수집: 로그인 시각과 로그인 방식, 최근 활동 시각</li>
        </ul>
      </section>
      <section>
        <h2>2. 이용 목적</h2>
        <ul>
          <li>회원 식별과 로그인, 메일 인증과 비밀번호 재설정</li>
          <li>계정 화면의 &quot;직전 로그인&quot; 표시로 계정 도용 확인</li>
        </ul>
      </section>
      <section>
        <h2>3. 친구에게 최근 활동 시점 표시</h2>
        <p>
          서로 친구인 회원에게 내 최근 활동 시점(예: &quot;3시간 전 활동&quot;)을 보여 줘요. 친구가
          아닌 사람에게는 보이지 않아요.
        </p>
      </section>
      <section>
        <h2>4. 보관 기간</h2>
        <p>탈퇴하면 30일 뒤 개인 정보를 익명 처리해요. 약관 동의 기록은 법령에 따라 보관해요.</p>
      </section>
      <section>
        <h2>5. 조회수 집계</h2>
        <p>
          같은 사람이 새로고침해 조회수가 부풀지 않도록, 로그인하지 않은 방문자의 브라우저에 무작위
          식별자 쿠키(vid)를 1년 동안 저장해요. 이 값으로 누구인지 알 수는 없어요.
        </p>
        <p>
          쿠키를 쓸 수 없는 경우에는 접속 주소와 브라우저 정보로 만든 되돌릴 수 없는 값을 하루
          동안만 써요. 접속 주소와 쿠키 값 자체는 저장하지 않아요.
        </p>
      </section>
    </main>
  );
}
