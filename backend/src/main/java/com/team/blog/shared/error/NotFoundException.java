package com.team.blog.shared.error;

/**
 * 없거나 볼 수 없는 리소스 → 404. 기능별 하위 클래스를 만들어도 되지만 응답 본문은 메시지·하위 클래스와 상관없이 항상 같다 ({@code
 * {code:"NOT_FOUND", message:"볼 수 없는 페이지예요", errors:[], details:null}} — constitution III). 생성자
 * 메시지는 서버 로그용이다.
 */
public class NotFoundException extends ApiException {

    public NotFoundException() {
        super(CommonReasonCode.NOT_FOUND);
    }

    /**
     * @param logMessage 서버 로그에만 남기는 설명 (응답에 실리지 않음)
     */
    public NotFoundException(String logMessage) {
        super(CommonReasonCode.NOT_FOUND, logMessage);
    }

    @Override
    public ErrorResponse toResponse() {
        return ErrorResponse.notFound();
    }
}
