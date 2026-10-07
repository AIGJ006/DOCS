package com.team.blog.account.application;

/** 블로그 주인 (005 블로그 머리말·{@code /@{handle}}). 탈퇴 유예·익명 처리된 회원은 만들지 않는다. */
public record BlogOwner(long id, String handle, String nickname, String bio) {}
