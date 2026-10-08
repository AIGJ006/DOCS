package com.team.blog.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 보이지 않는 글자 공용 판정 (008 T005, research R2, docs/12 §7-4). 002 제목 정리와 008 태그 정규화가 함께 쓴다. */
class InvisibleCharactersTest {

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(
            strings = {
                "200B", "200C", "200D", "200E", "200F", "2060", "2061", "2064", "2066", "2069",
                "FEFF", "202A", "202B", "202C", "202D", "202E", "0000", "0009", "000A", "001F",
                "007F", "0080", "009F"
            })
    void 폭_0_방향_제어_제어_문자는_보이지_않는_글자다(String hex) {
        assertThat(InvisibleCharacters.isInvisible(Integer.parseInt(hex, 16))).isTrue();
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(
            strings = {
                "0020", "00A0", "3000", "0041", "0061", "AC00", "D7A3", "3131", "314B", "1100",
                "1161", "11A8", "2028", "1F525", "002D", "0023"
            })
    void 공백_한글_한글_자모_영문은_보이지_않는_글자가_아니다(String hex) {
        assertThat(InvisibleCharacters.isInvisible(Integer.parseInt(hex, 16))).isFalse();
    }

    @Test
    void strip은_보이지_않는_글자만_지우고_나머지는_그대로_둔다() {
        String zw = Character.toString(0x200B);
        String rlo = Character.toString(0x202E);
        String bom = Character.toString(0xFEFF);
        assertThat(InvisibleCharacters.strip(" a" + zw + "b" + rlo + "가\t" + bom + " "))
                .isEqualTo(" ab가 ");
        assertThat(InvisibleCharacters.strip("")).isEmpty();
        assertThat(InvisibleCharacters.strip(null)).isEmpty();
    }

    @Test
    void 이모지_같은_보충_평면_글자는_반으로_쪼개지_않는다() {
        String fire = Character.toString(0x1F525);
        assertThat(InvisibleCharacters.strip(fire + "hot")).isEqualTo(fire + "hot");
    }
}
