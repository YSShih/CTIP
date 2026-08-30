package com.ctip.application.admin;

/** 管理端點的操作與目標當下的狀態衝突(例如對停用中的來源要求同步),映射 409。 */
public class AdminConflictException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public AdminConflictException(String message) {
        super(message);
    }
}
