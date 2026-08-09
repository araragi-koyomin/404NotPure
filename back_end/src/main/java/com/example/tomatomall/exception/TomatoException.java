package com.example.tomatomall.exception;

import lombok.Getter;

/*
全局异常
*/
@Getter
public class TomatoException extends RuntimeException{

    private String code;

    public TomatoException(String msg,String code) {
        super(msg);
        this.code = code;
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

    public static TomatoException telephoneExist() { return new TomatoException("电话号码已存在！","409");}

    public static TomatoException productNotExist(){
        return new TomatoException("商品不存在！","404");
    }

    public static TomatoException stockNotEnough(){
        return new TomatoException("库存不足！", "404");
    }

    public static TomatoException invalidOrderRequest() {
        return new TomatoException("订单信息不完整或购买数量非法", "400");
    }

    public static TomatoException stockDataInconsistent() {
        return new TomatoException("库存数据异常，请联系管理员", "500");
    }
    public static TomatoException advertisementNotExist(){
        return new TomatoException("商品不存在","400");
    }

    public static TomatoException commentExist() {
        return new TomatoException("不能发表重复评论！", "409");
    }

    public static TomatoException commentNotExist() {
        return new TomatoException("删除失败，评论不存在", "400");
    }

    public static TomatoException pointsInvalid() {
        return new TomatoException("积分非法", "400");
    }

    public static TomatoException existInCart() {
        return new TomatoException("商品已存在于购物车中", "400");
    }

    public static TomatoException spillStock() {
        return new TomatoException("修改数量超出库存", "400");
    }

    public static TomatoException failToUploadFile() {
        return new TomatoException("文件上传失败", "400");
    }

    public static TomatoException invalidImageFile() {
        return new TomatoException("仅支持 10MB 以内的 PNG、JPEG 或 GIF 图片", "400");
    }

    public static TomatoException orderNotExist() {
        return new TomatoException("订单不存在", "400");
    }

    public static TomatoException sessionNotExist() {
        return new TomatoException("会话不存在", "400");
    }

    public static TomatoException paymentError() {
        return new TomatoException("支付报错", "403");
    }
}
