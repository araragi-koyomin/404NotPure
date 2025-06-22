package com.example.tomatomall.exception;
/*
catch全局exception并处理
*/
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandle {
    @ExceptionHandler(value= TomatoException.class)
    public Response<String> handleTomatoException(TomatoException e) {
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(),e.getCode());
    }
}
