package tech.ydb.jdbc.exception;

import tech.ydb.core.StatusCode;

public class YdbRetryableException extends YdbExecutionStatusException {

    public YdbRetryableException(Object response, StatusCode statusCode) {
        super(response, statusCode);
    }
}
