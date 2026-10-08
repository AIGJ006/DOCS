interface NicknameInputProps {
  id: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  invalid?: boolean;
  describedBy?: string;
  /** 도움말 대신 보일 문구 (예: 소셜 이름이 규칙에 맞지 않아 비웠을 때 "닉네임을 입력해 주세요") */
  helpText?: string;
}

/** 닉네임 칸 (09 §2, FR-022~027). 앞뒤 공백 제거·NFC 정규화·판정은 서버가 한다. */
export default function NicknameInput({
  id,
  value,
  onChange,
  placeholder,
  invalid,
  describedBy,
  helpText,
}: NicknameInputProps) {
  const helpId = `${id}-help`;
  return (
    <div className="field">
      <label htmlFor={id}>닉네임</label>
      <input
        id={id}
        autoComplete="nickname"
        value={value}
        placeholder={placeholder}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={invalid || undefined}
        aria-describedby={[helpId, describedBy].filter(Boolean).join(' ')}
      />
      <p id={helpId} className="field-help">
        {helpText ?? '한글·영문·숫자로 2~10자'}
      </p>
    </div>
  );
}
