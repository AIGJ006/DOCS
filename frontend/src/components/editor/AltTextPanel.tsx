import { useState } from 'react';
import { findMissingAlt, setAlt, type MissingAlt } from '../../features/image-upload/altText';

/**
 * 대체글 권유 (003 T067, US5, FR-033·034). 발행 설정 창 위쪽에 접힌 상태로 둔다. 대체글이 빈 우리 사진이 있으면 수를 알리고, [대체글
 * 넣기]를 누르면 사진마다 작은 미리보기와 입력칸을 보인다. 입력은 바로 본문에 반영한다. 발행은 막지 않는다.
 */
interface Props {
  contentMd: string;
  onChange: (contentMd: string) => void;
}

export const ALT_HELP =
  "사진을 볼 수 없는 분께 읽어 줄 설명이에요. 예: 'fetch join 전후 쿼리 수 비교 그래프'";
const RECOMMENDED_LENGTH = 125;

export default function AltTextPanel({ contentMd, onChange }: Props) {
  const missing = findMissingAlt(contentMd);
  /** 펼친 순간의 대상 — 입력 중 대체글이 채워져도 입력칸이 사라지지 않게 고정한다. */
  const [targets, setTargets] = useState<MissingAlt[] | null>(null);
  const [values, setValues] = useState<Record<number, string>>({});

  if (targets === null && missing.length === 0) {
    return null;
  }

  const update = (target: MissingAlt, value: string) => {
    setValues((current) => ({ ...current, [target.ordinal]: value }));
    onChange(setAlt(contentMd, target, value));
  };

  return (
    <section className="alt-text-panel" aria-label="대체글">
      {missing.length > 0 ? (
        <p className="alt-text-summary">
          대체글이 없는 사진이 {missing.length}장 있어요
          {targets === null ? (
            <>
              {' '}
              <button type="button" onClick={() => setTargets(missing)}>
                대체글 넣기
              </button>
            </>
          ) : null}
        </p>
      ) : null}
      {targets !== null ? (
        <ol className="alt-text-list">
          {targets.map((target, i) => {
            const value = values[target.ordinal] ?? '';
            const id = `alt-text-${target.ordinal}`;
            return (
              <li key={target.ordinal}>
                <img src={target.url} alt="" className="alt-text-thumb" loading="lazy" />
                <label htmlFor={id}>사진 {i + 1} 대체글</label>
                <input
                  id={id}
                  value={value}
                  onChange={(e) => update(target, e.target.value)}
                  aria-describedby={`${id}-help`}
                />
                <p id={`${id}-help`} className="alt-text-help">
                  {ALT_HELP}
                </p>
                {value.length > RECOMMENDED_LENGTH ? (
                  <p className="alt-text-length">
                    {RECOMMENDED_LENGTH}자 이내가 읽기 좋아요 (지금 {value.length}자)
                  </p>
                ) : null}
              </li>
            );
          })}
        </ol>
      ) : null}
    </section>
  );
}
