import { useState } from 'react';
import type { TargetType } from '../../api/types/moderation';
import type { ViewerFlags } from '../../api/types/viewerFlags';
import { useToast } from '../../components/useToast';
import AuthPrompt from '../auth-gate/AuthPrompt';
import { useAuthGate } from '../auth-gate/useAuthGate';
import ReportDialog from './ReportDialog';
import { REPORT_TEXT } from './useReport';

export interface ReportButtonProps {
  targetType: TargetType;
  targetId: number;
  /** 서버 판정 플래그. 주면 비회원·인증 전 회원에게 창 대신 안내를 띄우고 작성자에게는 버튼을 그리지 않는다 */
  viewer?: ViewerFlags;
  className?: string;
}

/**
 * [신고] 버튼 + 신고 창 (014 T024). 글은 005 `ReactionBar.reportButton`, 댓글은 007 `CommentItem`의 `comment-actions` 자리에
 * 들어간다. 접수되면 창을 닫고 006 알림 줄로 "신고가 접수됐어요. 검토 후 처리할게요". 401·403은 004 `useAuthGate` 안내로 바꾼다.
 */
export default function ReportButton({ targetType, targetId, viewer, className }: ReportButtonProps) {
  const [open, setOpen] = useState(false);
  const gate = useAuthGate({ unauthorized: 'prompt' });
  const { show, toast } = useToast();

  if (viewer?.isAuthor) {
    return null;
  }

  function onClick() {
    gate.dismiss();
    if (viewer && !viewer.loggedIn) {
      gate.show('login');
      return;
    }
    if (viewer && !viewer.emailVerified) {
      gate.show('verify-email');
      return;
    }
    setOpen(true);
  }

  return (
    <span className="report-area">
      <button type="button" className={className ?? 'report-button'} onClick={onClick}>
        {REPORT_TEXT.button}
      </button>
      {open ? (
        <ReportDialog
          targetType={targetType}
          targetId={targetId}
          onClose={() => setOpen(false)}
          onGate={gate.handle}
          onDone={() => {
            setOpen(false);
            show({ text: REPORT_TEXT.accepted });
          }}
        />
      ) : null}
      {gate.prompt ? (
        <AuthPrompt kind={gate.prompt} loginPath={gate.loginPath} onClose={gate.dismiss} />
      ) : null}
      {toast}
    </span>
  );
}
