import { useLocation, useNavigate } from 'react-router-dom';
import { useFriendship } from '../features/friends/useFriendship';
import { useSession } from '../features/auth/useSession';
import { useConfirm } from './useConfirm';

const UNFRIEND_CONFIRM = {
  title: '친구를 끊을까요?',
  message: '상대에게 알리지 않아요. 끊은 뒤에는 서로의 최근 활동이 보이지 않아요.',
  confirmLabel: '끊기',
};

/**
 * 블로그 머리말의 친구 버튼 (001 T132, FR-054~056). 배치는 specs/005가 한다.
 *
 * - `SELF` 숨김, `NONE` [친구 요청], `REQUEST_SENT` [요청 취소], `REQUEST_RECEIVED` [수락]·[거절], `FRIENDS` [친구 끊기](확인 후).
 * - 비로그인은 [친구 요청]을 누르면 `/login?returnTo=지금 주소`로.
 * - 처리 후 서버가 돌려준 상태로 바꾼다. 거절·취소·끊기는 상대에게 알리지 않는다.
 */
export default function FriendButton({ handle }: { handle: string }) {
  const { me, loading } = useSession();
  const loggedIn = !loading && me !== null;
  const { view, error, busy, request, remove } = useFriendship(handle, loggedIn);
  const { confirm, dialog } = useConfirm();
  const navigate = useNavigate();
  const location = useLocation();

  if (loading) {
    return null;
  }
  if (!loggedIn) {
    return (
      <button
        type="button"
        onClick={() =>
          navigate(`/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`)
        }
      >
        친구 요청
      </button>
    );
  }
  if (!view || view.status === 'SELF') {
    return null;
  }

  let buttons;
  switch (view.status) {
    case 'NONE':
      buttons = (
        <button type="button" onClick={() => void request()} disabled={busy}>
          친구 요청
        </button>
      );
      break;
    case 'REQUEST_SENT':
      buttons = (
        <button type="button" onClick={() => void remove()} disabled={busy}>
          요청 취소
        </button>
      );
      break;
    case 'REQUEST_RECEIVED':
      buttons = (
        <>
          <button type="button" onClick={() => void request()} disabled={busy}>
            수락
          </button>
          <button type="button" onClick={() => void remove()} disabled={busy}>
            거절
          </button>
        </>
      );
      break;
    case 'FRIENDS':
      buttons = (
        <button
          type="button"
          disabled={busy}
          onClick={async () => {
            if (await confirm(UNFRIEND_CONFIRM)) {
              await remove();
            }
          }}
        >
          친구 끊기
        </button>
      );
      break;
  }

  return (
    <span className="friend-button">
      {buttons}
      {error && (
        <span role="alert" className="field-error">
          {error}
        </span>
      )}
      {dialog}
    </span>
  );
}
