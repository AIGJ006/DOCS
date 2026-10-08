import { Link } from 'react-router-dom';
import {
  listFriendRequests,
  listMyFriends,
  removeFriendship,
  requestFriendship,
  type FriendItem,
  type FriendRequestItem,
} from '../../api/friends';
import { ApiError } from '../../api/client';
import DefaultAvatar from '../../components/DefaultAvatar';
import LastActiveBadge from '../../components/LastActiveBadge';
import { useConfirm } from '../../components/useConfirm';
import { formatDate } from '../time/dateFormat';
import { useCursorList } from './useFriendLists';
import { useState } from 'react';

const FAILED_MESSAGE = '잠시 후 다시 시도해 주세요';

function Person({
  item,
}: {
  item: { handle: string; nickname: string; profileImageUrl: string | null };
}) {
  return (
    <>
      {item.profileImageUrl ? (
        <img src={item.profileImageUrl} alt="" width={32} height={32} className="friend-photo" />
      ) : (
        <DefaultAvatar nickname={item.nickname} handle={item.handle} size={32} />
      )}
      <Link to={`/@${item.handle}`} className="friend-name">
        <span>{item.nickname}</span> <span className="friend-handle">@{item.handle}</span>
      </Link>
    </>
  );
}

/**
 * 설정 화면의 친구 영역 (001 T133, FR-055·056): "받은 친구 요청"([수락]·[거절])과 "내 친구"([친구 끊기], 확인 후). 목록은 본인 것만
 * 있다. 처리한 줄은 목록에서 빠지고, 수락하면 내 친구 목록을 다시 읽는다. 거절·끊기는 상대에게 알리지 않는다.
 */
export default function FriendLists() {
  const requests = useCursorList<FriendRequestItem>(listFriendRequests);
  const friends = useCursorList<FriendItem>(listMyFriends);
  const { confirm, dialog } = useConfirm();
  const [error, setError] = useState<string | null>(null);

  async function act(action: () => Promise<unknown>, after: () => void) {
    setError(null);
    try {
      await action();
      after();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : FAILED_MESSAGE);
    }
  }

  return (
    <>
      <section aria-labelledby="friend-requests-title">
        <h2 id="friend-requests-title">받은 친구 요청</h2>
        {requests.failed && <p className="field-error">목록을 불러오지 못했어요</p>}
        {!requests.loading && !requests.failed && requests.items.length === 0 && (
          <p>받은 요청이 없어요</p>
        )}
        <ul className="friend-list">
          {requests.items.map((item) => (
            <li key={item.handle}>
              <Person item={item} />
              <span className="friend-actions">
                <button
                  type="button"
                  onClick={() =>
                    void act(
                      () => requestFriendship(item.handle),
                      () => {
                        requests.removeItem(item.handle);
                        void friends.reload();
                      },
                    )
                  }
                >
                  수락
                </button>
                <button
                  type="button"
                  onClick={() =>
                    void act(
                      () => removeFriendship(item.handle),
                      () => requests.removeItem(item.handle),
                    )
                  }
                >
                  거절
                </button>
              </span>
            </li>
          ))}
        </ul>
        {requests.hasMore && (
          <button type="button" onClick={() => void requests.loadMore()}>
            더 보기
          </button>
        )}
      </section>
      <section aria-labelledby="friends-title">
        <h2 id="friends-title">내 친구</h2>
        {friends.failed && <p className="field-error">목록을 불러오지 못했어요</p>}
        {!friends.loading && !friends.failed && friends.items.length === 0 && (
          <p>아직 친구가 없어요</p>
        )}
        <ul className="friend-list">
          {friends.items.map((item) => (
            <li key={item.handle}>
              <Person item={item} />
              <span className="friend-since">{formatDate(item.friendsSince)}부터</span>
              <LastActiveBadge value={item.lastActive} />
              <span className="friend-actions">
                <button
                  type="button"
                  onClick={async () => {
                    const ok = await confirm({
                      title: '친구를 끊을까요?',
                      message:
                        '상대에게 알리지 않아요. 끊은 뒤에는 서로의 최근 활동이 보이지 않아요.',
                      confirmLabel: '끊기',
                    });
                    if (ok) {
                      await act(
                        () => removeFriendship(item.handle),
                        () => friends.removeItem(item.handle),
                      );
                    }
                  }}
                >
                  친구 끊기
                </button>
              </span>
            </li>
          ))}
        </ul>
        {friends.hasMore && (
          <button type="button" onClick={() => void friends.loadMore()}>
            더 보기
          </button>
        )}
        {error && (
          <p role="alert" className="form-error">
            {error}
          </p>
        )}
      </section>
      {dialog}
    </>
  );
}
