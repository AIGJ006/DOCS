import { useEffect, useRef, useState } from 'react';
import AuthPrompt from '../auth-gate/AuthPrompt';
import { authPromptFor, type AuthPromptKind } from '../auth-gate/authGate';
import { useAuthGate } from '../auth-gate/useAuthGate';
import { useSession } from '../auth/useSession';
import { FOLLOW_MESSAGES } from './followMessages';
import { useFollowToggle } from './useFollowToggle';
import './follow.css';

export interface FollowButtonProps {
  /** 대상 블로그 주소 */
  handle: string;
  /** 처음 상태 (`followedByMe`·`viewer.followingAuthor`) */
  initialFollowing: boolean;
  /** 대상의 처음 팔로워 수 — `onCountChange`로 바뀐 수를 알릴 때만 필요 */
  initialCount?: number;
  /** 팔로워 수가 바뀌면 (누른 즉시 ±1, 응답이 오면 서버 값) */
  onCountChange?: (count: number) => void;
  /** 내 블로그·나 자신이면 버튼을 그리지 않는다 */
  isMe: boolean;
  /** 로그인 여부를 화면이 이미 알 때 (글 상세 `viewer.loggedIn`). 없으면 세션(`useSession`)으로 판단한다 */
  loggedIn?: boolean;
}

/**
 * 팔로우 버튼 (010 T022, FR-009~012, research R9).
 *
 * - [팔로우](채운 버튼) / [팔로잉 ✓](테두리 버튼, `aria-pressed="true"`). 팔로잉 버튼에 마우스를 올리거나 초점을 주면 글자만
 *   [언팔로우]로 바뀌고, 누르면 확인 창 없이 언팔로우한다. 상태는 색만이 아니라 글자·✓로도 구분된다.
 * - 누르는 즉시 바뀌고 0.3초 동안 마지막 상태만 보낸다(`useFollowToggle`). 실패하면 되돌리고 "잠시 후 다시 시도해 주세요"(`role="status"`).
 * - 비회원이 누르면 요청 없이 004 로그인 안내(`AuthPrompt`, 돌아온 뒤 자동으로 팔로우하지 않음 — H6). 401·403 응답도 같은 안내로 바꾼다.
 * - 방금 누른 버튼은 마우스·초점이 떠났다 돌아올 때까지 [팔로잉 ✓]로 보인다(누르자마자 [언팔로우]로 보이지 않게).
 */
export default function FollowButton({
  handle,
  initialFollowing,
  initialCount = 0,
  onCountChange,
  isMe,
  loggedIn,
}: FollowButtonProps) {
  const { loading, me } = useSession();
  const gate = useAuthGate({ unauthorized: 'prompt' });
  const toggle = useFollowToggle({ handle, initialFollowing, initialCount });
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [justPressed, setJustPressed] = useState(false);

  const reported = useRef(initialCount);
  useEffect(() => {
    if (onCountChange && toggle.count !== reported.current) {
      reported.current = toggle.count;
      onCountChange(toggle.count);
    }
  }, [onCountChange, toggle.count]);

  if (isMe) {
    return null;
  }

  const promptKind: AuthPromptKind | null = gate.prompt ?? authPromptFor(toggle.error);
  const failed = toggle.error !== null && promptKind === null;

  function onClick() {
    const anonymous = loggedIn !== undefined ? !loggedIn : !loading && !me;
    if (anonymous) {
      gate.show('login');
      return;
    }
    gate.dismiss();
    setJustPressed(true);
    toggle.toggle();
  }

  const reveal = toggle.following && (hovered || focused) && !justPressed;
  const label = !toggle.following
    ? FOLLOW_MESSAGES.follow
    : reveal
      ? FOLLOW_MESSAGES.unfollow
      : FOLLOW_MESSAGES.following;

  return (
    <span className="follow-area" data-testid="follow-area">
      <button
        type="button"
        className="follow-button"
        data-state={toggle.following ? 'following' : 'idle'}
        aria-pressed={toggle.following}
        onClick={onClick}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => {
          setHovered(false);
          setJustPressed(false);
        }}
        onFocus={() => setFocused(true)}
        onBlur={() => {
          setFocused(false);
          setJustPressed(false);
        }}
      >
        {label}
        {toggle.following && !reveal ? (
          <span aria-hidden="true" className="follow-button__check">
            {' ✓'}
          </span>
        ) : null}
      </button>
      <span role="status" className="follow-notice">
        {failed ? FOLLOW_MESSAGES.failed : null}
      </span>
      {promptKind ? (
        <AuthPrompt
          kind={promptKind}
          loginPath={gate.loginPath}
          onClose={() => {
            gate.dismiss();
            toggle.dismiss();
          }}
        />
      ) : null}
    </span>
  );
}
