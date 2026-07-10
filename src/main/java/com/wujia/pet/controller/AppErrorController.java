package com.wujia.pet.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AppErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, RedirectAttributes redirectAttributes) {
        HttpStatus status = resolveStatus(request);
        String message = messageOf(status);
        if (GlobalExceptionHandler.wantsJson(request)) {
            return ResponseEntity.status(status).body(GlobalExceptionHandler.errorBody(message));
        }
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:" + GlobalExceptionHandler.safeReturnPath(request);
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer code) {
            return HttpStatus.resolve(code) == null ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.valueOf(code);
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String messageOf(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "页面不存在或地址已失效。";
        }
        if (status == HttpStatus.FORBIDDEN || status == HttpStatus.UNAUTHORIZED) {
            return "没有权限访问这个页面，请登录后再试。";
        }
        if (status.is4xxClientError()) {
            return "请求内容不正确，请刷新页面后重试。";
        }
        return "系统开小差了，请稍后再试。";
    }
}
