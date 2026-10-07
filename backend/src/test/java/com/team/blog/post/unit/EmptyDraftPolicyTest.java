package com.team.blog.post.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.post.domain.EmptyDraftPolicy;
import com.team.blog.post.domain.Post;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 빈 임시글 판정 (002 research B-9, 006 13 D-2와 공용). */
class EmptyDraftPolicyTest {

    @ParameterizedTest(name = "[{0}] [{1}] → 빈 글")
    @CsvSource(
            delimiter = '|',
            value = {"''|''", "'  '|'\n\t '", "'\r\n'|' '", "'\t'|''"})
    void 공백_문자뿐이면_빈_글이다(String title, String contentMd) {
        assertThat(EmptyDraftPolicy.isEmpty(title, contentMd)).isTrue();
    }

    @Test
    void null도_빈_값으로_본다() {
        assertThat(EmptyDraftPolicy.isEmpty(null, null)).isTrue();
        assertThat(EmptyDraftPolicy.isEmpty(null, "본문")).isFalse();
    }

    @Test
    void 제목이나_본문_하나라도_있으면_빈_글이_아니다() {
        assertThat(EmptyDraftPolicy.isEmpty("제목만", "")).isFalse();
        assertThat(EmptyDraftPolicy.isEmpty("", "본문만")).isFalse();
        assertThat(EmptyDraftPolicy.isEmpty("  ", "  x  ")).isFalse();
    }

    @Test
    void 업로드_대기_사진만_있는_본문도_빈_글이_아니다() {
        assertThat(EmptyDraftPolicy.isEmpty("", "![](local:3f2a)")).isFalse();
    }

    @Test
    void 공백_문자_집합_밖의_글자는_내용으로_본다() {
        // 배치 SQL btrim(x, E' \t\r\n')과 같아야 한다: 전각 공백·폭 0 공백은 지우지 않는다
        assertThat(EmptyDraftPolicy.isEmpty("　", "")).isFalse();
        assertThat(EmptyDraftPolicy.isEmpty("", "​")).isFalse();
    }

    @Test
    void 공백_문자_집합은_배치_SQL과_같은_상수다() {
        assertThat(EmptyDraftPolicy.WHITESPACE).isEqualTo(" \t\r\n");
        // T115 배치 SQL은 btrim(title, :ws)로 이 값을 바인딩한다
        assertThat(EmptyDraftPolicy.sqlWhitespace()).isEqualTo(EmptyDraftPolicy.WHITESPACE);
        assertThat(EmptyDraftPolicy.SQL_IS_EMPTY)
                .isEqualTo("btrim(p.title, :ws) = '' AND btrim(p.content_md, :ws) = ''");
    }

    @Test
    void 엔티티의_빈_글_판정은_정책에_위임한다() {
        Instant now = Instant.parse("2026-10-07T00:00:00Z");
        assertThat(Post.newDraft(1L, Visibility.PUBLIC, " ", "\n", now).isEmptyDraft()).isTrue();
        assertThat(Post.newDraft(1L, Visibility.PUBLIC, "제목", "", now).isEmptyDraft()).isFalse();
    }
}
