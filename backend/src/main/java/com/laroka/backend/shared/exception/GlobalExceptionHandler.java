package com.laroka.backend.shared.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.laroka.backend.auth.exception.InvalidCredentialsException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Map<String, Object>> handleAccessDenied(
			AccessDeniedException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.FORBIDDEN, "Acceso denegado", null, request);
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
			InvalidCredentialsException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleEntityNotFound(
			EntityNotFoundException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + ex.getMessage(), null, request);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, Object>> handleBusinessException(
			BusinessException ex, HttpServletRequest request) {
		log.warn("BusinessException en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null, request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidationException(
			MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(fe ->
			errors.putIfAbsent(fe.getField(), fe.getDefaultMessage())
		);
		return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", errors, request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Map<String, Object>> handleTypeMismatch(
			MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		String type = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
		String message = "Parámetro inválido: " + ex.getName() + " debe ser " + type;
		return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleNotReadable(
			HttpMessageNotReadableException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "Request body inválido o malformado", null, request);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNoHandler(
			NoHandlerFoundException ex, HttpServletRequest request) {
		String message = "Endpoint no encontrado: " + request.getMethod() + " " + request.getRequestURI();
		return buildResponse(HttpStatus.NOT_FOUND, message, null, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(
			Exception ex, HttpServletRequest request) {
		log.error("Error interno del servidor en {} {}", request.getMethod(), request.getRequestURI(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null, request);
	}

	private ResponseEntity<Map<String, Object>> buildResponse(
			HttpStatus status, String message, Map<String, String> errors, HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		if (errors != null && !errors.isEmpty()) {
			body.put("errors", errors);
		}
		body.put("path", request.getRequestURI());
		body.put("timestamp", Instant.now().toString());
		return new ResponseEntity<>(body, status);
	}
}
