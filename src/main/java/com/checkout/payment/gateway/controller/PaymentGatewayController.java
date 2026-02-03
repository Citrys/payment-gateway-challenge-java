package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.requests.ApiPaymentRequest;
import com.checkout.payment.gateway.responces.ApiPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import com.checkout.payment.gateway.service.validator.ValidationResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  /**
   * Post payment processing endpoint with idempotency support (async)
   * Uses the provided payment ID for idempotent processing
   * Returns:
   * - 204 No Content: Payment already exists (idempotent request)
   * - 400 Bad Request: Validation failed (payment rejected)
   * - 200 OK: Payment processed successfully (authorized or declined)
   * @param paymentRequest payment request
   * @return CompletableFuture with payment response with appropriate status code
   */
  @PostMapping("/payment")
  public CompletableFuture<ResponseEntity<ApiPaymentResponse>> processPaymentWithId(
      @RequestBody ApiPaymentRequest paymentRequest) {

    // Check idempotency - return 204 if payment already exists, this is a fake demo
    // because we are generating a new UUID each time, it should come from client in real world and maybe calculated as hash of the request, so we might need to add timestamp

    UUID id = UUID.randomUUID();
    Optional<ApiPaymentResponse> existingPayment = paymentGatewayService.checkIdempotency(id);
    if (existingPayment.isPresent()) {
      return CompletableFuture.completedFuture(
          new ResponseEntity<>(existingPayment.get(), HttpStatus.NO_CONTENT));
    }

    // Subfunction 2: Validate request - return 400 if validation fails
    ValidationResult validationResult = paymentGatewayService.validateRequest(paymentRequest);
    if (!validationResult.isValid()) {
      ApiPaymentResponse rejectedResponse = paymentGatewayService.createRejectedPayment(id, paymentRequest, validationResult);
      return CompletableFuture.completedFuture(
          new ResponseEntity<>(rejectedResponse, HttpStatus.BAD_REQUEST));
    }

    // Subfunction 3: Authorize and create payment - return 200 (async bank call)
    return paymentGatewayService.authorizeAndCreatePayment(id, paymentRequest)
        .thenApply(response -> new ResponseEntity<>(response, HttpStatus.CREATED));
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<ApiPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    ApiPaymentResponse response = paymentGatewayService.getPaymentById(id);
    if (response == null) {
      throw new com.checkout.payment.gateway.exception.EventProcessingException("Payment not found");
    }
    return new ResponseEntity<>(response, HttpStatus.OK);
  }
}
