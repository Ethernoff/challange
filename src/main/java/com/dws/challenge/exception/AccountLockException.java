package com.dws.challenge.exception;

public class AccountLockException extends RuntimeException {
    public AccountLockException(String message) {
        super(message);
    }
}
