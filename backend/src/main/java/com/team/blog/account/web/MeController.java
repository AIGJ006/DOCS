package com.team.blog.account.web;

import com.team.blog.account.application.MeQueryService;
import com.team.blog.account.application.MeSummary;
import com.team.blog.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 정보 API ({@code /api/me/**}, 로그인 필요). 프로필·설정·비밀번호·재동의는 US4~US6에서 더한다. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeQueryService meQueryService;

    public MeController(MeQueryService meQueryService) {
        this.meQueryService = meQueryService;
    }

    /** 현재 로그인 상태 요약 ({@code getMe}). 비로그인이면 401. */
    @GetMapping
    public MeSummary me(@CurrentUser Long memberId) {
        return meQueryService.summary(memberId);
    }
}
