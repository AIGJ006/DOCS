/** 줄 바로 아래 오류 (FR-013). 글자는 텍스트로만 렌더링한다. */
export default function RowError({ message }: { message: string | undefined }) {
  if (!message) {
    return null;
  }
  return (
    <p className="manage-row-error" role="alert">
      {message}
    </p>
  );
}
