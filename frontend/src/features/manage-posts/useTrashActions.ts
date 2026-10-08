import { useCallback } from 'react';
import { purgePost, restorePost, trashPost, type ManagePostItem } from '../../api/managePosts';
import type { PostStatus } from '../../api/posts';
import type { ConfirmOptions } from '../../components/ConfirmDialog';
import type { ToastMessage } from '../../components/Toast';
import { PURGE_CONFIRM, PURGED_EMPTY_TOAST, RESTORED_TOAST, TRASH_CONFIRM } from './confirmDialogs';
import type { RowActions } from './useRowAction';
import type { CountsDelta } from './useManagePosts';

export interface TrashActionDeps {
  confirm: (options: ConfirmOptions) => Promise<boolean>;
  rowAction: RowActions;
  showToast: (message: ToastMessage) => void;
  /** 그 줄을 목록에서 뺀다 */
  onRowRemoved: (id: number) => void;
  /** 탭 옆 숫자를 화면에서만 고친다 */
  onCountsChange: (delta: CountsDelta) => void;
}

export interface TrashActions {
  /** [삭제] — 확인창(FR-018) 뒤 휴지통으로. 빈 임시글은 바로 완전 삭제(FR-020) */
  trash: (row: ManagePostItem) => Promise<void>;
  /** [복구] — 확인창 없이(FR-027) */
  restore: (row: ManagePostItem) => Promise<void>;
  /** [영구 삭제] — 확인창(FR-029) 뒤 완전 삭제 */
  purge: (row: ManagePostItem) => Promise<void>;
}

function tabOf(status: PostStatus): 'drafts' | 'published' {
  return status === 'DRAFT' ? 'drafts' : 'published';
}

/** 복구 뒤 "복구했어요 [발행 글 탭에서 보기]"의 링크 (FR-027) */
export function restoredToast(status: PostStatus): ToastMessage {
  return status === 'PUBLISHED'
    ? {
        text: RESTORED_TOAST,
        action: { label: '발행 글 탭에서 보기', to: '/manage/posts?tab=published' },
      }
    : {
        text: RESTORED_TOAST,
        action: { label: '임시글 탭에서 보기', to: '/manage/posts?tab=drafts' },
      };
}

/**
 * 삭제·복구·영구 삭제 (006 T025·T032·T065). 실패 처리(줄 아래 이유, 404면 목록 다시 부르기)는 `useRowAction`에 맡기고,
 * 성공하면 그 줄만 빼고 탭 옆 숫자만 고친다(FR-004·FR-013).
 */
export function useTrashActions({
  confirm,
  rowAction,
  showToast,
  onRowRemoved,
  onCountsChange,
}: TrashActionDeps): TrashActions {
  const trash = useCallback(
    async (row: ManagePostItem) => {
      if (!(await confirm(TRASH_CONFIRM))) {
        return;
      }
      await rowAction.run(row.id, () => trashPost(row.id), {
        onSuccess: (result) => {
          onRowRemoved(row.id);
          if ('purged' in result) {
            onCountsChange({ [tabOf(row.status)]: -1 });
            showToast({ text: PURGED_EMPTY_TOAST });
          } else {
            onCountsChange({ [tabOf(row.status)]: -1, trash: +1 });
          }
        },
      });
    },
    [confirm, rowAction, showToast, onRowRemoved, onCountsChange],
  );

  const restore = useCallback(
    async (row: ManagePostItem) => {
      await rowAction.run(row.id, () => restorePost(row.id), {
        onSuccess: (result) => {
          onRowRemoved(row.id);
          onCountsChange({ trash: -1, [tabOf(result.status)]: +1 });
          showToast(restoredToast(result.status));
        },
      });
    },
    [rowAction, showToast, onRowRemoved, onCountsChange],
  );

  const purge = useCallback(
    async (row: ManagePostItem) => {
      if (!(await confirm(PURGE_CONFIRM))) {
        return;
      }
      await rowAction.run(row.id, () => purgePost(row.id), {
        onSuccess: () => {
          onRowRemoved(row.id);
          onCountsChange({ trash: -1 });
        },
      });
    },
    [confirm, rowAction, onRowRemoved, onCountsChange],
  );

  return { trash, restore, purge };
}
