package com.kw.readwith.apiPayload.exception;

import com.kw.readwith.apiPayload.code.BaseErrorCode;
import com.kw.readwith.apiPayload.code.ErrorReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode code;
    private final String overrideMessage;

    public GeneralException(BaseErrorCode code) {
        this.code = code;
        this.overrideMessage = null;
    }

    public GeneralException(BaseErrorCode code, String overrideMessage) {
        this.code = code;
        this.overrideMessage = overrideMessage;
    }

    public ErrorReasonDTO getErrorReason() {
        if (overrideMessage == null) {
            return this.code.getReason();
        }
        return ErrorReasonDTO.builder()
                .message(this.overrideMessage)
                .code(this.code.getReason().getCode())
                .isSuccess(false)
                .build();
    }

    public ErrorReasonDTO getErrorReasonHttpStatus(){
        if (overrideMessage == null) {
            return this.code.getReasonHttpStatus();
        }
        return ErrorReasonDTO.builder()
                .message(this.overrideMessage)
                .code(this.code.getReasonHttpStatus().getCode())
                .isSuccess(false)
                .httpStatus(this.code.getReasonHttpStatus().getHttpStatus())
                .build();
    }

    public BaseErrorCode getErrorCode() {
        return this.code;
    }
}
