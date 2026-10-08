package com.team.blog.account.web;

import com.team.blog.account.application.AvailabilityService;
import com.team.blog.account.application.AvailabilityService.HandleAvailability;
import com.team.blog.account.application.AvailabilityService.NicknameAvailability;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 사용 가능 확인 API (contracts {@code availability} 태그). 로그인 없이 쓸 수 있다. */
@RestController
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/api/handles/availability")
    public HandleAvailability handle(
            @RequestParam(name = "handle", defaultValue = "") String handle,
            HttpServletRequest request) {
        return availabilityService.checkHandle(limit(handle), ClientIp.of(request));
    }

    @GetMapping("/api/nicknames/availability")
    public NicknameAvailability nickname(
            @RequestParam(name = "nickname", defaultValue = "") String nickname,
            @CurrentUser(required = false) Optional<Long> memberId,
            HttpServletRequest request) {
        return availabilityService.checkNickname(
                limit(nickname), memberId.orElse(null), ClientIp.of(request));
    }

    /** 계약 maxLength 64. 더 긴 값은 잘라도 형식 오류로 판정된다. */
    private static String limit(String value) {
        return value.length() > 64 ? value.substring(0, 65) : value;
    }
}
