/**
 * 글 읽기 응답 타입 (005 contracts/openapi.yaml 그대로). 목록 카드·블로그 머리말·글 상세.
 */

/** 카드·상세의 작성자 (contracts `AuthorSummary`). */
export interface AuthorSummary {
  handle: string;
  nickname: string;
  /** 작은 프로필 사진(썸네일, 없으면 원본). 없으면 null → 화면 기본 아이콘 */
  profileImageUrl: string | null;
}

/** 글 카드 (contracts `PostCard`). 본문은 없다. */
export interface PostCard {
  id: number;
  /** `/@{handle}/posts/{id}` */
  url: string;
  title: string;
  excerpt: string | null;
  thumbnailUrl: string | null;
  /** 최초 공개 일자 (UTC ISO-8601, 마이크로초) */
  firstPublicAt: string;
  commentCount: number;
  likeCount: number;
  author: AuthorSummary;
}

/** 커서 목록 한 페이지 (contracts `PostCardPage`). `nextCursor`는 해석하지 않고 그대로 돌려준다. */
export interface PostCardPage {
  items: PostCard[];
  nextCursor: string | null;
}

/** 개인 블로그 머리말 (contracts `BlogHeader`). */
export interface BlogHeader {
  handle: string;
  nickname: string;
  bio: string | null;
  profileImageUrl: string | null;
  publicPostCount: number;
  isMe: boolean;
}

/**
 * 화면 버튼 판단용 보는 사람 기준 값.
 *
 * (구현 메모) 004 T060 `api/types/viewerFlags.ts`가 아직 없어 여기 둔다. 004가 만들면 `ViewerFlags`를
 * 그 파일에서 가져와 확장한다(이름·뜻은 같게 맞췄다).
 */
export interface ViewerFlags {
  loggedIn: boolean;
  emailVerified: boolean;
  isAdmin: boolean;
  isAuthor: boolean;
}

/** 상세의 보는 사람 기준 값 (contracts `PostDetail.viewer`). */
export interface PostDetailViewer extends ViewerFlags {
  /** 비회원·작성자는 항상 false */
  likedByMe: boolean;
  /** 비회원·작성자는 항상 false. 010 미구현이면 false */
  followingAuthor: boolean;
}

/** 작성자에게만 오는 값 (contracts `PostDetail.authorView`). */
export interface PostAuthorView {
  hasDraft: boolean;
  /** `post_draft.updated_at` — "수정 중인 내용이 있어요(10월 3일 14:03 저장)" */
  draftSavedAt: string | null;
  hidden: boolean;
  hiddenReason: string | null;
}

/** 작성자 카드용 작성자 (소개 포함). */
export interface PostDetailAuthor extends AuthorSummary {
  bio: string | null;
}

/** 글 상세 (contracts `PostDetail`). 작성자 본인의 임시글이면 {@link PostDetailDraft}가 온다. */
export interface PostDetail {
  id: number;
  status: 'PUBLISHED';
  /** 발행 글은 항상 null (`DRAFT` 응답에만 있다) */
  editorPath: string | null;
  canonicalPath: string;
  visibility: 'PUBLIC' | 'PRIVATE';
  title: string;
  /** 발행 때 정화된 HTML. 수정 중이어도 마지막 발행본 */
  contentHtml: string;
  hasCodeBlock: boolean;
  /** PUBLIC이면 firstPublicAt, 작성자가 보는 PRIVATE이면 publishedAt */
  displayedAt: string;
  firstPublicAt: string | null;
  publishedAt: string;
  /** 값이 있을 때만 "수정됨 · M월 D일" */
  editedAt: string | null;
  tags: string[];
  likeCount: number;
  viewCount: number;
  commentCount: number;
  author: PostDetailAuthor;
  viewer: PostDetailViewer;
  authorView: PostAuthorView | null;
}

/** 작성자 본인이 자기 임시글 상세를 열었을 때 (contracts `PostDetail` — id·status·editorPath만). 화면은 에디터로 옮긴다. */
export interface PostDetailDraft {
  id: number;
  status: 'DRAFT';
  /** `/write/{postId}` */
  editorPath: string;
}

/** `GET /api/posts/{postId}` 응답. */
export type PostDetailResponse = PostDetail | PostDetailDraft;
