package com.xi.error;

public enum EmBussinessError implements CommonError {
    //通用错误类型_00001
    PARAMETER_VALIDATION_ERROR(10001, "参数不合法"),

    //未知错误
    UNKNOWN_ERROR(10002,"未知错误"),

    //10000开头为用户信息的相关错误定义
    USER_NOT_EXIST(20001, "用户不存在"),
    USER_NOT_LOGIN(20002,"用户尚未登录"),

    //3000开头：交易信息出错！
    TRADE_ERROR(30001, "库存不足！"),

    MQ_SEND_FAIL(30001, "库存异步消息失败")

    ;

    EmBussinessError(int errCode, String errMsg){
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    private int errCode;
    private String errMsg;


    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrMsg() {
        return this.errMsg;
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg = errMsg;
        return this;
    }
}
