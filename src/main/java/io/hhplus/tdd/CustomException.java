package io.hhplus.tdd;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CustomException extends RuntimeException {

    private final HttpStatus httpStatus;
    @Getter
    @AllArgsConstructor
    public enum ErrorMessageCode {
        AMOUNT_EXCEPTION("AMOUNT가 음수일 수 없습니다.", HttpStatus.BAD_REQUEST),
        TOO_MANY_AMOUNT_EXCEPTION("포인트는 100만을 초과할 수 없습니다.", HttpStatus.BAD_REQUEST),
        USER_ID_EXCEPTION("유저 아이디는 음수일 수 없습니다.", HttpStatus.BAD_REQUEST),
        NOT_ENOUGH_POINTS_EXCEPTION("보유 포인트가 부족합니다.", HttpStatus.BAD_REQUEST),
        WAIT_TIME_EXCEPTION("락 대기시간 초과", HttpStatus.LOCKED);

        private final String message;
        private final HttpStatus httpStatus;
    }

    public CustomException(ErrorMessageCode messageCode) {
        super(messageCode.getMessage());
        this.httpStatus = messageCode.getHttpStatus();
    }
}
