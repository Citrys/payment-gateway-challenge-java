package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.requests.ApiPaymentRequest;
import com.checkout.payment.gateway.responces.ApiPaymentResponse;
import com.checkout.payment.gateway.responces.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.service.bank.BankCommunicationException;
import com.checkout.payment.gateway.service.bank.BankService;
import com.checkout.payment.gateway.service.util.DataMaskingUtil;
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
   * @return the payment response
   * @throws EventProcessingException if payment not found
   */
  public ApiPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Retrieving payment with ID: {}", id);
    return paymentRepository
        .findById(id)
        .map(this::mapToApiResponse)
        .orElse(null);
  }

  /**
   * Processes a payment request with a generated UUID
   * @param paymentRequest the payment request
   * @return the payment response with status
   */
  public ApiPaymentResponse processPayment(ApiPaymentRequest paymentRequest) {
    return processPayment(UUID.randomUUID(), paymentRequest);
  }

  /**
   * Processes a payment request with idempotency guarantee using the 3 subfunctions
   * If a payment with the same ID already exists, returns the existing result
   * without calling the bank again (idempotent operation)
   * NOTE: This method blocks on the async bank call. For non-blocking behavior,
   * use authorizeAndCreatePayment() directly which returns CompletableFuture.
   * @param paymentId the unique payment identifier (idempotency key)
   * @param paymentRequest the payment request
   * @return the payment response with status
   */
  public ApiPaymentResponse processPayment(UUID paymentId, ApiPaymentRequest paymentRequest) {
    LOG.info("Processing payment request ID: {}", paymentId);

    // Step 1: IDEMPOTENCY CHECK - Return existing payment if already processed
    Optional<ApiPaymentResponse> existingPayment = checkIdempotency(paymentId);
    if (existingPayment.isPresent()) {
      LOG.info("Payment ID {} already exists, returning cached result (idempotent)", paymentId);
      return existingPayment.get();
    }

    // Step 2: VALIDATION - Validate request and reject if invalid
    ValidationResult validationResult = validateRequest(paymentRequest);
    if (!validationResult.isValid()) {
      LOG.warn("Payment validation failed, ID: {}, errors: {}", paymentId, validationResult.getErrors());
      return createRejectedPayment(paymentId, paymentRequest, validationResult);
    }

    // Step 3: AUTHORIZATION & PROCESSING - Authorize with bank and create payment
    // Block on async call for backward compatibility
    return authorizeAndCreatePayment(paymentId, paymentRequest).join();
  }

  /**
   * Subfunction 1: Check if payment already exists (Idempotency)
   * Controller should return 204 No Content if payment exists
   * @param paymentId the payment ID to check
   * @return Optional containing the existing payment response if found
   */
  public Optional<ApiPaymentResponse> checkIdempotency(UUID paymentId) {
    LOG.debug("Checking idempotency for payment ID: {}", paymentId);
    return paymentRepository.findById(paymentId)
        .map(this::mapToApiResponse);
  }

  /**
   * Subfunction 2: Validate payment request
   * Controller should return 400 Bad Request if validation fails
   * @param paymentRequest the payment request to validate
   * @return ValidationResult containing validation status and errors
   */
  public ValidationResult validateRequest(ApiPaymentRequest paymentRequest) {
    LOG.debug("Validating payment request");
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
    paymentRepository.save(payment);
    LOG.debug("Rejected payment persisted, ID: {}, reason: {}", paymentId, payment.getRejectionReason());
    return mapToApiResponse(payment);
  }

  /**
   * Subfunction 3: Authorize payment with bank and create payment record (async)
   * Controller should return 200 OK with payment details
   * @param paymentId the payment ID
   * @param paymentRequest the payment request
   * @return CompletableFuture with the payment response with authorization status
   */
  public CompletableFuture<ApiPaymentResponse> authorizeAndCreatePayment(UUID paymentId, ApiPaymentRequest paymentRequest) {
    LOG.debug("Authorizing and creating payment, ID: {}", paymentId);

    Payment payment = buildPaymentDomain(paymentId, paymentRequest);

    return authorizeWithBank(paymentRequest, paymentId)
        .thenApply(bankResponse -> {
          payment.setStatus(bankResponse != null && bankResponse.isAuthorized()
              ? PaymentStatus.AUTHORIZED
              : PaymentStatus.DECLINED);
          payment.setAuthorizationCode(bankResponse != null ? bankResponse.getAuthorizationCode() : null);

          paymentRepository.save(payment);
          LOG.debug("Payment persisted, ID: {}, status: {}, auth_code: {}",
              payment.getId(), payment.getStatus(),
              payment.getAuthorizationCode() != null ? "***" : "null");

          return mapToApiResponse(payment);
        });
  }

  /**
   * Builds a Payment domain object from the request
   * Contains only last 4 digits of card number
   */
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

  /**
   * Calls the bank service to authorize the payment asynchronously
   * Passes payment ID in headers for bank idempotency check
   * Returns CompletableFuture with bank response or null if communication failed
   */
  private CompletableFuture<BankPaymentResponse> authorizeWithBank(ApiPaymentRequest paymentRequest, UUID paymentId) {
    try {
      return bankService.authorizePayment(paymentId, paymentRequest)
          .thenApply(bankResponse -> {
            String maskedAuthCode = DataMaskingUtil.maskAuthorizationCode(
                bankResponse.getAuthorizationCode());
            if (bankResponse.isAuthorized()) {
              LOG.info("Payment authorized, ID: {}, auth_code: {}", paymentId, maskedAuthCode);
            } else {
              LOG.warn("Payment declined by bank, ID: {}, auth_code: {}", paymentId, maskedAuthCode);
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

  /**
   * Maps the Payment domain object to ApiPaymentResponse DTO
   */
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
