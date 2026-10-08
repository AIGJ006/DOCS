/**
 * 관리자 API (014 contracts/openapi.yaml `/api/admin/**`). 일반 회원에게는 404라 공통 404 화면이 뜨는 게 맞지만, 처리 중
 * 대상이 사라진 경우를 화면에서 안내하려고 상태 변경 요청은 `notFoundScreen: false`로 보낸다.
 */
import { apiDelete, apiGet, apiPost, apiPut } from './client';
import type {
  AdminMemberView,
  CaseDetail,
  CasePage,
  CaseTab,
  HiddenState,
  ReportReason,
  ResolutionAction,
  SuspensionDuration,
  TargetType,
} from './types/moderation';

export function getCases(tab: CaseTab, cursor?: string | null): Promise<CasePage> {
  const params = new URLSearchParams({ tab });
  if (cursor) params.set('cursor', cursor);
  return apiGet<CasePage>(`/api/admin/reports?${params.toString()}`);
}

export function getCase(caseId: number | string): Promise<CaseDetail> {
  return apiGet<CaseDetail>(`/api/admin/reports/${caseId}`);
}

export function resolveCase(
  caseId: number,
  action: ResolutionAction,
  reason: ReportReason | null,
): Promise<CaseDetail> {
  return apiPost<CaseDetail>(
    `/api/admin/reports/${caseId}/resolution`,
    { action, reason: action === 'HIDE' ? reason : null },
    { notFoundScreen: false },
  );
}

function hiddenPath(type: TargetType, id: number): string {
  return type === 'POST' ? `/api/admin/posts/${id}/hidden` : `/api/admin/comments/${id}/hidden`;
}

export function hideTarget(
  type: TargetType,
  id: number,
  reason: ReportReason,
): Promise<HiddenState> {
  return apiPut<HiddenState>(hiddenPath(type, id), { reason }, { notFoundScreen: false });
}

export function unhideTarget(type: TargetType, id: number): Promise<HiddenState> {
  return apiDelete<HiddenState>(hiddenPath(type, id), undefined, { notFoundScreen: false });
}

export function getAdminMember(handle: string): Promise<AdminMemberView> {
  return apiGet<AdminMemberView>(`/api/admin/members/${encodeURIComponent(handle)}`);
}

export function suspendMember(
  handle: string,
  duration: SuspensionDuration,
  reason: string,
): Promise<AdminMemberView> {
  return apiPost<AdminMemberView>(
    `/api/admin/members/${encodeURIComponent(handle)}/suspensions`,
    { duration, reason },
    { notFoundScreen: false },
  );
}

export function liftSuspension(handle: string): Promise<AdminMemberView> {
  return apiDelete<AdminMemberView>(
    `/api/admin/members/${encodeURIComponent(handle)}/suspensions/current`,
    undefined,
    { notFoundScreen: false },
  );
}
