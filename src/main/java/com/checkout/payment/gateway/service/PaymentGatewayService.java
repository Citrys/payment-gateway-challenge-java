package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.ApiPaymentRequest;
import com.checkout.payment.gateway.model.ApiPaymentResponse;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.service.bank.BankCommunicationException;
import com.checkout.payment.gateway.service.bank.BankService;
import com.checkout.payment.gateway.service.util.DataMaskingUtil;
import com.checkout.payment.gateway.service.validator.PaymentValidator;
import com.checkout.payment.gateway.service.validator.ValidationResult;
import java.util.Optional;
import java.util.UUID;
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
        .orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  /**
   * Processes a payment request with idempotency guarantee
   * If a payment with the same ID already exists, returns the existing result
   * without calling the bank again (idempotent operation)
   *
   * @param paymentId the unique payment identifier (idempotency key)
   * @param paymentRequest the payment request
   * @return the payment response with status
   */
  public ApiPaymentResponse processPayment(UUID paymentId, ApiPaymentRequest paymentRequest) {
    LOG.info("Processing payment request ID: {}", paymentId);

    // IDEMPOTENCY CHECK: Return existing payment if already processed
    Optional<ApiPaymentResponse> existingPayment = paymentRepository.findById(paymentId);
    if (existingPayment.isPresent()) {
      LOG.info("Payment ID {} already exists, returning cached result (idempotent)", paymentId);
      return existingPayment.get();
    }

    // First time processing this payment ID - proceed with validation and bank call
    ApiPaymentResponse response = buildPaymentResponse(paymentId, paymentRequest);

    ValidationResult validationResult = paymentValidator.validate(paymentRequest);
    if (!validationResult.isValid()) {
      LOG.warn("Payment validation failed, ID: {}", paymentId);
      return savePaymentWithStatus(response, PaymentStatus.REJECTED);
    }

    PaymentStatus status = authorizeWithBank(paymentRequest, paymentId);
    return savePaymentWithStatus(response, status);
  }

  private ApiPaymentResponse buildPaymentResponse(
      UUID paymentId, ApiPaymentRequest paymentRequest) {
    ApiPaymentResponse response = new ApiPaymentResponse();
    response.setId(paymentId);
    response.setCardNumberLastFour(paymentRequest.getCardNumberLastFour());
    response.setExpiryMonth(paymentRequest.getExpiryMonth());
    response.setExpiryYear(paymentRequest.getExpiryYear());
    response.setCurrency(paymentRequest.getCurrency());
    response.setAmount(paymentRequest.getAmount());
    return response;
  }

  private PaymentStatus authorizeWithBank(ApiPaymentRequest paymentRequest, UUID paymentId) {
    try {
      BankPaymentResponse bankResponse = bankService.authorizePayment(paymentRequest);
      return determinePaymentStatus(bankResponse, paymentId);
    } catch (BankCommunicationException e) {
      LOG.error("Bank authorization failed, ID: {}, error: {}",
          paymentId, e.getClass().getSimpleName());
      return PaymentStatus.DECLINED;
    }
  }

  private PaymentStatus determinePaymentStatus(BankPaymentResponse bankResponse, UUID paymentId) {
    if (bankResponse.isAuthorized()) {
      String maskedAuthCode = DataMaskingUtil.maskAuthorizationCode(
          bankResponse.getAuthorizationCode());
      LOG.info("Payment authorized, ID: {}, auth_code: {}", paymentId, maskedAuthCode);
      return PaymentStatus.AUTHORIZED;
    } else {
      LOG.info("Payment declined by bank, ID: {}", paymentId);
      return PaymentStatus.DECLINED;
    }
  }

  private ApiPaymentResponse savePaymentWithStatus(
      ApiPaymentResponse response, PaymentStatus status) {
    response.setStatus(status);
    paymentRepository.save(response);
    LOG.debug("Payment persisted, ID: {}, status: {}", response.getId(), status);
    return response;
  }
}
