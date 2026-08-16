package com.example.tomatomall.exception;
/*
catch全局exception并处理
*/
import com.example.tomatomall.vo.Response;
import com.example.tomatomall.service.cache.ProductCacheFallbackRejectedException;
import com.example.tomatomall.service.cache.ProductCacheSingleFlightRejectedException;
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

    @ExceptionHandler(InvalidCheckoutRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<String> handleInvalidCheckoutRequest(InvalidCheckoutRequestException exception) {
        return Response.buildFailure(exception.getMessage(), "400");
    }

    @ExceptionHandler(OrderCheckoutConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Response<String> handleOrderCheckoutConflict(OrderCheckoutConflictException exception) {
        return Response.buildFailure(exception.getMessage(), "409");
    }

    @ExceptionHandler(OrderCheckoutUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Response<String> handleOrderCheckoutUnavailable(OrderCheckoutUnavailableException exception) {
        return Response.buildFailure(exception.getMessage(), "503");
    }

    @ExceptionHandler(ProductCacheFallbackRejectedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Response<String> handleProductCacheFallbackRejected(ProductCacheFallbackRejectedException exception) {
        return Response.buildFailure(exception.getMessage(), "503");
    }

    @ExceptionHandler(ProductCacheSingleFlightRejectedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Response<String> handleProductCacheSingleFlightRejected(
            ProductCacheSingleFlightRejectedException exception
    ) {
        return Response.buildFailure(exception.getMessage(), "503");
    }
}
