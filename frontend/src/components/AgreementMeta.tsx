import type { AgreementDocument } from '../api/auth';

/** 약관 문서의 버전·시행일 (FR-011). */
export default function AgreementMeta({
  document,
  failed,
}: {
  document: AgreementDocument | undefined;
  failed: boolean;
}) {
  if (failed) {
    return <p className="field-error">버전 정보를 불러오지 못했어요</p>;
  }
  if (!document) {
    return <p aria-live="polite">불러오는 중이에요</p>;
  }
  return (
    <dl className="agreement-meta">
      <dt>버전</dt>
      <dd>{document.version}</dd>
      <dt>시행일</dt>
      <dd>
        <time dateTime={document.effectiveDate}>{document.effectiveDate}</time>
      </dd>
    </dl>
  );
}
