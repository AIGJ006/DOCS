/**
 * 자동 저장 대기열 (002 T084, FR-007·010·011·021, 04 §2-1).
 *
 * - 입력이 1초 멈추면 이 기기(IndexedDB)에 저장한다.
 * - 입력이 3초 멈추면 서버로 보낸다. 계속 입력해도 30초마다 한 번은 보낸다. 바뀐 내용이 없으면 보내지 않는다.
 * - 탭 하나에서 요청은 한 번에 하나다(응답 뒤 다음).
 * - 실패하면 2s → 4s → 8s … 60s + 무작위 지연으로 다시 시도하고, 그동안 이 기기에 남긴다. 429는 `Retry-After`만큼 기다린다.
 * - 413·400·401·403·404는 다시 시도하지 않는다(오류 상태). 409는 서버 전송을 멈추고 충돌 상태가 된다(이 기기 저장은 계속).
 *
 * 타이머·네트워크·저장소는 주입받아 가짜 타이머로 시험한다.
 */
import { ApiError } from '../../api/client';
import type { SaveRequest, SaveResponse } from '../../api/posts';
import { EDITOR_CONFIG, retryDelayMs } from './editorConfig';
import type { LocalDraft } from './localDraftStore';

export type AutosaveStatus =
  | { kind: 'saved'; savedAt: string }
  | { kind: 'local' }
  | { kind: 'offline' }
  | { kind: 'conflict' }
  | { kind: 'error'; message: string };

export interface AutosaveContent {
  title: string;
  contentMd: string;
  baseVersion: number;
}

export interface AutosaveDeps {
  /** 에디터를 연 내용. */
  initial: AutosaveContent;
  /** 서버가 `initial.baseVersion`에 가진 내용. 없으면 `initial`과 같다(보낼 것 없음). */
  server?: { title: string; contentMd: string };
  send: (body: SaveRequest) => Promise<SaveResponse>;
  /** 대기 사진(`pendingImages`)은 넘기지 않는다 — 저장소가 그대로 둔다(003 US3). */
  saveLocal: (draft: Omit<LocalDraft, 'pendingImages'>) => Promise<void>;
  onStatus: (status: AutosaveStatus) => void;
  onConflict?: (error: ApiError) => void;
  onSaved?: (response: SaveResponse) => void;
  isOnline?: () => boolean;
  random?: () => number;
}

const TOO_LARGE_MESSAGE = '글이 너무 커서 서버에 저장하지 못했어요 — 이 기기에는 저장돼 있어요';
const LOGIN_MESSAGE = '로그인이 필요해요 — 이 기기에는 저장돼 있어요';

export class AutosaveQueue {
  private title: string;
  private contentMd: string;
  private version: number;
  private acked: { title: string; contentMd: string };
  private lastInputAt = 0;
  private firstUnsentAt: number | null = null;
  private localTimer: ReturnType<typeof setTimeout> | null = null;
  private sendTimer: ReturnType<typeof setTimeout> | null = null;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private inFlight: Promise<void> | null = null;
  private attempts = 0;
  private stopped: 'conflict' | 'error' | null = null;
  private paused = false;
  private disposed = false;
  private lastStatus: AutosaveStatus | null = null;

  constructor(private readonly deps: AutosaveDeps) {
    this.title = deps.initial.title;
    this.contentMd = deps.initial.contentMd;
    this.version = deps.initial.baseVersion;
    this.acked = deps.server ?? { title: deps.initial.title, contentMd: deps.initial.contentMd };
    if (this.hasUnsent()) {
      this.firstUnsentAt = Date.now();
      this.lastInputAt = Date.now() - EDITOR_CONFIG.serverSaveDebounceMs;
      this.scheduleSend();
    }
  }

  /** 서버가 가진 편집 버전 (다음 요청의 `baseVersion`). */
  get baseVersion(): number {
    return this.version;
  }

  get status(): AutosaveStatus | null {
    return this.lastStatus;
  }

  /** 서버에 아직 보내지 않은 변경이 있는가. */
  hasUnsent(): boolean {
    return this.title !== this.acked.title || this.contentMd !== this.acked.contentMd;
  }

  /** 지금 내용 (`pagehide` keepalive 전송용). */
  pendingBody(): SaveRequest {
    return { title: this.title, contentMd: this.contentMd, baseVersion: this.version };
  }

