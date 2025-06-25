package com.kw.readwith.apiPayload.exception;

import com.kw.readwith.apiPayload.code.BaseErrorCode;
import com.kw.readwith.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException {

    private BaseErrorCode code;

    public ErrorReasonDTO getErrorReason() {
        return this.code.getReason();
    }

    public ErrorReasonDTO getErrorReasonHttpStatus(){
        return this.code.getReasonHttpStatus();
    }

    public BaseErrorCode getErrorCode() {
        return this.code;
    }
}
