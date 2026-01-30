package com.checkout.payment.gateway.controller;

import com.checkout.payment.gateway.model.ApiPaymentRequest;
import com.checkout.payment.gateway.model.ApiPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("api")
public class PaymentGatewayController {

  private final PaymentGatewayService paymentGatewayService;

  public PaymentGatewayController(PaymentGatewayService paymentGatewayService) {
    this.paymentGatewayService = paymentGatewayService;
  }

  /**
   * PUT request is idempotent by default, reduce the chance of double payment
   * @param paymentRequest payment request
   *                       @param id payment identifier
   * @return payment response
   */
  @PutMapping("/payment/{id}")
  public ResponseEntity<ApiPaymentResponse> processPayment(@PathVariable UUID id, @RequestBody ApiPaymentRequest paymentRequest) {
    ApiPaymentResponse response = paymentGatewayService.processPayment(id, paymentRequest);
    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @GetMapping("/payment/{id}")
  public ResponseEntity<ApiPaymentResponse> getPostPaymentEventById(@PathVariable UUID id) {
    return new ResponseEntity<>(paymentGatewayService.getPaymentById(id), HttpStatus.OK);
  }
}
