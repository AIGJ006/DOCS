package com.team.blog.tag.application.suggest;

/** 태그 추천 공급자 (013 data-model §3). */
public enum Provider {
    /** 외부 AI (Google Gemini 무료 등급). */
    GEMINI,
    /** 자체 AI (같은 Compose의 Ollama). */
    OLLAMA
}
