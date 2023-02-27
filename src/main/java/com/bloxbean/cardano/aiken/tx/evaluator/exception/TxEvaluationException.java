package com.bloxbean.cardano.aiken.tx.evaluator.exception;

public class TxEvaluationException extends RuntimeException {
    public TxEvaluationException(String msg, Exception e) {
        super(msg, e);
    }

    public TxEvaluationException(String msg) {
        super(msg);
    }
}
