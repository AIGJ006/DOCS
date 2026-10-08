import type { CaseDetail, CaseListItem } from '../../../api/types/moderation';

export function item(overrides: Partial<CaseListItem> = {}): CaseListItem {
  return {
    caseId: 1,
    targetType: 'POST',
    title: '신고된 글',
    authorHandle: 'writer01',
    reportCount: 3,
    reasonCounts: { SPAM: 2, ABUSE: 1 },
    lastReportedAt: new Date(Date.now() - 60_000).toISOString(),
    status: 'PENDING',
    handledAt: null,
    handledByNickname: null,
    targetHiddenNow: false,
    ...overrides,
  };
}

export function detail(overrides: Partial<CaseDetail> = {}): CaseDetail {
  return {
    caseId: 7,
    targetType: 'POST',
    postId: 42,
    commentId: null,
    snapshotTitle: '신고 당시 제목',
    snapshotContent: '당시 본문 <script>alert(1)</script>',
    currentState: 'PRIVATE',
    status: 'PENDING',
    createdAt: new Date(Date.now() - 3_600_000).toISOString(),
    handledAt: null,
    handledByNickname: null,
    reportCount: 2,
    reasonCounts: { SPAM: 1, OTHER: 1 },
    otherDetails: [{ detail: '광고 링크가 있어요', reportedAt: new Date().toISOString() }],
    reportedByMe: false,
    onlyMyReport: false,
    author: {
      handle: 'writer01',
      nickname: '글쓴이',
      joinedAt: '2026-01-01T00:00:00Z',
      hiddenCount: 1,
      suspendedNow: false,
      suspensionCount: 0,
    },
    ...overrides,
  };
}
