package com.wujia.pet.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            IOException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            MultipartException.class,
            MaxUploadSizeExceededException.class,
            DataIntegrityViolationException.class,
            AccessDeniedException.class
    })
    public String handleReadableError(
            Exception exception,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", readableMessage(exception));
        return "redirect:" + safeReturnPath(request);
    }

    private String readableMessage(Exception exception) {
        if (exception instanceof MaxUploadSizeExceededException || exception instanceof MultipartException) {
            return "上传文件过大或格式不正确，请压缩图片后重新上传。";
        }
        if (exception instanceof MissingServletRequestParameterException) {
            return "提交内容不完整，请补充必填信息后再试。";
        }
        if (exception instanceof MethodArgumentTypeMismatchException) {
            return "提交的数据格式不正确，请检查日期、评分、坐标等内容。";
        }
        if (exception instanceof DataIntegrityViolationException) {
            return "保存失败，可能存在必填项缺失或数据长度过长，请检查后再提交。";
        }
        if (exception instanceof AccessDeniedException) {
            return "没有权限执行这个操作。";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "操作没有完成，请检查输入内容后再试。";
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private String safeReturnPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer == null || referer.isBlank()) {
            return "/places";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getPath();
            if (path == null || path.isBlank() || path.equals("/error")) {
                return "/places";
            }
            String query = uri.getRawQuery();
            return query == null || query.isBlank() ? path : path + "?" + query;
        } catch (IllegalArgumentException ignored) {
            return "/places";
        }
    }
}
