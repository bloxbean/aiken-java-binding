package com.bloxbean.cardano.aiken.exception;

public class ApplyParamException extends RuntimeException {
    public ApplyParamException(String msg, Exception e) {
        super(msg, e);
    }

    public ApplyParamException(String msg) {
        super(msg);
    }
}