  /** 입력이 바뀔 때마다 부른다. */
  update(title: string, contentMd: string): void {
    if (this.disposed) {
      return;
    }
    this.title = title;
    this.contentMd = contentMd;
    this.lastInputAt = Date.now();
    if (this.stopped === 'error') {
      this.stopped = null;
    }
    this.scheduleLocal();
    if (this.hasUnsent()) {
      this.firstUnsentAt ??= this.lastInputAt;
      this.scheduleSend();
    }
  }

  /** 연결 상태가 바뀌었다 (`online`/`offline` 이벤트). 연결되면 기다리지 않고 보낸다. */
  setOnline(online: boolean): void {
    if (this.disposed) {
      return;
    }
    if (!online) {
      this.clearSendTimers();
      this.emitWaiting();
      return;
    }
    this.attempts = 0;
    this.clearSendTimers();
    if (this.hasUnsent()) {
      this.sendNow();
    } else {
      this.emitWaiting();
    }
  }

  /** 기다리지 않고 지금 보낸다. 끝난 뒤 모두 보냈는지(true) 알려 준다 (탭 가림·로그아웃). */
  async flush(): Promise<boolean> {
    this.flushLocal();
    if (this.inFlight) {
      await this.inFlight;
    }
    if (this.hasUnsent() && !this.stopped && !this.paused && this.online()) {
      this.clearSendTimers();
      this.sendNow();
      if (this.inFlight) {
        await this.inFlight;
      }
    }
    return !this.hasUnsent();
  }

  /** 발행 중에는 서버 전송을 멈춘다. 진행 중인 요청이 끝날 때까지 기다린다. */
  async pause(): Promise<void> {
    this.paused = true;
    this.clearSendTimers();
    if (this.inFlight) {
      await this.inFlight;
    }
  }

  resume(): void {
    this.paused = false;
    if (this.hasUnsent()) {
      this.scheduleSend();
    }
  }

  /** 수동 저장·발행처럼 다른 경로로 서버에 저장했다. */
  markSaved(response: SaveResponse, saved: { title: string; contentMd: string }): void {
    this.version = response.version;
    this.acked = saved;
    if (this.stopped === 'error') {
      this.stopped = null;
    }
    this.attempts = 0;
    this.persistLocal();
    if (this.hasUnsent()) {
      this.emit({ kind: 'local' });
      this.scheduleSend();
    } else {
      this.firstUnsentAt = null;
      this.emit({ kind: 'saved', savedAt: response.savedAt });
    }
  }

  /**
   * 충돌을 사용자가 정리했다 (US5: 편집 중인 내용으로 저장 / 저장된 내용 불러오기). `server`는 서버가 `content.baseVersion`에 가진
   * 내용, `savedAt`은 그 저장 시각(있으면 "저장됨"으로 보인다).
   */
  resolveConflict(
    content: AutosaveContent,
    server: { title: string; contentMd: string },
    savedAt?: string,
  ): void {
    this.stopped = null;
    this.attempts = 0;
    this.title = content.title;
    this.contentMd = content.contentMd;
    this.version = content.baseVersion;
    this.acked = server;
    this.persistLocal();
    if (this.hasUnsent()) {
      this.firstUnsentAt = Date.now();
      this.emit({ kind: 'local' });
      this.scheduleSend();
    } else {
      this.firstUnsentAt = null;
      if (savedAt) {
        this.emit({ kind: 'saved', savedAt });
      }
    }
  }

  /** 충돌 상태인가 (서버 전송을 멈췄음). */
  get inConflict(): boolean {
    return this.stopped === 'conflict';
  }

  /** 수동 저장·발행이 409를 받았다 — 자동 저장도 멈추고 충돌 상태로 둔다. */
  reportConflict(): void {
    this.stopped = 'conflict';
    this.clearSendTimers();
    this.emit({ kind: 'conflict' });
  }

  dispose(): void {
    this.disposed = true;
    this.clearSendTimers();
    if (this.localTimer) {
      clearTimeout(this.localTimer);
      this.localTimer = null;
    }
  }

  // ---- 내부 ----

  private online(): boolean {
    return this.deps.isOnline ? this.deps.isOnline() : true;
  }

  private emit(status: AutosaveStatus): void {
    this.lastStatus = status;
    if (!this.disposed) {
      this.deps.onStatus(status);
    }
  }

  /** 서버 전송을 기다리는 중의 상태. */
  private emitWaiting(): void {
    if (this.stopped === 'conflict') {
      this.emit({ kind: 'conflict' });
    } else if (this.stopped === 'error' && this.lastStatus?.kind === 'error') {
      this.emit(this.lastStatus);
    } else if (!this.online()) {
      this.emit({ kind: 'offline' });
    } else if (this.hasUnsent()) {
      this.emit({ kind: 'local' });
    }
  }

