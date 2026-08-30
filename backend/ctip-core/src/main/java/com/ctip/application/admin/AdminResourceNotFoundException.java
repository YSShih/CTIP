package com.ctip.application.admin;

/** 管理端點操作的目標不存在({@code /api/v1/admin/**};§9.1「管理」),映射 404。 */
public class AdminResourceNotFoundException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public AdminResourceNotFoundException(String message) {
        super(message);
    }
}
