package com.wujia.pet.controller;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            IllegalArgumentException.class,
            IOException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class,
            DataIntegrityViolationException.class,
            AccessDeniedException.class,
            BindException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public Object handleReadableError(
            Exception exception,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        HttpStatus status = statusOf(exception);
        if (isAssetRequest(request)) {
            return ResponseEntity.status(status).build();
        }
        if (wantsJson(request)) {
            return ResponseEntity.status(status).body(errorBody(readableMessage(exception)));
        }
        redirectAttributes.addFlashAttribute("errorMessage", readableMessage(exception));
        return "redirect:" + safeReturnPath(request);
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpectedError(
            Exception exception,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        log.error("Unexpected request error: {} {}", request.getMethod(), request.getRequestURI(), exception);
        String message = "系统开小差了，请稍后再试。";
        if (isAssetRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (wantsJson(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(message));
        }
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + safeReturnPath(request);
    }

    static String readableMessage(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException || exception instanceof MultipartException) {
            return "上传文件过大或格式不正确，请压缩图片后重新上传。";
        }
        if (exception instanceof MissingServletRequestParameterException) {
            return "提交内容不完整，请补充必填信息后再试。";
        }
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return "提交的数据格式不正确，请检查日期、评分、坐标等内容。";
        }
        if (exception instanceof BindException bindException) {
            return bindErrorMessage(bindException);
        }
        if (exception instanceof MethodArgumentNotValidException validException) {
            return bindErrorMessage(validException);
        }
        if (exception instanceof ConstraintViolationException) {
            return "提交内容不符合要求，请检查长度、格式和必填项。";
        }
        if (exception instanceof DataIntegrityViolationException) {
            return "保存失败，可能存在必填项缺失或数据长度过长，请检查后再提交。";
        }
        if (exception instanceof AccessDeniedException) {
            return "没有权限执行这个操作。";
        }
        if (exception instanceof NoHandlerFoundException || exception instanceof NoResourceFoundException) {
            return "页面不存在或地址已失效。";
        }
        if (exception instanceof HttpRequestMethodNotSupportedException) {
            return "当前操作方式不支持，请刷新页面后重试。";
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            return "提交格式不支持，请检查上传内容后重试。";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "操作没有完成，请检查输入内容后再试。";
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    static String safeReturnPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return fallbackPath(request);
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path == null || path.isBlank() || path.equals("/error")) {
                return fallbackPath(request);
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (IllegalArgumentException ignored) {
            return fallbackPath(request);
        }
    }

    static boolean wantsJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (originalUri instanceof String original && (original.startsWith("/api/") || original.startsWith("/miniapp/api/") || original.equals("/qa/ask"))) {
            return true;
        }
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        return uri.startsWith("/api/")
                || uri.startsWith("/miniapp/api/")
                || uri.equals("/qa/ask")
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || (accept != null && accept.contains("application/json"));
    }

    static Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("message", message);
        return body;
    }

    private static HttpStatus statusOf(Exception exception) {
        if (exception instanceof AccessDeniedException) {
            return HttpStatus.FORBIDDEN;
        }
        if (exception instanceof NoHandlerFoundException || exception instanceof NoResourceFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (exception instanceof HttpRequestMethodNotSupportedException) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        }
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static boolean isAssetRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/css/")
                || uri.startsWith("/js/")
                || uri.startsWith("/img/")
                || uri.startsWith("/webjars/")
                || uri.equals("/favicon.ico")
                || uri.equals("/robots.txt");
    }

    private static String fallbackPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank() || uri.equals("/error")) {
            return "/";
        }
        if (uri.startsWith("/places")) {
            return "/places";
        }
        if (uri.startsWith("/qa")) {
            return "/qa";
        }
        if (uri.startsWith("/login")) {
            return "/login";
        }
        if (uri.startsWith("/register")) {
            return "/register";
        }
        return "/";
    }

    private static String bindErrorMessage(BindException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getDefaultMessage();
        }
        return "提交内容不符合要求，请检查必填项和格式。";
    }

    private static String bindErrorMessage(MethodArgumentNotValidException exception) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null && !fieldError.getDefaultMessage().isBlank()) {
            return fieldError.getDefaultMessage();
        }
        return "提交内容不符合要求，请检查必填项和格式。";
    }
}