  private scheduleLocal(): void {
    if (this.localTimer) {
      clearTimeout(this.localTimer);
    }
    this.localTimer = setTimeout(() => {
      this.localTimer = null;
      this.persistLocal();
      this.emitWaiting();
    }, EDITOR_CONFIG.localSaveDebounceMs);
  }

  private flushLocal(): void {
    if (this.localTimer) {
      clearTimeout(this.localTimer);
      this.localTimer = null;
      this.persistLocal();
    }
  }

  private persistLocal(): void {
    void this.deps
      .saveLocal({
        title: this.title,
        contentMd: this.contentMd,
        baseVersion: this.version,
        dirty: this.hasUnsent(),
        updatedAt: Date.now(),
      })
      .catch(() => undefined);
  }

  private clearSendTimers(): void {
    if (this.sendTimer) {
      clearTimeout(this.sendTimer);
      this.sendTimer = null;
    }
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  private scheduleSend(): void {
    if (this.inFlight || this.stopped || this.paused || this.retryTimer || this.disposed) {
      return;
    }
    if (!this.online()) {
      return;
    }
    if (this.sendTimer) {
      clearTimeout(this.sendTimer);
    }
    const now = Date.now();
    const debounce = Math.max(0, this.lastInputAt + EDITOR_CONFIG.serverSaveDebounceMs - now);
    const maxWait = Math.max(
      0,
      (this.firstUnsentAt ?? now) + EDITOR_CONFIG.serverSaveMaxWaitMs - now,
    );
    this.sendTimer = setTimeout(
      () => {
        this.sendTimer = null;
        this.sendNow();
      },
      Math.min(debounce, maxWait),
    );
  }

  private retryAfter(ms: number): void {
    this.firstUnsentAt ??= Date.now();
    this.retryTimer = setTimeout(() => {
      this.retryTimer = null;
      this.sendNow();
    }, ms);
  }

  private sendNow(): void {
    if (this.inFlight || this.stopped || this.paused || this.disposed) {
      return;
    }
    if (!this.online()) {
      this.emitWaiting();
      return;
    }
    if (!this.hasUnsent()) {
      return;
    }
    const snapshot = { title: this.title, contentMd: this.contentMd };
    const body: SaveRequest = { ...snapshot, baseVersion: this.version };
    this.firstUnsentAt = null;
    this.inFlight = this.deps
      .send(body)
      .then(
        (response) => this.onSuccess(snapshot, response),
        (error: unknown) => this.onFailure(error),
      )
      .finally(() => {
        this.inFlight = null;
        if (!this.stopped && !this.retryTimer && this.hasUnsent()) {
          this.firstUnsentAt ??= Date.now();
          this.scheduleSend();
        }
      });
  }

  private onSuccess(snapshot: { title: string; contentMd: string }, response: SaveResponse): void {
    this.version = response.version;
    this.acked = snapshot;
    this.attempts = 0;
    this.deps.onSaved?.(response);
    this.persistLocal();
    if (this.hasUnsent()) {
      this.emit({ kind: 'local' });
    } else {
      this.emit({ kind: 'saved', savedAt: response.savedAt });
    }
  }

  private onFailure(error: unknown): void {
    if (error instanceof ApiError) {
      switch (error.status) {
        case 409:
          this.stopped = 'conflict';
          this.emit({ kind: 'conflict' });
          this.deps.onConflict?.(error);
          return;
        case 413:
          this.stop(TOO_LARGE_MESSAGE);
          return;
        case 401:
          this.stop(LOGIN_MESSAGE);
          return;
        case 400:
          this.stop(error.errors[0]?.message ?? error.message);
          return;
        case 403:
        case 404:
          this.stop(error.message);
          return;
        case 429:
          this.emitWaiting();
          this.retryAfter(Math.max(1, error.retryAfter ?? 5) * 1000);
          return;
        default:
          break;
      }
    }
    this.attempts += 1;
    if (error instanceof TypeError || !this.online()) {
      this.emit({ kind: 'offline' });
    } else {
      this.emit({ kind: 'local' });
    }
    this.retryAfter(retryDelayMs(this.attempts, this.deps.random));
  }

  private stop(message: string): void {
    this.stopped = 'error';
    this.emit({ kind: 'error', message });
  }
}
