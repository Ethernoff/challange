package com.dws.challenge.exception;

public class NotExistedAccountException extends RuntimeException {
    public NotExistedAccountException(String message) {
        super(message);
    }
}
