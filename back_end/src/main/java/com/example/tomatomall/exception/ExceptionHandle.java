package com.example.tomatomall.exception;
/*
catch全局exception并处理
*/
import com.example.tomatomall.vo.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandle {
    @ExceptionHandler(value= TomatoException.class)
    public Response<String> handleTomatoException(TomatoException e) {
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(),e.getCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<String> handleInvalidRequest(Exception ignored) {
        return Response.buildFailure("请求参数不完整或格式错误", "400");
    }

    @ExceptionHandler(InvalidProductPageRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<String> handleInvalidProductPageRequest(InvalidProductPageRequestException exception) {
        return Response.buildFailure(exception.getMessage(), "400");
    }
}
