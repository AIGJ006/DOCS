import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { trashPost } from '../../api/managePosts';
import { useConfirm } from '../../components/useConfirm';
import { ACTION_FAILED, TRASH_CONFIRM } from './confirmDialogs';

/**
 * 글 상세의 작성자 [삭제] (005 `PostDetailPage`의 `deleteControl` 자리, 006 US1). 확인창(FR-018) 뒤 휴지통으로 옮기고
 * 내 글 관리의 휴지통 탭으로 간다(빈 임시글이었으면 임시글 탭). 404면 상세를 다시 불러 공통 404 화면이 뜨게 한다.
 */
export default function DetailDeleteButton({
  postId,
  reload,
}: {
  postId: number;
  reload: () => void;
}) {
  const navigate = useNavigate();
  const { confirm, dialog } = useConfirm();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onClick() {
    if (!(await confirm(TRASH_CONFIRM))) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await trashPost(postId);
      navigate('purged' in result ? '/manage/posts?tab=drafts' : '/manage/posts?tab=trash');
    } catch (caught) {
      setBusy(false);
      if (caught instanceof ApiError && caught.status === 404) {
        reload();
        return;
      }
      setError(caught instanceof ApiError ? caught.message : ACTION_FAILED);
    }
  }

  return (
    <>
      <button type="button" disabled={busy} onClick={() => void onClick()}>
        삭제
      </button>
      {error ? <span role="alert">{error}</span> : null}
      {dialog}
    </>
  );
}
