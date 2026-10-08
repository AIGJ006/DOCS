/**
 * 신고·관리자 숨김·회원 정지 응답 모양 (014 contracts/openapi.yaml components.schemas).
 * 시각은 모두 UTC ISO-8601 문자열이다.
 */
import type { ReasonCode } from '../../features/moderation/reasonLabels';

export type ReportReason = ReasonCode;
export type TargetType = 'POST' | 'COMMENT';
export type CaseStatus = 'PENDING' | 'HIDDEN' | 'REJECTED' | 'CLOSED_NO_TARGET';
export type CaseTab = 'PENDING' | 'HANDLED';

/** 처리 화면의 "현재: …" 상태. 글은 앞 6개, 댓글은 VISIBLE·HIDDEN·DELETED·POST_NOT_VISIBLE·AUTHOR_WITHDRAWN·GONE. */
export type TargetState =
  | 'PUBLIC'
  | 'PRIVATE'
  | 'TRASHED'
  | 'HIDDEN'
  | 'AUTHOR_WITHDRAWN'
  | 'GONE'
  | 'VISIBLE'
  | 'DELETED'
  | 'POST_NOT_VISIBLE';

/** 사유 코드 → 신고 수 (0인 사유는 빠짐). */
export type ReasonCounts = Partial<Record<ReportReason, number>>;

export interface ReportRequest {
  targetType: TargetType;
  targetId: number;
  reason: ReportReason;
  /** 기타일 때만 보낸다 */
  detail?: string | null;
}

export interface ReportAccepted {
  accepted: true;
}

export interface CaseListItem {
  caseId: number;
  targetType: TargetType;
  /** 글: 스냅샷 제목, 댓글: 내용 앞 40자. 30일 정리 뒤 null */
  title: string | null;
  authorHandle: string | null;
  reportCount: number;
  reasonCounts: ReasonCounts;
  lastReportedAt: string | null;
  status: CaseStatus;
  handledAt: string | null;
  /** 자동 종료면 null */
  handledByNickname: string | null;
  targetHiddenNow: boolean;
}

export interface CasePage {
  items: CaseListItem[];
  nextCursor: string | null;
}

export interface CaseAuthor {
  handle: string | null;
  nickname: string | null;
  joinedAt: string;
  hiddenCount: number;
  suspendedNow: boolean;
  suspensionCount: number;
}

export interface CaseDetail {
  caseId: number;
  targetType: TargetType;
  postId: number | null;
  commentId: number | null;
  snapshotTitle: string | null;
  snapshotContent: string | null;
  currentState: TargetState;
  status: CaseStatus;
  createdAt: string;
  handledAt: string | null;
  handledByNickname: string | null;
  reportCount: number;
  reasonCounts: ReasonCounts;
  otherDetails: { detail: string | null; reportedAt: string }[];
  reportedByMe: boolean;
  onlyMyReport: boolean;
  author: CaseAuthor;
}

export type ResolutionAction = 'HIDE' | 'REJECT';

export interface HiddenState {
  hidden: boolean;
  caseId: number | null;
}

export type SuspensionDuration = 'P1D' | 'P7D' | 'P30D' | 'PERMANENT';

export interface SuspensionRecord {
  id: number;
  reason: string;
  startedAt: string;
  /** null = 영구 */
  endsAt: string | null;
  suspendedByHandle: string | null;
  liftedAt: string | null;
  /** 기한 지남 자동 해제면 null */
  liftedByHandle: string | null;
}

export interface AdminMemberView {
  handle: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED' | 'WITHDRAWN';
  joinedAt: string;
  hiddenCount: number;
  openSuspension: SuspensionRecord | null;
  history: SuspensionRecord[];
}
