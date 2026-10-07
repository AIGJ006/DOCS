package com.team.blog.support.permission;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 행동 실행 결과. HTTP 행동은 상태 코드와 오류 본문의 {@code code}, 목록 행동은 대상 글이 들어 있었는지({@code included})를 담는다.
 *
 * @param status HTTP 상태 (목록 행동도 응답 상태를 담는다)
 * @param code 오류 본문의 {@code code} (성공·본문 없음이면 {@code null})
 * @param included 목록 행동이면 대상 글 포함 여부, 아니면 {@code null}
 */
public record ActionResult(int status, String code, Boolean included) {

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z0-9_]+)\"");

    /** 응답에서 상태와 오류 {@code code}를 꺼낸다 (4xx·5xx일 때만 code). */
    public static ActionResult of(MvcResult result) {
        int status = result.getResponse().getStatus();
        String code = null;
        if (status >= 400) {
            String body =
                    new String(
                            result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
            Matcher m = CODE.matcher(body);
            code = m.find() ? m.group(1) : null;
        }
        return new ActionResult(status, code, null);
    }

    /** 목록 행동 결과. */
    public static ActionResult listed(int status, boolean included) {
        return new ActionResult(status, null, included);
    }
}
