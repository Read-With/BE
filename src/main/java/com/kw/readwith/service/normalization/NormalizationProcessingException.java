package com.kw.readwith.service.normalization;

import com.kw.readwith.domain.enums.NormalizationFailureCode;
import lombok.Getter;

@Getter
public class NormalizationProcessingException extends RuntimeException {

    private final NormalizationFailureCode failureCode;
    private final String step;

    public NormalizationProcessingException(NormalizationFailureCode failureCode, String step, String message) {
        super(message);
        this.failureCode = failureCode;
        this.step = step;
    }

    public NormalizationProcessingException(NormalizationFailureCode failureCode, String step, String message, Throwable cause) {
        super(message, cause);
        this.failureCode = failureCode;
        this.step = step;
    }
}
