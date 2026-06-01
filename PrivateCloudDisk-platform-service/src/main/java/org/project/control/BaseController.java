package org.project.control;

import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.project.control.result.JsonResult;
import org.project.service.ex.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class BaseController {

    //操作成功的状态码
    public static final int OK = 200;

    /**
     * 1.@ExceptionHandler表示该方法用于处理捕获抛出的异常
     * 2.什么样的异常才会被这个方法处理呢?所以需要ServiceException.class,这样的话只要是抛出ServiceException异常就会被拦截到handleException方法,此时handleException方法就是请求处理方法,返回值就是需要传递给前端的数据
     * 3.被ExceptionHandler修饰后如果项目发生异常,那么异常对象就会被自动传递给此方法的参数列表上,所以形参就需要写Throwable e用来接收异常对象
     */
    @ExceptionHandler({ServiceException.class})
    public JsonResult<Void> handleServiceException(Throwable e) {
        JsonResult<Void> result = new JsonResult<>(e);
        if (e instanceof UserNotFoundException) {
            result.setCode(4000);
            result.setMessage(e.getMessage());
        } else if (e instanceof PasswordNotMatchException) {
            result.setCode(5000);
            result.setMessage(e.getMessage());
        } else if (e instanceof AccountOrPhoneNumberException) {
            result.setCode(6000);
            result.setMessage(e.getMessage());
        } else if (e instanceof InsertException) {
            result.setCode(7000);
            result.setMessage(e.getMessage());
        } else if (e instanceof VerificationCodeErrorException) {
            result.setCode(8000);
            result.setMessage(e.getMessage());
        } else if (e instanceof PhoneNumberDuplicatedException) {
            result.setCode(9000);
            result.setMessage(e.getMessage());
        } else if (e instanceof ChunkDuplicatedException) {
            result.setCode(10000);
            result.setMessage(e.getMessage());
        } else if (e instanceof NodeNotExistException) {
            result.setCode(11000);
            result.setMessage(e.getMessage());
        } else if (e instanceof NodeUserNotMatchException) {
            result.setCode(12000);
            result.setMessage(e.getMessage());
        } else if (e instanceof ParentNodeNotExistException) {
            result.setCode(13000);
            result.setMessage(e.getMessage());
        } else if (e instanceof InvalidChunkIndexException) {
            result.setCode(14000);
            result.setMessage(e.getMessage());
        } else if (e instanceof InvalidUploadsSessionException) {
            result.setCode(15000);
            result.setMessage(e.getMessage());
        } else if (e instanceof FileNameDuplicatedException) {
            result.setCode(16000);
            result.setMessage(e.getMessage());
        } else if (e instanceof NodeStatusException) {
            result.setCode(17000);
            result.setMessage(e.getMessage());
        } else if (e instanceof FileNotExistException) {
            result.setCode(18000);
            result.setMessage(e.getMessage());
        }
        //将处理输出日志...
        log.warn("服务器运行时发生异常:" + result.toString());
        return result;
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(error -> {
            String fieldName = error.getPropertyPath().toString();
            String errorMessage = error.getMessage();
            errors.put(fieldName, errorMessage);
        });
        //将处理输出日志...
        log.warn("服务器运行时发生异常:" + errors.toString());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * 处理参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public JsonResult<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        //将处理输出日志...
        log.warn("参数校验失败:" + msg);
        return new JsonResult<>("参数校验失败: " + msg, 400);
    }

    /**
     * 处理Bearer Token无效异常 通行令牌验证失败
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<String> handleInvaildAccessTokenExceptions(JwtException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }
}
