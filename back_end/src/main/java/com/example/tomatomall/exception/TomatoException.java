package com.example.tomatomall.exception;
/*
全局异常
*/
public class TomatoException extends RuntimeException{

    private String code;

    public TomatoException(String msg,String code) {
        super(msg);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static TomatoException userNameExist(){
        return new TomatoException("用户名已存在！","409");
    }

    public static TomatoException notLogin(){
        return new TomatoException("未登陆！","401");
    }

    public static TomatoException loginFailure(){
        return new TomatoException("用户名或密码错误！","401");
    }

    public static TomatoException uploadFailure(){
        return new TomatoException("上传失败！","500");
    }

    public static TomatoException userNotExist(){
        return new TomatoException("用户不存在！","404");
    }

    // 权限不足报错
    public static TomatoException noPermission() {
        return new TomatoException("无权限!", "403");
    }

}
