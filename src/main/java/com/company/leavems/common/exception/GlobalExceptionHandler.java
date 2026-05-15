package com.company.leavems.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException ex, HttpServletRequest request) {
        return handleException(ex, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public Object handleForbiddenException(ForbiddenException ex, HttpServletRequest request) {
        return handleException(ex, HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(NotFoundException.class)
    public Object handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        return handleException(ex, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = "Validation failed: " + ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " - " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return handleException(new RuntimeException(message), HttpStatus.BAD_REQUEST, request);
    }

    private Object handleException(RuntimeException ex, HttpStatus status, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_HTML_VALUE)) {
            // Web request
            ModelAndView mav = new ModelAndView("error");
            mav.addObject("status", status.value());
            mav.addObject("error", status.getReasonPhrase());
            mav.addObject("message", ex.getMessage());
            mav.addObject("timestamp", LocalDateTime.now());
            return mav;
        } else {
            // API request
            Map<String, Object> body = new HashMap<>();
            body.put("status", status.value());
            body.put("error", status.getReasonPhrase());
            body.put("message", ex.getMessage());
            body.put("timestamp", LocalDateTime.now());
            return new ResponseEntity<>(body, status);
        }
    }
}