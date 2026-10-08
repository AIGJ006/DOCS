/**
 * 신고 API (014 contracts/openapi.yaml `createReport`). 404여도 공통 404 화면으로 바꾸지 않는다 — 신고 창 안에서 안내한다.
 */
import { apiPost } from './client';
import type { ReportAccepted, ReportRequest } from './types/moderation';

export function createReport(request: ReportRequest): Promise<ReportAccepted> {
  const body: ReportRequest = {
    targetType: request.targetType,
    targetId: request.targetId,
    reason: request.reason,
    detail: request.reason === 'OTHER' ? (request.detail ?? '').trim() : null,
  };
  return apiPost<ReportAccepted>('/api/reports', body, { notFoundScreen: false });
}
