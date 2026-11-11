package com.aws.alphabetuniverse.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class EmailAlreadyExistsException extends ResponseStatusException {

    public static final String CODE = "EMAIL_ALREADY_EXISTS";
    public static final String MESSAGE =
            "Email đã thuộc tài khoản khác. Vui lòng đăng nhập bằng phương thức đã đăng ký.";

    public EmailAlreadyExistsException() {
        super(HttpStatus.CONFLICT, MESSAGE);
    }
}
