/** 태그 API 응답 타입 (008 contracts/openapi.yaml 그대로). */

/** 태그 페이지 머리말 (`TagSummary`). 형식에 맞으면 글이 없어도 `postCount = 0`. */
export interface TagSummary {
  name: string;
  /** 전체 공개 조건의 글 수 */
  postCount: number;
}

/** 이름 + 공개 글 수 (`TagCount`). */
export interface TagCount {
  name: string;
  postCount: number;
}

/** 전체 태그 목록 (`TagIndex`, 최대 100). */
export interface TagIndex {
  items: TagCount[];
}

/** 자동완성 후보 (`TagSuggestion`). 내 태그면 공개 글 수가 0일 수 있다. */
export interface TagSuggestion {
  name: string;
  postCount: number;
  mine: boolean;
}

/** 블로그 태그 줄 (`BlogTags`, 최대 100). 처음 `initialVisible`개를 보이고 [태그 더 보기]로 펼친다. */
export interface BlogTags {
  items: TagCount[];
  initialVisible: number;
}
