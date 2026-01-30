package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.ApiPaymentRequest;
import com.checkout.payment.gateway.model.ApiPaymentResponse;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.service.bank.BankCommunicationException;
import com.checkout.payment.gateway.service.bank.BankService;
import com.checkout.payment.gateway.service.validator.PaymentValidator;
import com.checkout.payment.gateway.service.validator.ValidationResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for idempotency behavior of payment processing
 * Ensures that retrying the same payment ID returns the cached result
 * without calling the bank again
 */
@ExtendWith(MockitoExtension.class)
class PaymentIdempotencyTest {

  @Mock private PaymentValidator paymentValidator;
  @Mock private BankService bankService;
  @Mock private PaymentRepository paymentRepository;

  private PaymentGatewayService service;
  private ApiPaymentRequest validRequest;
  private UUID paymentId;

  @BeforeEach
  void setUp() {
    service = new PaymentGatewayService(paymentValidator, bankService, paymentRepository);

    paymentId = UUID.randomUUID();

    validRequest = new ApiPaymentRequest();
    validRequest.setCardNumber("4111111111111111");
    validRequest.setExpiryMonth(12);
    validRequest.setExpiryYear(2026);
    validRequest.setCurrency("USD");
    validRequest.setAmount(1000);
    validRequest.setCvv(123);
  }

  @Test
  void shouldProcessPaymentFirstTime() throws BankCommunicationException {
    // Given: Payment doesn't exist yet
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
    when(paymentValidator.validate(validRequest)).thenReturn(ValidationResult.success());

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("AUTH123");
    when(bankService.authorizePayment(validRequest)).thenReturn(bankResponse);

    // When: Process payment
    ApiPaymentResponse response = service.processPayment(paymentId, validRequest);

    // Then: Payment processed successfully
    assertNotNull(response);
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());

    // Verify bank was called once
    verify(bankService, times(1)).authorizePayment(validRequest);
    verify(paymentRepository, times(1)).save(any(ApiPaymentResponse.class));
  }

  @Test
  void shouldReturnCachedResultOnRetry() throws BankCommunicationException {
    // Given: Payment already exists (processed before)
    ApiPaymentResponse existingPayment = new ApiPaymentResponse();
    existingPayment.setId(paymentId);
    existingPayment.setStatus(PaymentStatus.AUTHORIZED);
    existingPayment.setCardNumberLastFour("1111");
    existingPayment.setExpiryMonth(12);
    existingPayment.setExpiryYear(2026);
    existingPayment.setCurrency("USD");
    existingPayment.setAmount(1000);

    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existingPayment));

    // When: Retry same payment ID
    ApiPaymentResponse response = service.processPayment(paymentId, validRequest);

    // Then: Returns cached result
    assertNotNull(response);
    assertSame(existingPayment, response);
    assertEquals(PaymentStatus.AUTHORIZED, response.getStatus());

    // Verify: Bank was NOT called (idempotent)
    verify(bankService, times(0)).authorizePayment(any());
    // Verify: Validator was NOT called (idempotent)
    verify(paymentValidator, times(0)).validate(any());
    // Verify: Repository save was NOT called again
    verify(paymentRepository, times(0)).save(any());
  }

  @Test
  void shouldReturnCachedDeclinedPaymentOnRetry() throws BankCommunicationException {
    // Given: Payment already exists with DECLINED status
    ApiPaymentResponse existingPayment = new ApiPaymentResponse();
    existingPayment.setId(paymentId);
    existingPayment.setStatus(PaymentStatus.DECLINED);
    existingPayment.setCardNumberLastFour("1112");
    existingPayment.setAmount(1000);

    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existingPayment));

    // When: Retry same payment ID
    ApiPaymentResponse response = service.processPayment(paymentId, validRequest);

    // Then: Returns cached DECLINED result
    assertNotNull(response);
    assertEquals(PaymentStatus.DECLINED, response.getStatus());

    // Verify: No additional processing
    verify(bankService, times(0)).authorizePayment(any());
  }

  @Test
  void shouldReturnCachedRejectedPaymentOnRetry() throws BankCommunicationException {
    // Given: Payment already exists with REJECTED status
    ApiPaymentResponse existingPayment = new ApiPaymentResponse();
    existingPayment.setId(paymentId);
    existingPayment.setStatus(PaymentStatus.REJECTED);

    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(existingPayment));

    // When: Retry same payment ID
    ApiPaymentResponse response = service.processPayment(paymentId, validRequest);

    // Then: Returns cached REJECTED result
    assertNotNull(response);
    assertEquals(PaymentStatus.REJECTED, response.getStatus());

    // Verify: No validation or bank call
    verify(paymentValidator, times(0)).validate(any());
    verify(bankService, times(0)).authorizePayment(any());
  }

  @Test
  void shouldHandleMultipleRetriesCorrectly() throws BankCommunicationException {
    // Given: First call - payment doesn't exist
    ApiPaymentResponse processedPayment = new ApiPaymentResponse();
    processedPayment.setId(paymentId);
    processedPayment.setStatus(PaymentStatus.AUTHORIZED);
    processedPayment.setAmount(1000);

    when(paymentRepository.findById(paymentId))
        .thenReturn(Optional.empty()) // First call - not found
        .thenReturn(Optional.of(processedPayment)) // Subsequent calls - found
        .thenReturn(Optional.of(processedPayment));

    when(paymentValidator.validate(validRequest)).thenReturn(ValidationResult.success());

    BankPaymentResponse bankResponse = new BankPaymentResponse();
    bankResponse.setAuthorized(true);
    bankResponse.setAuthorizationCode("AUTH123");
    when(bankService.authorizePayment(validRequest)).thenReturn(bankResponse);

    // When: Call multiple times with same ID
    ApiPaymentResponse response1 = service.processPayment(paymentId, validRequest);
    ApiPaymentResponse response2 = service.processPayment(paymentId, validRequest);
    ApiPaymentResponse response3 = service.processPayment(paymentId, validRequest);

    // Then: All return same status
    assertEquals(PaymentStatus.AUTHORIZED, response1.getStatus());
    assertEquals(PaymentStatus.AUTHORIZED, response2.getStatus());
    assertEquals(PaymentStatus.AUTHORIZED, response3.getStatus());

    // Verify: Bank called only ONCE (first time)
    verify(bankService, times(1)).authorizePayment(validRequest);
    // Verify: Repository queried 3 times (once per call)
    verify(paymentRepository, times(3)).findById(paymentId);
  }
}
