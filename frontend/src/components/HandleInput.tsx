import { useEffect, useRef, useState } from 'react';
import { toHandleChars } from '../features/handle/handleChars';
import { prefillHandleFromEmail } from '../features/handle/prefillHandleFromEmail';

interface HandleInputProps {
  id: string;
  label: string;
  /** 접두어를 뺀 본문 */
  value: string;
  onChange: (value: string) => void;
  /** 소셜 가입: 고칠 수 없는 고정 접두어 */
  prefix?: 'go-' | 'gi-';
  /** 이 이메일로 미리 채운다. 사용자가 칸을 직접 고치면 더 이상 따라가지 않는다(US3 #3) */
  sourceEmail?: string;
  invalid?: boolean;
  describedBy?: string;
}

/**
 * 블로그 주소 칸 (08 §2~§4, FR-016~021). `inputmode="url"`·`autocapitalize="off"`로 모바일 자판을 영문으로 연다.
 */
export default function HandleInput({
  id,
  label,
  value,
  onChange,
  prefix,
  sourceEmail,
  invalid,
  describedBy,
}: HandleInputProps) {
  const edited = useRef(false);
  const [prefilled, setPrefilled] = useState(false);

  useEffect(() => {
    if (sourceEmail === undefined || edited.current) {
      return;
    }
    const next = prefillHandleFromEmail(sourceEmail);
    onChange(next);
    setPrefilled(next !== '');
    // onChange는 부모가 매번 새로 만들 수 있어 의존성에서 뺀다 — 이메일이 바뀔 때만 다시 채운다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceEmail]);

  const helpId = `${id}-help`;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className={prefix ? 'handle-input' : undefined}>
        {prefix && (
          <span className="handle-prefix" aria-hidden="true">
            {prefix}
          </span>
        )}
        <input
          id={id}
          inputMode="url"
          autoCapitalize="off"
          autoCorrect="off"
          autoComplete="off"
          spellCheck={false}
          maxLength={36}
          value={value}
          onChange={(event) => {
            edited.current = true;
            setPrefilled(false);
            onChange(toHandleChars(event.target.value));
          }}
          aria-invalid={invalid || undefined}
          aria-describedby={[helpId, describedBy].filter(Boolean).join(' ')}
        />
      </div>
      <p id={helpId} className="field-help">
        {prefix && (
          <>
            앞의 <code>{prefix}</code>는 가입 수단 표시라 바꿀 수 없어요.{' '}
          </>
        )}
        영문 소문자·숫자·_로 3~36자
      </p>
      {prefilled && (
        <p className="field-help">
          이메일 앞부분으로 미리 채웠어요. 이메일을 드러내고 싶지 않으면 바꿔 주세요.
        </p>
      )}
      <p className="field-help">블로그 주소는 가입 후 바꿀 수 없어요</p>
    </div>
  );
}
