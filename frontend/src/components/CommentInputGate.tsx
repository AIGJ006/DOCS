import type { ReactNode } from 'react';
import { Link, useLocation } from 'react-router-dom';
import type { ViewerFlags } from '../api/types/viewerFlags';
import { loginPathFor } from '../features/auth-gate/authGate';
import ResendVerificationButton from '../features/auth-gate/ResendVerificationButton';

export interface CommentInputGateProps {
  viewer: ViewerFlags;
  /** 댓글 입력창 (007) — 쓸 수 있는 회원에게만 그린다 */
  children: ReactNode;
}

/**
 * 댓글 입력창 안내 (004 T062, FR-045, US6-3). 007 댓글 입력창이 이것으로 감싼다: 비회원은 "로그인하고 댓글을 남겨 보세요 [로그인]"
 * (007 FR-023 문구, 지금 글로 돌아오는 로그인 링크), 인증 전 회원은 "이메일 인증 후 댓글을 쓸 수 있어요 [인증 메일 다시 보내기]", 그 밖(회원·관리자·작성자)은 입력창. 판정은
 * 서버가 다시 한다.
 */
export default function CommentInputGate({ viewer, children }: CommentInputGateProps) {
  const location = useLocation();
  if (!viewer.loggedIn) {
    return (
      <div className="comment-input-gate" data-gate="login">
        <p>로그인하고 댓글을 남겨 보세요</p>
        <Link to={loginPathFor(location.pathname + location.search)}>로그인</Link>
      </div>
    );
  }
  if (!viewer.emailVerified) {
    return (
      <div className="comment-input-gate" data-gate="verify-email">
        <p>이메일 인증 후 댓글을 쓸 수 있어요</p>
        <ResendVerificationButton />
      </div>
    );
  }
  return <>{children}</>;
}
