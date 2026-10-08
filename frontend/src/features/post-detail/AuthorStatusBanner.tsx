import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { discardWorkingCopy } from '../../api/posts';
import type { PostAuthorView, PostDetail } from '../../api/types/reading';
import ConfirmDialog from '../../components/editor/ConfirmDialog';
import { formatDateTime } from '../time/dateFormat';

/** 비공개 글 안내 (FR-039, 40 §2-1). */
export const PRIVATE_NOTICE = '나만 볼 수 있는 글이에요';
/** 관리자 숨김 안내 (FR-039). 사유 표시 규칙은 014가 정한다. */
export const HIDDEN_NOTICE = '운영 정책에 따라 숨겨진 글이에요. 다른 사람에게는 보이지 않아요';
/** [변경 취소] 확인 문구 — 002 에디터(`EditorPage.DISCARD_CONFIRM`)와 같은 문구. 006 `confirmDialogs.ts`가 생기면 그쪽으로 옮긴다. */
export const DISCARD_CONFIRM = '고치던 내용을 버리고 발행한 내용으로 돌아갈까요?';
const DISCARD_FAILED = '변경 취소에 실패했어요. 잠시 후 다시 시도해 주세요';

export interface AuthorStatusBannerProps {
  postId: number;
  visibility: PostDetail['visibility'];
  authorView: PostAuthorView;
  /** 작업본을 버린 뒤 — 상세를 다시 불러온다 */
  onDiscarded: () => void;
  /** 숨김 사유 표시 자리 (014가 채운다) */
  hiddenReasonSlot?: React.ReactNode;
}

/**
 * 작성자에게만 보이는 상태 안내 (005 T058, US4 #1·#3·#4, FR-038·039).
 *
 * - 수정 중: "수정 중인 내용이 있어요({M월 D일 HH:mm} 저장)" + [이어서 수정](→ `/write/{id}`) + [변경 취소](확인 창 →
 *   002 `DELETE /api/posts/{postId}/working-copy` → 상세 다시 불러오기). 시각은 한국 시간.
 * - 비공개: 🔒 비공개 + "나만 볼 수 있는 글이에요".
 * - 관리자 숨김: 숨김 안내(사유 표시는 014 자리).
 *
 * (구현 메모) 004 `VisibilityBadge`(T039)가 아직 없어 🔒 표시는 이 부품 안의 글자 배지로 둔다 — 004가 만들면 바꾼다.
 */
export default function AuthorStatusBanner({
  postId,
  visibility,
  authorView,
  onDiscarded,
  hiddenReasonSlot = null,
}: AuthorStatusBannerProps) {
  const [confirming, setConfirming] = useState(false);
  const [discarding, setDiscarding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isPrivate = visibility !== 'PUBLIC';
  if (!authorView.hasDraft && !isPrivate && !authorView.hidden) {
    return null;
  }

  async function discard() {
    setDiscarding(true);
    setError(null);
    try {
      await discardWorkingCopy(postId);
      setConfirming(false);
      onDiscarded();
    } catch (e) {
      setError(e instanceof ApiError && e.message ? e.message : DISCARD_FAILED);
      setConfirming(false);
    } finally {
      setDiscarding(false);
    }
  }

  return (
    <section
      data-testid="author-status"
      aria-label="내 글 상태"
      style={{
        display: 'grid',
        gap: '0.5rem',
        margin: '0 0 1rem',
        padding: '0.75rem 1rem',
        borderRadius: '0.5rem',
        background: 'var(--color-notice-bg)',
        fontSize: '0.875rem',
      }}
    >
      {isPrivate ? (
        <p style={{ margin: 0 }}>
          <span
            data-testid="private-badge"
            style={{
              display: 'inline-block',
              marginRight: '0.5rem',
              padding: '0 0.375rem',
              borderRadius: '0.25rem',
              background: 'var(--thumb-empty)',
            }}
          >
            🔒 비공개
          </span>
          {PRIVATE_NOTICE}
        </p>
      ) : null}
      {authorView.hidden ? (
        <p data-testid="hidden-notice" style={{ margin: 0 }}>
          {HIDDEN_NOTICE}
          {hiddenReasonSlot}
        </p>
      ) : null}
      {authorView.hasDraft ? (
        <p
          data-testid="draft-notice"
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: '0.5rem',
            margin: 0,
          }}
        >
          <span>
            수정 중인 내용이 있어요
            {authorView.draftSavedAt ? `(${formatDateTime(authorView.draftSavedAt)} 저장)` : ''}
          </span>
          <Link to={`/write/${postId}`}>이어서 수정</Link>
          <button type="button" onClick={() => setConfirming(true)} disabled={discarding}>
            변경 취소
          </button>
        </p>
      ) : null}
      {error ? (
        <p role="alert" style={{ margin: 0 }}>
          {error}
        </p>
      ) : null}
      {confirming ? (
        <ConfirmDialog
          message={DISCARD_CONFIRM}
          confirmLabel="버리기"
          cancelLabel="계속 고치기"
          busy={discarding}
          onConfirm={() => void discard()}
          onCancel={() => setConfirming(false)}
        />
      ) : null}
    </section>
  );
}
