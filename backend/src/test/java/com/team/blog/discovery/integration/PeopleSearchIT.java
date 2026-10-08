package com.team.blog.discovery.integration;

import static com.team.blog.discovery.support.SearchApi.body;
import static com.team.blog.discovery.support.SearchApi.read;
import static com.team.blog.discovery.support.SearchApi.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.discovery.support.SearchApi;
import com.team.blog.interaction.support.FollowFixtures;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** 사람 검색 (012 T030, US3 #1·#2, FR-036, research R10). */
class PeopleSearchIT extends IntegrationTestBase {

    private SearchApi api() {
        return new SearchApi(mockMvc);
    }

    private static List<String> handles(MvcResult result) {
        return read(result, "$.items[*].handle");
    }

    @Test
    void US3_1_탈퇴_신청_익명_처리는_빼고_정지_회원은_넣는다() throws Exception {
        members().member().handle("kim7550").nickname("김민서").create();
        members().member().handle("kimgone").nickname("김민서탈퇴").status("WITHDRAWN").create();
        members().member().handle("kimanon").deleted().create();
        long suspended = members().member().handle("kimstop").nickname("김민서정지").create();
        members().suspend(suspended, Instant.now().plus(7, ChronoUnit.DAYS), "스팸");

        MvcResult result = api().people("김민서");

        assertThat(status(result)).as(body(result)).isEqualTo(200);
        assertThat(handles(result)).containsExactly("kim7550", "kimstop");
        assertThat(handles(api().people("kim"))).containsExactlyInAnyOrder("kim7550", "kimstop");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");
    }

    @Test
    void US3_2_정확히_일치_먼저_주소와_닉네임_각각() throws Exception {
        members().member().handle("akim7550").nickname("가가").create();
        members().member().handle("kim75501").nickname("나나").create();
        members().member().handle("kim7550").nickname("다다").create();

        assertThat(handles(api().people("kim7550")))
                .containsExactly("kim7550", "akim7550", "kim75501");
        assertThat(handles(api().people("KIM7550")).get(0)).isEqualTo("kim7550");

        members().member().handle("java1").nickname("자바왕").create();
        members().member().handle("java2").nickname("Java").create();
        members().member().handle("java3").nickname("자바").create();
        assertThat(handles(api().people("자바"))).containsExactly("java3", "java1");
        assertThat(handles(api().people("java")).get(0)).isEqualTo("java2");
    }

    @Test
    void 최대_20명() throws Exception {
        for (int i = 0; i < 25; i++) {
            members().member().handle("many" + (100 + i)).nickname("많은" + i).create();
        }
        assertThat(handles(api().people("many"))).hasSize(20);
    }

    @Test
    void 공백과_맨앞_골뱅이를_지운_한_덩어리() throws Exception {
        members().member().handle("kim7550").nickname("김민서").create();
        members().member().handle("lee1234").nickname("이민서").create();

        assertThat(handles(api().people("김 민서"))).containsExactly("kim7550");
        assertThat(handles(api().people("@kim7550"))).containsExactly("kim7550");
        assertThat(handles(api().people("민 서"))).containsExactlyInAnyOrder("kim7550", "lee1234");
    }

    @Test
    void 한글자는_400() throws Exception {
        for (String q : new String[] {"김", "@k", " ", ""}) {
            MvcResult result = api().people(q);
            assertThat(status(result)).as(q).isEqualTo(400);
            assertThat((String) read(result, "$.code")).isEqualTo("SEARCH_QUERY_TOO_SHORT");
        }
        assertThat(status(api().people(null))).isEqualTo(400);
    }

    @Test
    void 퍼센트와_밑줄은_글자_그대로() throws Exception {
        members().member().handle("a_bc").nickname("가나").create();
        members().member().handle("axbc").nickname("다라").create();

        assertThat(handles(api().people("a_b"))).containsExactly("a_bc");
        assertThat(handles(api().people("%%"))).isEmpty();
    }

    @Test
    void 프로필_사진과_소개_첫_줄() throws Exception {
        long withPhoto = members().member().handle("photo1").nickname("사진있음").create();
        new FollowFixtures(jdbc).profileImage(withPhoto, "profiles/p.png", "profiles/p_thumb.webp");
        jdbc.update("UPDATE member SET bio = ? WHERE id = ?", "백엔드 공부 기록\n둘째 줄", withPhoto);
        members().member().handle("photo2").nickname("사진없음").create();

        MvcResult result = api().people("사진");

        List<Map<String, Object>> items = read(result, "$.items");
        Map<String, Object> first =
                items.stream()
                        .filter(i -> "photo1".equals(i.get("handle")))
                        .findFirst()
                        .orElseThrow();
        assertThat(first).containsOnlyKeys("handle", "nickname", "profileImageUrl", "bioFirstLine");
        assertThat((String) first.get("profileImageUrl")).endsWith("profiles/p_thumb.webp");
        assertThat(first.get("bioFirstLine")).isEqualTo("백엔드 공부 기록");
        Map<String, Object> second =
                items.stream()
                        .filter(i -> "photo2".equals(i.get("handle")))
                        .findFirst()
                        .orElseThrow();
        assertThat(second.get("profileImageUrl")).isNull();
        assertThat(second.get("bioFirstLine")).isNull();
    }
}
