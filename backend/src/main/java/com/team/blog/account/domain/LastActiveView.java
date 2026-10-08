package com.team.blog.account.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 최근 활동 표시 (openapi {@code LastActive}). {@code days}는 {@code DAYS_AGO}일 때만(2~6) 있다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LastActiveView(LastActiveBucket bucket, Integer days) {}
