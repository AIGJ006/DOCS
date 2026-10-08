/**
 * moderation 모듈: 신고·숨김·정지 관리 (014). {@code report_case}·{@code report}는 이 모듈만 읽고 쓴다. 글·댓글 숨김은 post
 * {@code PostModerationService}·interaction {@code CommentModerationService}, 회원 정지는 account {@code
 * SuspensionService}로만 바꾼다(헌법 II).
 */
package com.team.blog.moderation;
