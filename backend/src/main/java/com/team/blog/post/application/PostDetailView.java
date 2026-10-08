package com.team.blog.post.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 글 상세 응답 (005 T032, contracts {@code PostDetail}, data-model §5). {@code content_md}는 담지 않는다 —
 * 독자에게는 발행 때 정화된 {@link #contentHtml()}만 준다(40 §6, FR-037).
 *
 * <p>{@code null} 필드는 JSON에서 {@code null}로 남긴다(화면이 "없음"과 "빈 값"을 구분한다).
 *
 * @param id 글 번호
 * @param status 글 상태
 * @param editorPath 작성자용 편집 화면 주소 (임시글 응답 {@link Draft}에만 있다 — 여기서는 항상 {@code null})
 * @param canonicalPath 바른 글 주소 {@code /@{handle}/posts/{id}} (화면이 주소를 맞춘다)
 * @param visibility 공개 범위
 * @param title 제목 (글자 그대로)
 * @param contentHtml 발행 때 정화된 본문 HTML
 * @param hasCodeBlock 코드 블록이 있는가 (화면이 강조 모듈을 지연 로드할지 판단)
 * @param displayedAt 화면에 쓰는 날짜 — 공개 글은 최초 공개 일자, 그 밖은 발행 일자 (research R-13)
 * @param firstPublicAt 최초 공개 일자 (비공개로만 발행했으면 {@code null})
 * @param publishedAt 최초 발행 일자
 * @param editedAt 재발행 일자 (없으면 {@code null} — 공개 범위만 바꾼 글은 없다)
 * @param tags 태그 이름 (입력 순서; 조회 실패·미구현이면 빈 목록)
 * @param likeCount 좋아요 수
 * @param viewCount 조회 수 (저장값 그대로 — 상세 조회가 올리지 않는다)
 * @param commentCount 댓글 수
 * @param author 작성자
 * @param viewer 보는 사람 기준 플래그
 * @param authorView 작성자에게만 주는 정보 (그 밖은 {@code null})
 */
public record PostDetailView(
        long id,
        String status,
        String editorPath,
        String canonicalPath,
        String visibility,
        String title,
        String contentHtml,
        boolean hasCodeBlock,
        Instant displayedAt,
        Instant firstPublicAt,
        Instant publishedAt,
        Instant editedAt,
        List<String> tags,
        int likeCount,
        long viewCount,
        int commentCount,
        Author author,
        ViewerFlags viewer,
        AuthorView authorView)
        implements PostDetailResponse {

    /** 작성자 본인의 임시글 (005 T056, research R-22): 본문 없이 에디터 주소만 준다. */
    public static Draft draft(long id, String editorPath) {
        return new Draft(id, "DRAFT", editorPath);
    }

    /**
     * 작성자 본인이 자기 임시글 상세를 열었을 때의 응답 — contracts {@code PostDetail}의 "status가 DRAFT면
     * id·status·editorPath만".
     *
     * @param id 글 번호
     * @param status 항상 {@code DRAFT}
     * @param editorPath {@code /write/{id}}
     */
    public record Draft(long id, String status, String editorPath) implements PostDetailResponse {}

    /**
     * @param handle 블로그 주소
     * @param nickname 닉네임
     * @param profileImageUrl 작은 프로필 사진 주소 (없으면 {@code null} → 화면 기본 아이콘)
     * @param bio 소개 (줄바꿈 그대로; 화면이 {@code pre-line} 텍스트로 출력)
     */
    public record Author(String handle, String nickname, String profileImageUrl, String bio) {}

    /**
     * 보는 사람 기준 플래그 (004 research R-29 {@code PostActions} 버튼 표시와 맞춤).
     *
     * <p>{@code isAuthor}·{@code isAdmin}은 JSON 이름을 그대로 쓴다 — record 접근자 {@code isAuthor()}가 {@code
     * author}로 줄어들지 않게 {@link JsonProperty}로 못 박았다.
     *
     * @param loggedIn 로그인했는가
     * @param isAuthor 이 글의 작성자인가
     * @param likedByMe 내가 좋아요를 눌렀는가 (비회원·작성자는 항상 {@code false})
     * @param followingAuthor 내가 작성자를 팔로우 중인가 (비회원·작성자는 항상 {@code false})
     * @param emailVerified 이메일 인증을 마쳤는가
     * @param isAdmin 관리자인가
     */
    public record ViewerFlags(
            boolean loggedIn,
            @JsonProperty("isAuthor") boolean isAuthor,
            boolean likedByMe,
            boolean followingAuthor,
            boolean emailVerified,
            @JsonProperty("isAdmin") boolean isAdmin) {}

    /**
     * 작성자에게만 주는 정보 (005 T055, FR-038·039). 독자에게는 {@code null}이다.
     *
     * @param hasDraft 발행 뒤 고치는 중인 내용이 있는가
     * @param draftSavedAt 그 내용을 저장한 시각 ({@code post_draft.updated_at})
     * @param hidden 관리자가 숨긴 글인가
     * @param hiddenReason 숨김 사유 (표시 규칙은 014)
     */
    public record AuthorView(
            boolean hasDraft, Instant draftSavedAt, boolean hidden, String hiddenReason) {}
}
