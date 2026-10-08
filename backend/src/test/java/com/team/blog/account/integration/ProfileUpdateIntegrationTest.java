package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestLogin;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** US6 프로필 저장 (FR-028·047~052, SC-008, R-18·R-19·R-20, quickstart §4-6). */
class ProfileUpdateIntegrationTest extends IntegrationTestBase {

    private ResultActions save(Cookie session, String json) throws Exception {
        return mockMvc.perform(
                TestLogin.withCsrf(
                        patch("/api/me/profile")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json),
                        session));
    }

    private long profileImage(long uploaderId) {
        return image(uploaderId, "PROFILE", "TEMP");
    }

    private long image(long uploaderId, String purpose, String status) {
        return jdbc.queryForObject(
                "INSERT INTO image (uploader_id, storage_key, content_type, size_bytes, width, height,"
                        + " status, purpose) VALUES (?, ?, 'image/webp', 1000, 256, 256, ?, ?) RETURNING id",
                Long.class,
                uploaderId,
                "profile/" + UUID.randomUUID() + ".webp",
                status,
                purpose);
    }

    private String nickname(long memberId) {
        return jdbc.queryForObject(
                "SELECT nickname FROM member WHERE id = ?", String.class, memberId);
    }

    private String bio(long memberId) {
        return jdbc.queryForObject("SELECT bio FROM member WHERE id = ?", String.class, memberId);
    }

    private Long currentImage(long memberId) {
        List<Long> ids =
                jdbc.queryForList(
                        "SELECT id FROM image WHERE uploader_id = ? AND purpose = 'PROFILE'"
                                + " AND status = 'ATTACHED' AND detached_at IS NULL",
                        Long.class,
                        memberId);
        assertThat(ids).hasSizeLessThanOrEqualTo(1);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    @Test
    @DisplayName("#1 닉네임·소개·사진을 한 번에 저장 → 200 MyProfile, 사진 연결")
    void saveAllAtOnce() throws Exception {
        long id = members().member().handle("kim755030").nickname("처음이름").create();
        long imageId = profileImage(id);
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(
                        session,
                        "{\"nickname\":\"김민서\",\"bio\":\"백엔드 개발을 공부하고 있어요.\",\"profileImageId\":"
                                + imageId
                                + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("kim755030"))
                .andExpect(jsonPath("$.nickname").value("김민서"))
                .andExpect(jsonPath("$.bio").value("백엔드 개발을 공부하고 있어요."))
                .andExpect(jsonPath("$.profileImageId").value(imageId))
                .andExpect(jsonPath("$.profileImageUrl").isString())
                .andExpect(jsonPath("$.nicknameChangeAvailableAt").isString());
        assertThat(nickname(id)).isEqualTo("김민서");
        assertThat(currentImage(id)).isEqualTo(imageId);
        mockMvc.perform(get("/api/me/profile").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageId").value(imageId))
                .andExpect(jsonPath("$.bio").value("백엔드 개발을 공부하고 있어요."));
    }

    @Test
    @DisplayName("#2 올바른 닉네임 + 201자 소개 → 400 BIO_TOO_LONG, 닉네임도 그대로 (SC-008)")
    void oneFailureSavesNothing() throws Exception {
        long id = members().member().nickname("처음이름").create();
        long imageId = profileImage(id);
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(
                        session,
                        "{\"nickname\":\"새이름\",\"bio\":\""
                                + "가".repeat(201)
                                + "\",\"profileImageId\":"
                                + imageId
                                + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("bio"))
                .andExpect(jsonPath("$.errors[0].code").value("BIO_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].message").value("소개는 200자까지 쓸 수 있어요"));
        assertThat(nickname(id)).isEqualTo("처음이름");
        assertThat(bio(id)).isNull();
        assertThat(currentImage(id)).isNull();
    }

    @Test
    @DisplayName("여러 칸이 함께 틀리면 모두 돌려준다")
    void allErrorsReturned() throws Exception {
        long id = members().member().create();
        long other = members().member().create();
        long othersImage = profileImage(other);
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(
                        session,
                        "{\"nickname\":\"a\",\"bio\":\"1\\n2\\n3\\n4\\n5\",\"profileImageId\":"
                                + othersImage
                                + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(
                        jsonPath("$.errors[?(@.field=='nickname')].code")
                                .value("NICKNAME_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[?(@.field=='bio')].code").value("BIO_TOO_MANY_LINES"))
                .andExpect(
                        jsonPath("$.errors[?(@.field=='profileImageId')].code")
                                .value("INVALID_PROFILE_IMAGE"));
    }

    @Test
    @DisplayName("#3 <script> 소개는 원문 그대로 저장한다 (출력 때 이스케이프)")
    void bioStoredAsIs() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(session, "{\"bio\":\"<script>alert(1)</script>\"}").andExpect(status().isOk());
        assertThat(bio(id)).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("소개 정리: 앞뒤 공백 제거·CRLF → LF·연속 빈 줄 하나로, 빈 문자열은 null")
    void bioNormalized() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(session, "{\"bio\":\"  첫 줄\\r\\n\\r\\n\\r\\n둘째 줄  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("첫 줄\n\n둘째 줄"));
        save(session, "{\"bio\":\"   \"}").andExpect(status().isOk());
        assertThat(bio(id)).isNull();
    }

    @Test
    @DisplayName("#4 닉네임 변경 뒤 30일 안: 다음 변경 가능일 표시, 닉네임+소개 → 409(소개도 그대로), 소개만 → 200")
    void nicknameThirtyDayRule() throws Exception {
        long id = members().member().nickname("처음이름").create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        mockMvc.perform(get("/api/me/profile").cookie(session))
                .andExpect(jsonPath("$.nicknameChangeAvailableAt").value((Object) null));
        save(session, "{\"nickname\":\"두번째\"}").andExpect(status().isOk());
        Instant changedAt =
                jdbc.queryForObject(
                                "SELECT nickname_changed_at FROM member WHERE id = ?",
                                Timestamp.class,
                                id)
                        .toInstant();
        String expected = changedAt.plus(30, ChronoUnit.DAYS).toString();
        mockMvc.perform(get("/api/me/profile").cookie(session))
                .andExpect(
                        result ->
                                assertThat(
                                                Instant.parse(
                                                        com.jayway.jsonpath.JsonPath.read(
                                                                result.getResponse()
                                                                        .getContentAsString(),
                                                                "$.nicknameChangeAvailableAt")))
                                        .isEqualTo(Instant.parse(expected)));
        save(session, "{\"nickname\":\"세번째\",\"bio\":\"바뀌면 안 됨\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_CHANGE_TOO_SOON"))
                .andExpect(jsonPath("$.details.nextChangeAvailableAt").isString());
        assertThat(nickname(id)).isEqualTo("두번째");
        assertThat(bio(id)).isNull();
        save(session, "{\"bio\":\"소개만\"}").andExpect(status().isOk());
        assertThat(bio(id)).isEqualTo("소개만");
        // 같은 닉네임 재저장은 변경이 아니다
        save(session, "{\"nickname\":\"두번째\"}").andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT nickname_changed_at FROM member WHERE id = ?",
                                        Timestamp.class,
                                        id)
                                .toInstant())
                .isEqualTo(changedAt);
    }

    @Test
    @DisplayName("같은 닉네임 재저장은 nickname_changed_at을 바꾸지 않고, 대소문자만 바꾸면 변경으로 센다(자기 자신은 중복 아님)")
    void sameNicknameAndCaseChange() throws Exception {
        long id = members().member().nickname("Minseo").create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        save(session, "{\"nickname\":\"Minseo\"}").andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT nickname_changed_at FROM member WHERE id = ?",
                                Timestamp.class,
                                id))
                .isNull();
        save(session, "{\"nickname\":\"minseo\"}").andExpect(status().isOk());
        assertThat(nickname(id)).isEqualTo("minseo");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT nickname_changed_at FROM member WHERE id = ?",
                                Timestamp.class,
                                id))
                .isNotNull();
    }

    @Test
    @DisplayName("다른 회원이 쓰는 닉네임 → NICKNAME_DUPLICATE")
    void duplicateNickname() throws Exception {
        members().member().nickname("있는이름").create();
        long id = members().member().create();
        save(TestLogin.loginAs(mockMvc, id), "{\"nickname\":\"있는이름\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NICKNAME_DUPLICATE"));
    }

    @Test
    @DisplayName("#5 남이 올린 사진·글용 사진·없는 ID → 400 INVALID_PROFILE_IMAGE")
    void invalidImages() throws Exception {
        long id = members().member().create();
        long other = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        for (long imageId : new long[] {profileImage(other), image(id, "POST", "TEMP"), 999_999L}) {
            save(session, "{\"profileImageId\":" + imageId + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("profileImageId"))
                    .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROFILE_IMAGE"));
        }
        assertThat(currentImage(id)).isNull();
    }

    @Test
    @DisplayName("#6 새 사진 → 이전 행 detached_at, 새 행 ATTACHED / null → 이전 행만 뗀다")
    void replaceAndRemovePhoto() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        long first = profileImage(id);
        long second = profileImage(id);
        save(session, "{\"profileImageId\":" + first + "}").andExpect(status().isOk());
        save(session, "{\"profileImageId\":" + second + "}").andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                first))
                .isNotNull();
        assertThat(currentImage(id)).isEqualTo(second);
        save(session, "{\"profileImageId\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageId").value((Object) null))
                .andExpect(jsonPath("$.profileImageUrl").value((Object) null));
        assertThat(currentImage(id)).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT detached_at FROM image WHERE id = ?",
                                Timestamp.class,
                                second))
                .isNotNull();
        // 같은 사진을 다시 연결해도 된다
        save(session, "{\"profileImageId\":" + first + "}").andExpect(status().isOk());
        assertThat(currentImage(id)).isEqualTo(first);
    }

    @Test
    @DisplayName("#7 본문의 memberId·handle은 무시하고 본인만 바꾼다")
    void ignoresOtherFields() throws Exception {
        long id = members().member().handle("mine").nickname("내이름").create();
        long other = members().member().handle("other").nickname("남의이름").create();
        save(
                        TestLogin.loginAs(mockMvc, id),
                        "{\"memberId\":" + other + ",\"handle\":\"hacked\",\"nickname\":\"바꾼이름\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("mine"));
        assertThat(nickname(id)).isEqualTo("바꾼이름");
        assertThat(nickname(other)).isEqualTo("남의이름");
        assertThat(jdbc.queryForObject("SELECT handle FROM member WHERE id = ?", String.class, id))
                .isEqualTo("mine");
    }

    @Test
    @DisplayName("빈 본문 → 400")
    void emptyBody() throws Exception {
        long id = members().member().create();
        save(TestLogin.loginAs(mockMvc, id), "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("같은 회원 동시 저장 2건 → 둘 다 성공, 현재 사진 1장")
    void concurrentSaves() throws Exception {
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        long a = profileImage(id);
        long b = profileImage(id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (long imageId : new long[] {a, b}) {
                Callable<Integer> task =
                        () -> {
                            start.await();
                            return save(
                                            session,
                                            "{\"bio\":\"사진 "
                                                    + imageId
                                                    + "\",\"profileImageId\":"
                                                    + imageId
                                                    + "}")
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        Long current = currentImage(id);
        assertThat(current).isIn(a, b);
        assertThat(bio(id)).isEqualTo("사진 " + current);
    }

    @Test
    @DisplayName("인증 전 회원도 닉네임·소개를 바꿀 수 있다")
    void unverifiedAllowed() throws Exception {
        long id = members().member().emailVerified(false).create();
        save(TestLogin.loginAs(mockMvc, id), "{\"nickname\":\"새닉네임\",\"bio\":\"안녕\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인 401, 정지된 세션 403 ACCOUNT_SUSPENDED")
    void loginAndSuspension() throws Exception {
        mockMvc.perform(
                        TestLogin.withCsrf(
                                patch("/api/me/profile")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"bio\":\"x\"}"),
                                null))
                .andExpect(status().isUnauthorized());
        long id = members().member().create();
        Cookie session = TestLogin.loginAs(mockMvc, id);
        members().suspend(id, Instant.now().plus(1, ChronoUnit.DAYS), "스팸");
        save(session, "{\"bio\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
        assertThat(bio(id)).isNull();
    }
}
