package com.checkout.payment.gateway.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import lombok.Data;

@Data
public class ApiPaymentRequest implements Serializable {
  @JsonProperty(required = true, value = "card_number")
  private String cardNumber;
  @JsonProperty(required = true, value = "expiry_month")
  private int expiryMonth;
  @JsonProperty(required = true, value = "expiry_year")
  private int expiryYear;
  @JsonProperty(required = true, value = "currency")
  private String currency;
  @JsonProperty(required = true, value = "amount")
  private int amount;
  @JsonProperty(required = true, value = "cvv")
  private String cvv;

  @JsonProperty("card_number_last_four")
  public String getCardNumberLastFour() {
    if (cardNumber != null && cardNumber.length() >= 4) {
      return cardNumber.substring(cardNumber.length() - 4);
    }
    return null;
  }

  @JsonProperty("expiry_date")
  public String getExpiryDate() {
    return String.format("%d/%d", expiryMonth, expiryYear);
  }

  @Override
  public String toString() {
    return "ApiPaymentRequest{" +
        "cardNumber=****" + getCardNumberLastFour() +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", cvv=***" +
        '}';
  }
}
