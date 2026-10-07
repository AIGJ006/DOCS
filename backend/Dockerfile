# syntax=docker/dockerfile:1
# 빌드 컨텍스트: 저장소 루트 (docker compose의 app 서비스 참고)
# ① React 빌드 → ② backend 패키징(React 빌드를 정적 경로에 포함) → ③ 실행 이미지
# React 빌드를 API와 같은 도메인에서 서빙한다 (constitution "기술 제약").

FROM node:lts AS frontend
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS backend
WORKDIR /workspace/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
COPY --from=frontend /workspace/frontend/dist/ src/main/resources/static/
RUN ./mvnw -B -q -DskipTests -Dspotless.check.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 blog
COPY --from=backend /workspace/backend/target/blog-*.jar /app/app.jar
USER blog
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
