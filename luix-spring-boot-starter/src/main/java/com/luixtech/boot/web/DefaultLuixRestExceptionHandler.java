package com.luixtech.boot.web;

import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Default {@link LuixRestExceptionHandler} registered by {@link LuixWebAutoConfiguration}
 * when no custom {@link LuixRestExceptionHandler} bean is present.
 */
@RestControllerAdvice
class DefaultLuixRestExceptionHandler extends LuixRestExceptionHandler {
}
