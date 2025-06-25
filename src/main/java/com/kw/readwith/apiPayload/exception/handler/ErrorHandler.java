package com.kw.readwith.apiPayload.exception.handler;

import com.kw.readwith.apiPayload.code.BaseErrorCode;
import com.kw.readwith.apiPayload.exception.GeneralException;

public class ErrorHandler extends GeneralException {

    public ErrorHandler(BaseErrorCode errorCode) {
        super(errorCode);
    }
}