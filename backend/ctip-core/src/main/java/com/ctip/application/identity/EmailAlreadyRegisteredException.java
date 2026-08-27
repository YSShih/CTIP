package com.ctip.application.identity;

/** 註冊時 email 已存在(不變量 U1)。 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
