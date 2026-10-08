/**
 * 저장 충돌 상태 (002 T102, FR-021·022·024, US5).
 *
 * - 자동 저장·수동 저장·발행이 409 `VERSION_CONFLICT`를 받으면 `report(server)`: 서버 전송을 멈추고(이 기기 저장과 편집은 계속)
 *   배너를 띄운다. 입력 중에 비교 창을 저절로 띄우지는 않는다. 에디터를 열 때의 충돌만 바로 연다(`{ open: true }`).
 * - 충돌 중에는 [비교하기]·[저장]·[발행]이 비교 창을 연다(`intercept()`).
 * - 비교 창에서 고른 결과: 편집 중인 내용으로 저장(`keptMine`) / 저장된 내용 불러오기(`loadServer` — 편집 중 내용은 7일 백업) /
 *   새 임시글로 따로 저장(부모가 새 글로 이동). 창을 닫으면(`dismiss`) 배너와 전송 멈춤이 그대로 남는다.
 */
import { ApiError } from '../../api/client';
import type { SaveResponse, ServerCopy } from '../../api/posts';
import type { AutosaveContent, AutosaveQueue } from './autosaveQueue';
import { saveBackup as saveBackupToStore, type DraftBackup } from './localDraftStore';
import { formatSavedAt } from './saveStatusText';

export interface ConflictState {
  server: ServerCopy;
  /** 이 탭이 충돌을 알게 된 시각(ms). */
  detectedAt: number;
}

export interface ConflictDeps {
  queue: Pick<AutosaveQueue, 'reportConflict' | 'resolveConflict' | 'baseVersion'>;
  memberId: number;
  postId: number;
  onChange: (state: ConflictState | null) => void;
  onOpenCompare: () => void;
  now?: () => number;
  saveBackup?: (memberId: number, postId: number, backup: DraftBackup) => Promise<void>;
}

/** 배너 문구 (FR-021). 시각은 서버에 저장된 시각(서울). */
export function conflictBannerText(savedAt: string): string {
  const time = formatSavedAt(savedAt);
  return `⚠ 다른 탭이나 기기에서 이 글이 수정되었어요${time ? `(${time})` : ''}. 지금 내용은 이 기기에만 저장되고 있어요.`;
}

/** 409 `VERSION_CONFLICT` 오류의 `details.server`. 다른 오류면 null. */
export function serverCopyOf(error: unknown): ServerCopy | null {
  if (!(error instanceof ApiError) || error.code !== 'VERSION_CONFLICT') {
    return null;
  }
  const server = (error.details as { server?: ServerCopy } | null)?.server;
  return server ?? null;
}

export class ConflictController {
  private current: ConflictState | null = null;

  constructor(private readonly deps: ConflictDeps) {}

  get active(): boolean {
    return this.current !== null;
  }

  get state(): ConflictState | null {
    return this.current;
  }

  /** 충돌을 알았다. 서버 전송을 멈추고 배너를 띄운다. `open`이면 비교 창도 바로 연다(에디터 열기 때만). */
  report(server: ServerCopy, options: { open?: boolean } = {}): void {
    this.current = { server, detectedAt: (this.deps.now ?? Date.now)() };
    this.deps.queue.reportConflict();
    this.deps.onChange(this.current);
    if (options.open) {
      this.deps.onOpenCompare();
    }
  }

  /** 비교 창에서 다시 저장하다 또 충돌했다 — 서버 내용만 바꾼다. */
  updateServer(server: ServerCopy): void {
    if (this.current) {
      this.current = { ...this.current, server };
      this.deps.onChange(this.current);
    }
  }

  /** [비교하기]·[저장]·[발행]: 충돌 중이면 비교 창을 열고 true(원래 동작은 하지 않음). */
  intercept(): boolean {
    if (!this.current) {
      return false;
    }
    this.deps.onOpenCompare();
    return true;
  }

  /** 비교 창을 닫았다. 배너와 전송 멈춤은 남는다. */
  dismiss(): void {
    // 상태를 그대로 둔다 (FR-024: 닫기 → 배너 유지)
  }

  /** [편집 중인 내용으로 저장] 성공 — 그 버전을 기준으로 다시 자동 저장한다. */
  keptMine(response: SaveResponse, mine: { title: string; contentMd: string }): void {
    this.deps.queue.resolveConflict(
      { ...mine, baseVersion: response.version },
      mine,
      response.savedAt,
    );
    this.clear();
  }

  /**
   * [저장된 내용 불러오기] — 편집 중 내용을 이 기기 백업(`draft-backup:`, 7일)에 두고 서버 내용·버전으로 바꾼다. 에디터에 넣을 내용을
   * 돌려준다.
   */
  async loadServer(mine: { title: string; contentMd: string }): Promise<AutosaveContent> {
    const state = this.current;
    if (!state) {
      throw new Error('충돌 상태가 아닙니다');
    }
    const save = this.deps.saveBackup ?? saveBackupToStore;
    await save(this.deps.memberId, this.deps.postId, {
      title: mine.title,
      contentMd: mine.contentMd,
      baseVersion: this.deps.queue.baseVersion,
      backedUpAt: (this.deps.now ?? Date.now)(),
    });
    const content: AutosaveContent = {
      title: state.server.title,
      contentMd: state.server.contentMd,
      baseVersion: state.server.version,
    };
    this.deps.queue.resolveConflict(
      content,
      { title: state.server.title, contentMd: state.server.contentMd },
      state.server.savedAt,
    );
    this.clear();
    return content;
  }

  private clear(): void {
    this.current = null;
    this.deps.onChange(null);
  }
}
