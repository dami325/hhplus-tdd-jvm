package io.hhplus.tdd;

public record ErrorResponse(
        Integer code,
        String message
) {
}
