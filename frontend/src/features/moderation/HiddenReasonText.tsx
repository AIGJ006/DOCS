import { reasonLabel } from './reasonLabels';

/**
 * 숨긴 글 작성자 안내 문장 (014 T041, FR-021). 사유가 있으면 "(사유: 스팸·광고)"를 넣고, 없으면 괄호 없이, 모르는 코드는 "기타".
 * 신고자·신고 수는 넣지 않는다(SC-004).
 */
export function hiddenNotice(reason: string | null | undefined): string {
  const head = '운영 정책에 따라 숨겨진 글이에요';
  const tail = '다른 사람에게는 보이지 않아요';
  if (!reason) {
    return `${head}. ${tail}`;
  }
  return `${head} (사유: ${reasonLabel(reason)}). ${tail}`;
}

export default function HiddenReasonText({ reason }: { reason: string | null | undefined }) {
  return <span className="hidden-reason-text">{hiddenNotice(reason)}</span>;
}
