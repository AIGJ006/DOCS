import { describe, expect, it } from 'vitest';
import { findMissingAlt, setAlt } from './altText';

const A = 'http://localhost:9000/blog/images/2026/10/3f1c2a9e-8d7b-4c1e-9a55-0b6f2a1d7e44.webp';
const B = 'http://localhost:9000/blog/images/2026/10/7a2b4c6d-1e3f-4a5b-8c7d-9e0f1a2b3c4d.gif';

describe('findMissingAlt', () => {
  it('우리 사진 중 대체글이 비었거나 공백뿐인 것만 순서대로 찾는다', () => {
    const md = `![](${A})\n\n![  ](${B})\n\n![설명](${A})`;
    expect(findMissingAlt(md).map((m) => m.url)).toEqual([A, B]);
  });

  it('코드 블록·인라인 코드 안, 외부 사진, local: 사진은 뺀다', () => {
    const md = [
      '```',
      `![](${A})`,
      '```',
      `\`![](${A})\``,
      '![](https://example.com/cat.png)',
      '![](local:a1b2c3)',
      `~~~md\n![](${B})\n~~~`,
      `![](${B})`,
    ].join('\n');
    expect(findMissingAlt(md).map((m) => m.url)).toEqual([B]);
  });
});

describe('setAlt', () => {
  it('그 위치만 바꾸고 ]와 \\를 이스케이프한다', () => {
    const md = `앞 ![](${A}) 가운데 ![](${B}) 뒤`;
    const missing = findMissingAlt(md);

    const next = setAlt(md, missing[1], '그래프 [전] \\ 후');

    expect(next).toBe(`앞 ![](${A}) 가운데 ![그래프 [전\\] \\\\ 후](${B}) 뒤`);
    expect(findMissingAlt(next).map((m) => m.url)).toEqual([A]);
  });

  it('줄바꿈은 공백으로 바꾼다', () => {
    const md = `![](${A})`;
    expect(setAlt(md, findMissingAlt(md)[0], '첫 줄\n둘째 줄')).toBe(`![첫 줄 둘째 줄](${A})`);
  });
});
