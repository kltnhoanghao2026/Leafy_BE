package com.leafy.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
        // System errors (9xxx)
        SYS_UNCATEGORIZED(HttpStatus.INTERNAL_SERVER_ERROR, 9999, "error.sys.uncategorized"),

        // Authentication errors (1xxx)
        AUTH_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, 1001, "error.auth.unauthenticated"),
        AUTH_UNAUTHORIZED(HttpStatus.FORBIDDEN, 1002, "error.auth.unauthorized"),
        JWT_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 1003, "error.jwt.invalid.token"),
        JWT_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, 1004, "error.jwt.expired.token"),
        JWT_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, 1005, "error.jwt.signature.invalid"),
        AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 1006, "error.auth.invalid.credentials"),
        AUTH_DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, 1007, "error.auth.device.id.required"),
        AUTH_DEVICE_MISMATCH(HttpStatus.FORBIDDEN, 1008, "error.auth.device.mismatch"),
        AUTH_SESSION_KICKED(HttpStatus.UNAUTHORIZED, 1009, "error.auth.session.kicked"),
        TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, 1010, "error.token.revoked"),
        TOKEN_REPLAY_DETECTED(HttpStatus.UNAUTHORIZED, 1011, "error.token.replay.detected"),
        RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 1012, "error.rate.limit.exceeded"),
        REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, 1013, "error.refresh.token.not.found"),
        REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, 1014, "error.refresh.token.invalid"),
        TOKEN_FAMILY_REVOKED(HttpStatus.UNAUTHORIZED, 1015, "error.token.family.revoked"),

        // User account errors (2xxx)
        ACC_PHONE_NUMBER_ALREADY_USED(HttpStatus.CONFLICT, 2001, "error.acc.phone.number.already.used"),
        ACC_EMAIL_ALREADY_USED(HttpStatus.CONFLICT, 2002, "error.acc.email.already.used"),
        ACC_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, 2003, "error.acc.account.not.found"),
        USER_NOT_FOUND(HttpStatus.NOT_FOUND, 2004, "error.user.not.found"),
        FILE_NOT_FOUND(HttpStatus.NOT_FOUND, 2005, "error.file.not.found"),
        INVALID_OTP(HttpStatus.BAD_REQUEST, 2006, "error.invalid.otp"),
        ACC_WRONG_PASSWORD(HttpStatus.CONFLICT, 2007, "error.acc.wrong.password"),
        ACC_IS_OAUTH(HttpStatus.CONFLICT, 2008, "error.acc.is.oauth"),
        CIC_IS_EXIST(HttpStatus.CONFLICT, 2009, "error.cic.is.exist"),

        // OTP errors (20xx)
        OTP_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, 2010, "error.otp.cooldown.active"),
        OTP_MAX_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, 2011, "error.otp.max.attempts.exceeded"),
        OTP_EXPIRED(HttpStatus.BAD_REQUEST, 2012, "error.otp.expired"),
        OTP_INVALID(HttpStatus.BAD_REQUEST, 2013, "error.otp.invalid"),
        OTP_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, 2014, "error.otp.purpose.mismatch"),
        OTP_NOT_FOUND(HttpStatus.NOT_FOUND, 2015, "error.otp.not.found"),
        REGISTRATION_DATA_EXPIRED(HttpStatus.BAD_REQUEST, 2016, "error.registration.data.expired"),
        PLANT_NOT_FOUND(HttpStatus.NOT_FOUND, 2017, "error.plant.not.found"),
        SPECIES_NOT_FOUND(HttpStatus.NOT_FOUND, 2018, "error.species.not.found"),
        PLANT_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, 2019, "error.plant.event.not.found"),

        // Role and permission errors (21xx)
        ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, 2101, "error.role.not.found"),
        PERM_NOT_FOUND(HttpStatus.NOT_FOUND, 2102, "error.perm.not.found"),
        PERMISSION_IN_USE(HttpStatus.CONFLICT, 2103, "error.permission.in.use"),

        // VALIDATION (22xx)
        VALIDATION_ERROR(HttpStatus.BAD_REQUEST, 2200, "error.validation.error"),
        PROMOTION_CODE_REQUIRED(HttpStatus.BAD_REQUEST, 2201, "error.promotion.code.required"),
        INVALID_STATUS(HttpStatus.BAD_REQUEST, 2202, "error.invalid.status"),
        INVALID_DATE_ATTRIBUTE_PAIR(HttpStatus.BAD_REQUEST, 2203, "error.invalid.date.attribute.pair"),
        INVALID_YEAR_ATTRIBUTE_PAIR(HttpStatus.BAD_REQUEST, 2204, "error.invalid.year.attribute.pair"),
        INVALID_OPERATION(HttpStatus.BAD_REQUEST, 2205, "error.invalid.operation"),
        INVALID_PROMOTION_CONDITION(HttpStatus.BAD_REQUEST, 2206, "error.invalid.promotion.condition"),
        ACC_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, 2207, "error.acc.password.mismatch")

        ;

        private final HttpStatus httpStatus;
        private final int code;
        private final String messageKey;

        ErrorCode(HttpStatus httpStatus, int code, String messageKey) {
                this.httpStatus = httpStatus;
                this.code = code;
                this.messageKey = messageKey;
        }

        public static ErrorCode fromCode(int code) {
                for (ErrorCode errorCode : ErrorCode.values()) {
                        if (errorCode.getCode() == code) {
                                return errorCode;
                        }
                }
                return SYS_UNCATEGORIZED;
        }
}
