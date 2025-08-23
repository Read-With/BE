package com.kw.readwith.apiPayload.code.status;

import com.kw.readwith.apiPayload.code.BaseErrorCode;
import com.kw.readwith.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // 가장 일반적인 응답
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST,"COMMON400","잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED,"COMMON401","인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // For test
    TEMP_EXCEPTION(HttpStatus.BAD_REQUEST, "TEMP4001", "테스트 용도"),

    // Auth
    // 인증 관련 에러 상태
    SOCIAL_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "AUTH4001", "소셜 로그인 인증에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4002", "토큰이 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH4003", "토큰이 만료되었습니다."),
    TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH4004", "토큰이 일치하지 않습니다."),
    TOKEN_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH5001", "토큰 생성에 실패했습니다."),
    TOKEN_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH4005", "토큰 검증에 실패했습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "AUTH4006", "해당 유저가 존재하지 않습니다."),

    // Progress
    // 진도 관련 에러 상태
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "PROGRESS4001", "해당 책을 찾을 수 없습니다."),
    PROGRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "PROGRESS4002", "해당 진도를 찾을 수 없습니다."),
    BOOK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PROGRESS4003", "해당 책에 접근할 권한이 없습니다."),
    PROGRESS_DELETE_FAILED(HttpStatus.BAD_REQUEST, "PROGRESS4004", "삭제할 진도가 존재하지 않습니다."),

    // Admin
    BOOK_ALREADY_SUMMARIZED(HttpStatus.BAD_REQUEST, "ADMIN4001", "이미 요약이 완료된 책입니다."),
    CHAPTER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN4002", "해당 챕터를 찾을 수 없습니다."),
    CHAPTER_ALREADY_SUMMARIZED(HttpStatus.BAD_REQUEST, "ADMIN4003", "이미 요약이 완료된 챕터입니다."),
    CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN4004", "해당 인물을 찾을 수 없습니다."),
    JSON_PARSING_ERROR(HttpStatus.BAD_REQUEST, "ADMIN4005", "JSON 파일 파싱에 실패했습니다."),
    BOOK_CHARACTER_NOT_FOUND(HttpStatus.NOT_FOUND, "ADMIN4006", "해당 책에 등록된 인물이 아닙니다."),
    CHAPTER_NOT_BELONG_TO_BOOK(HttpStatus.BAD_REQUEST, "ADMIN4007", "해당 책에 속한 챕터가 아닙니다."),
    EVENT_DATA_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "ADMIN4008", "해당 챕터의 이벤트 데이터가 이미 존재합니다."), // [추가]

    // Bookmark
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOKMARK4001", "해당 북마크를 찾을 수 없습니다."),
    BOOKMARK_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "BOOKMARK4002", "동일한 위치에 이미 북마크가 존재합니다."),
    BOOKMARK_ACCESS_DENIED(HttpStatus.FORBIDDEN, "BOOKMARK4003", "해당 북마크에 접근할 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build()
                ;
    }
}
