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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.laroka.backend.auth.exception.InvalidCredentialsException;
import com.laroka.backend.auth.exception.RefreshTokenInvalidException;
import com.laroka.backend.media.exception.InvalidFileException;
import com.laroka.backend.media.exception.StorageException;
import com.laroka.backend.notification.email.EmailDeliveryException;
import com.laroka.backend.order.exception.ProductUnavailableException;

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

	@ExceptionHandler(RefreshTokenInvalidException.class)
	public ResponseEntity<Map<String, Object>> handleRefreshTokenInvalid(
			RefreshTokenInvalidException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, request);
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<Map<String, Object>> handleEntityNotFound(
			EntityNotFoundException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.NOT_FOUND, "Recurso no encontrado: " + ex.getMessage(), null, request);
	}

	// Más específico que BusinessException: además del mensaje, expone productId
	// como campo estructurado del body para que el cliente (US-15-CF-05) sepa qué
	// producto remover del carrito sin parsear el string del mensaje.
	@ExceptionHandler(ProductUnavailableException.class)
	public ResponseEntity<Map<String, Object>> handleProductUnavailable(
			ProductUnavailableException ex, HttpServletRequest request) {
		log.warn("ProductUnavailableException en {} {}: productId={} {}",
				request.getMethod(), request.getRequestURI(), ex.getProductId(), ex.getMessage());
		Map<String, Object> extra = new LinkedHashMap<>();
		extra.put("productId", ex.getProductId());
		return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null, extra, request);
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Map<String, Object>> handleBusinessException(
			BusinessException ex, HttpServletRequest request) {
		log.warn("BusinessException en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null, request);
	}

	@ExceptionHandler(InvalidFileException.class)
	public ResponseEntity<Map<String, Object>> handleInvalidFile(
			InvalidFileException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleMaxUploadSize(
			MaxUploadSizeExceededException ex, HttpServletRequest request) {
		return buildResponse(HttpStatus.BAD_REQUEST, "El archivo excede el tamaño máximo permitido", null, request);
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<Map<String, Object>> handleMissingPart(
			MissingServletRequestPartException ex, HttpServletRequest request) {
		String message = "Falta la parte requerida: " + ex.getRequestPartName();
		return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
	}

	@ExceptionHandler(StorageException.class)
	public ResponseEntity<Map<String, Object>> handleStorage(
			StorageException ex, HttpServletRequest request) {
		log.error("Fallo de almacenamiento en {} {}", request.getMethod(), request.getRequestURI(), ex);
		// El mensaje de StorageException ya es específico y seguro (no expone
		// detalles del proveedor): distingue fallo de storage vs. lectura del archivo.
		return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), null, request);
	}

	// US-17-07: el proveedor de email falló. 502 con mensaje genérico — el detalle
	// real (proveedor, causa) ya quedó logueado por EmailService, no se expone acá.
	@ExceptionHandler(EmailDeliveryException.class)
	public ResponseEntity<Map<String, Object>> handleEmailDelivery(
			EmailDeliveryException ex, HttpServletRequest request) {
		log.error("Fallo de envío de email en {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.BAD_GATEWAY, "No se pudo enviar el email. Intentá de nuevo.", null, request);
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

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<Map<String, Object>> handleMissingParam(
			MissingServletRequestParameterException ex, HttpServletRequest request) {
		String message = "Falta el parámetro requerido: " + ex.getParameterName();
		return buildResponse(HttpStatus.BAD_REQUEST, message, null, request);
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

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatus(
			ResponseStatusException ex, HttpServletRequest request) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		String message = ex.getReason() != null ? ex.getReason() : ex.getMessage();
		return buildResponse(status, message, null, request);
	}

	@ExceptionHandler({ AsyncRequestTimeoutException.class, AsyncRequestNotUsableException.class })
	public void handleSseLifecycle() {
		// ciclo de vida normal del SSE — conexión expirada o no disponible, nada que escribir
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(
			Exception ex, HttpServletRequest request) {
		log.error("Error interno del servidor en {} {}", request.getMethod(), request.getRequestURI(), ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null, request);
	}

	private ResponseEntity<Map<String, Object>> buildResponse(
			HttpStatus status, String message, Map<String, String> errors, HttpServletRequest request) {
		return buildResponse(status, message, errors, null, request);
	}

	private ResponseEntity<Map<String, Object>> buildResponse(
			HttpStatus status, String message, Map<String, String> errors,
			Map<String, Object> extra, HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", status.getReasonPhrase());
		body.put("message", message);
		if (extra != null) {
			extra.forEach(body::put);
		}
		if (errors != null && !errors.isEmpty()) {
			body.put("errors", errors);
		}
		body.put("path", request.getRequestURI());
		body.put("timestamp", Instant.now().toString());
		return new ResponseEntity<>(body, status);
	}
}
