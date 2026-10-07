package com.team.blog.account.web;

import com.team.blog.account.application.AgreementService;
import com.team.blog.account.application.CurrentAgreements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 이용약관·개인정보 처리방침 현재 버전 ({@code getCurrentAgreements}, FR-011). 비로그인·재동의 전에도 허용. */
@RestController
@RequestMapping("/api/agreements")
public class AgreementController {

    private final AgreementService agreementService;

    public AgreementController(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @GetMapping("/current")
    public CurrentAgreements current() {
        return agreementService.current();
    }
}
