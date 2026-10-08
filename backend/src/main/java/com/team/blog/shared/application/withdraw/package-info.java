/**
 * 탈퇴 30일 정리 확장점 (015 contracts/purge-steps.md §1). 각 모듈이 {@code WithdrawalPurgeStep}을 구현하고 015
 * account 모듈의 정리 작업이 순서대로 부른다 — account ↔ post 순환 의존을 만들지 않으려고 shared에 둔다(44 §4).
 */
package com.team.blog.shared.application.withdraw;
