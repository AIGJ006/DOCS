/**
 * 에디터 열기 판정 (002 T085, FR-022, data-model §5).
 *
 * - 이 기기에 안 보낸 변경(`dirty`)이 있고 그 기준 버전이 서버 버전과 같으면 → 이 기기 내용 + 안내
 * - `dirty`인데 기준 버전이 다르면 → 충돌: 이 기기 내용으로 열되 서버 전송은 멈추고 바로 비교 창을 연다(US5, FR-022).
 *   이 기기 내용은 백업(`draft-backup:`)에도 남긴다
 * - `dirty`가 아니면 → 서버 내용
 *
 * 열 때마다 보관 기간(7일)이 지난 백업을 정리한다.
 */
import {
  getWorkingCopy as fetchWorkingCopy,
  type ServerCopy,
  type WorkingCopy,
} from '../../api/posts';
import type { AutosaveContent } from './autosaveQueue';
import { loadDraft, purgeExpiredBackups, saveBackup } from './localDraftStore';

export const LOCAL_RESTORED_NOTICE = '이 기기에 저장되지 않은 변경을 불러왔어요';

export interface OpenedEditor {
  server: WorkingCopy;
  /** 에디터에 넣을 내용. */
  initial: AutosaveContent;
  /** 서버에 보낼 변경이 있는가 (이 기기 내용을 불러왔음). */
  dirty: boolean;
  notice: string | null;
  /** 이 기기의 안 보낸 변경과 기준 버전이 다른 서버 내용 — 바로 비교 창을 연다. 충돌이 아니면 null. */
  conflict: ServerCopy | null;
}

export interface OpenEditorDeps {
  getWorkingCopy?: (postId: number) => Promise<WorkingCopy>;
  now?: () => number;
}

export async function openEditor(
  memberId: number,
  postId: number,
  deps: OpenEditorDeps = {},
): Promise<OpenedEditor> {
  const now = deps.now ?? Date.now;
  const [server, local] = await Promise.all([
    (deps.getWorkingCopy ?? fetchWorkingCopy)(postId),
    loadDraft(memberId, postId).catch(() => null),
  ]);
  await purgeExpiredBackups(now()).catch(() => undefined);

  const fromServer: AutosaveContent = {
    title: server.title,
    contentMd: server.contentMd,
    baseVersion: server.version,
  };
  if (!local || !local.dirty) {
    return { server, initial: fromServer, dirty: false, notice: null, conflict: null };
  }
  const fromLocal: AutosaveContent = {
    title: local.title,
    contentMd: local.contentMd,
    baseVersion: local.baseVersion,
  };
  if (local.baseVersion === server.version) {
    return {
      server,
      initial: fromLocal,
      dirty: local.title !== server.title || local.contentMd !== server.contentMd,
      notice: LOCAL_RESTORED_NOTICE,
      conflict: null,
    };
  }
  await saveBackup(memberId, postId, { ...fromLocal, backedUpAt: now() }).catch(() => undefined);
  return {
    server,
    initial: fromLocal,
    dirty: true,
    notice: null,
    conflict: {
      title: server.title,
      contentMd: server.contentMd,
      version: server.version,
      savedAt: server.savedAt,
    },
  };
}
