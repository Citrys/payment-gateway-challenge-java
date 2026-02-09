package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.requests.ApiPaymentRequest;
import com.checkout.payment.gateway.responces.ApiPaymentResponse;
import com.checkout.payment.gateway.responces.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.service.bank.BankCommunicationException;
import com.checkout.payment.gateway.service.bank.BankService;
import com.checkout.payment.gateway.service.validator.PaymentValidator;
import com.checkout.payment.gateway.service.validator.ValidationResult;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Payment Gateway Service - orchestrates payment processing
 * - SR: Only orchestrates, delegates specifics to other services
 * - OC: Can extend with new validators/services without modification
 * - Uses specific interfaces for each dependency
 * - DI: Depends on abstractions (interfaces), not concretions
 */
@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentValidator paymentValidator;
  private final BankService bankService;
  private final PaymentRepository paymentRepository;

  public PaymentGatewayService(
      PaymentValidator paymentValidator,
      BankService bankService,
      PaymentRepository paymentRepository) {
    this.paymentValidator = paymentValidator;
    this.bankService = bankService;
    this.paymentRepository = paymentRepository;
  }

  /**
   * Retrieves a payment by ID
   * @param id the payment ID
   * @return the payment response if found, otherwise null (controller should handle 404 Not Found)
   */
  public ApiPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Retrieving payment with ID: {}", id);
    return paymentRepository
        .findById(id)
        .map(this::mapToApiResponse)
        .orElse(null);
  }

  /**
   * Controller should return 204 No Content if payment exists
   * @param paymentId the payment ID to check
   * @return Optional containing the existing payment response if found
   */
  public Optional<ApiPaymentResponse> checkIdempotency(UUID paymentId) {
    return paymentRepository.findById(paymentId)
        .map(this::mapToApiResponse);
  }

  /**
   * Controller should return 400 Bad Request if validation fails
   * @param paymentRequest the payment request to validate
   * @return ValidationResult containing validation status and errors
   */
  public ValidationResult validateRequest(ApiPaymentRequest paymentRequest) {
    return paymentValidator.validate(paymentRequest);
  }

  /**
   * Creates and saves a rejected payment when validation fails
   * Controller should return 400 Bad Request with this response
   * @param paymentId the payment ID
   * @param paymentRequest the payment request
   * @param validationResult the validation result containing errors
   * @return the rejected payment response
   */
  public ApiPaymentResponse createRejectedPayment(UUID paymentId, ApiPaymentRequest paymentRequest, ValidationResult validationResult) {
    Payment payment = buildPaymentDomain(paymentId, paymentRequest);
    payment.setStatus(PaymentStatus.REJECTED);
    payment.setRejectionReason(String.join("; ", validationResult.getErrors()));

    // This is a synchronous operation. In real world, consider making it async when real database comms implemented.
    paymentRepository.save(payment);
    return mapToApiResponse(payment);
  }

  /**
   * Controller should return 201 Created with payment details
   * @param paymentId the payment ID
   * @param paymentRequest the payment request
   * @return CompletableFuture with the payment response with authorization status
   */
  public CompletableFuture<ApiPaymentResponse> authorizeAndCreatePayment(UUID paymentId, ApiPaymentRequest paymentRequest) {
    Payment payment = buildPaymentDomain(paymentId, paymentRequest);
    return authorizeWithBank(paymentRequest, paymentId)
        .thenApply(bankResponse -> {
          payment.setStatus(bankResponse != null && bankResponse.isAuthorized()
              ? PaymentStatus.AUTHORIZED
              : PaymentStatus.DECLINED);
          payment.setAuthorizationCode(bankResponse != null ? bankResponse.getAuthorizationCode() : null);
          paymentRepository.save(payment);
          return mapToApiResponse(payment);
        });
  }

  private Payment buildPaymentDomain(UUID paymentId, ApiPaymentRequest paymentRequest) {
    return Payment.builder()
        .id(paymentId)
        .cardNumberLastFour(paymentRequest.getCardNumberLastFour())
        .expiryMonth(paymentRequest.getExpiryMonth())
        .expiryYear(paymentRequest.getExpiryYear())
        .currency(paymentRequest.getCurrency())
        .amount(paymentRequest.getAmount())
        .build();
  }

  private CompletableFuture<BankPaymentResponse> authorizeWithBank(ApiPaymentRequest paymentRequest, UUID paymentId) {
    try {
      return bankService.authorizePayment(paymentId, paymentRequest)
          .thenApply(bankResponse -> {
            if (bankResponse.isAuthorized()) {
              LOG.info("Payment authorized, ID: {}, auth_code: {}", paymentId, paymentRequest.getCardNumberLastFour());
            } else {
              LOG.warn("Payment declined by bank, ID: {}, auth_code: {}", paymentId, paymentRequest.getCardNumberLastFour());
            }
            return bankResponse;
          })
          .exceptionally(e -> {
            LOG.error("Bank authorization failed, ID: {}, error: {}", paymentId, e.getMessage());
            return null;
          });
    } catch (BankCommunicationException e) {
      LOG.error("Bank authorization failed, ID: {}, error: {}", paymentId, e.getMessage());
      return CompletableFuture.completedFuture(null);
    }
  }

  private ApiPaymentResponse mapToApiResponse(Payment payment) {
    ApiPaymentResponse response = new ApiPaymentResponse();
    response.setId(payment.getId());
    response.setStatus(payment.getStatus());
    response.setCardNumberLastFour(payment.getCardNumberLastFour());
    response.setExpiryMonth(payment.getExpiryMonth());
    response.setExpiryYear(payment.getExpiryYear());
    response.setCurrency(payment.getCurrency());
    response.setAmount(payment.getAmount());
    response.setRejectionReason(payment.getRejectionReason());
    return response;
  }
}
