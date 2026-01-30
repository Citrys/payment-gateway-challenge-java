package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class BankPaymentResponse {
  private boolean authorized;

  @JsonProperty("authorization_code")
  private String authorizationCode;
}
