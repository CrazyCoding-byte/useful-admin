package com.yzx.model.exception;


import com.yzx.model.ResultCode;

public class CustomException extends RuntimeException {

    private ResultCode resultCode;

    public CustomException(ResultCode resultCode) {
        super("错误代码 :" + resultCode.code() + "错误信息 :" + resultCode.msg());
        this.resultCode = resultCode;
    }

    public ResultCode getResultCode() {
        return this.resultCode;
    }
}
