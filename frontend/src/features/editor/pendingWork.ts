/**
 * 로그아웃 전 미전송 작업 보내기 (002 ↔ 001 로그아웃 연결, FR-014·041). main.tsx가 001
 * `registerLogoutCleanup({ flushPendingWork, clearMemberDrafts })`로 등록한다.
 *
 * ① 열려 있는 에디터의 대기열을 바로 보낸다. ② 이 기기에 남은 그 회원의 다른 dirty 임시 글도 한 번씩 보낸다.
 * 하나라도 못 보내면 false — 001이 "보내지 못한 작업이 지워져요" 확인을 띄운다.
 */
import { autosave as sendAutosave, type SaveRequest, type SaveResponse } from '../../api/posts';
import { listDirtyDrafts, saveDraft } from './localDraftStore';

export interface ActiveEditor {
  memberId: number;
  postId: number;
  flush: () => Promise<boolean>;
}

let active: ActiveEditor | null = null;

/** 지금 열린 에디터를 등록한다. 돌려받은 함수로 해제한다. */
export function setActiveEditor(editor: ActiveEditor): () => void {
  active = editor;
  return () => {
    if (active === editor) {
      active = null;
    }
  };
}

export async function flushPendingWork(
  memberId: number,
  send: (postId: number, body: SaveRequest) => Promise<SaveResponse> = sendAutosave,
): Promise<boolean> {
  let ok = true;
  const editor = active && active.memberId === memberId ? active : null;
  if (editor) {
    ok = (await editor.flush().catch(() => false)) && ok;
  }
  for (const { postId, draft } of await listDirtyDrafts(memberId).catch(() => [])) {
    if (editor && editor.postId === postId) {
      continue;
    }
    try {
      const response = await send(postId, {
        title: draft.title,
        contentMd: draft.contentMd,
        baseVersion: draft.baseVersion,
      });
      await saveDraft(memberId, postId, {
        ...draft,
        baseVersion: response.version,
        dirty: false,
        updatedAt: Date.now(),
      });
    } catch {
      ok = false;
    }
  }
  return ok;
}
