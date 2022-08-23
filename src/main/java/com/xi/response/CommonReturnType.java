package com.xi.response;

public class CommonReturnType {

    private String status;
    private Object data;


    public static CommonReturnType creat(Object result){

        return CommonReturnType.creat(result, "success");
    }

    public static CommonReturnType creat(Object result, String status){
        CommonReturnType type = new CommonReturnType();
        type.setStatus(status);
        type.setData(result);
        return type;
    }




    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
