package com.aws.alphabetuniverse.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AccountBlockedException extends ResponseStatusException {

    public static final String CODE = "ACCOUNT_BLOCKED";
    public static final String MESSAGE =
            "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";

    public AccountBlockedException() {
        super(HttpStatus.FORBIDDEN, MESSAGE);
    }
}
