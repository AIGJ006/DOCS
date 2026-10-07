import { evaluatePassword } from '../features/auth/passwordRules';

interface Props {
  password: string;
  id?: string;
}

/**
 * 입력하는 동안 비밀번호 규칙별 충족 여부를 글자와 ✓/✗로 보인다. 색만으로 표시하지 않는다 (FR-014, 07 §4).
 */
export default function PasswordRuleChecklist({ password, id }: Props) {
  const rules = evaluatePassword(password);
  return (
    <ul id={id} className="password-rules" aria-label="비밀번호 규칙" aria-live="polite">
      {rules.map((rule) => (
        <li key={rule.id} data-rule={rule.id} data-met={rule.met}>
          <span aria-hidden="true">{rule.met ? '✓' : '✗'}</span> {rule.label}
          <span className="visually-hidden">{rule.met ? ' 충족' : ' 아직 아니에요'}</span>
        </li>
      ))}
    </ul>
  );
}
