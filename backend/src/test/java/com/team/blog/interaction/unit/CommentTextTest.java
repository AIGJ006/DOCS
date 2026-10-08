package com.team.blog.interaction.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.interaction.domain.CommentReasonCode;
import com.team.blog.interaction.domain.CommentText;
import org.junit.jupiter.api.Test;

/** 댓글 내용 정리 (007 T003, FR-004·FR-005, research R3). */
class CommentTextTest {

    @Test
    void NFC로_합친다() {
        String decomposed = "가"; // ㄱ + ㅏ (조합형)
        assertThat(CommentText.normalize(decomposed)).isEqualTo("가");
    }

    @Test
    void 폭_0과_방향_제어_글자를_지운다() {
        assertThat(CommentText.normalize("안​녕‮하﻿세요⁦")).isEqualTo("안녕하세요");
    }

    @Test
    void 줄바꿈은_남기고_CRLF는_LF로() {
        assertThat(CommentText.normalize("첫 줄\r\n둘째 줄\r셋째 줄\n넷째")).isEqualTo("첫 줄\n둘째 줄\n셋째 줄\n넷째");
    }

    @Test
    void 탭은_공백_하나로() {
        assertThat(CommentText.normalize("a\tb")).isEqualTo("a b");
    }

    @Test
    void 앞뒤_공백과_줄바꿈을_지운다() {
        assertThat(CommentText.normalize("\n\n  내용  \n\n")).isEqualTo("내용");
    }

    @Test
    void 빈_줄_여러_개는_하나로() {
        assertThat(CommentText.normalize("가\n\n\n\n나\n \n\t\n다")).isEqualTo("가\n\n나\n\n다");
    }

    @Test
    void 공백만이면_COMMENT_REQUIRED() {
        String normalized = CommentText.normalize(" ​\n\t 　 ");
        assertThat(CommentText.check(normalized, 1000)).contains(CommentReasonCode.COMMENT_REQUIRED);
        assertThat(CommentText.check("", 1000)).contains(CommentReasonCode.COMMENT_REQUIRED);
    }

    @Test
    void 이모지는_코드_포인트로_센다() {
        String thousand = "😀".repeat(1000);
        assertThat(CommentText.check(CommentText.normalize(thousand), 1000)).isEmpty();
        String over = "😀".repeat(1001);
        assertThat(CommentText.check(CommentText.normalize(over), 1000))
                .contains(CommentReasonCode.COMMENT_TOO_LONG);
    }

    @Test
    void 한글_자모는_지우지_않는다() {
        assertThat(CommentText.normalize("ㅋㅋㅋ ㅠㅠ")).isEqualTo("ㅋㅋㅋ ㅠㅠ");
    }

    @Test
    void HTML과_Markdown은_글자_그대로() {
        String raw = "<script>alert(1)</script> **굵게** https://example.com";
        assertThat(CommentText.normalize(raw)).isEqualTo(raw);
    }
}
