package com.checkout.payment.gateway.service.util;

/**
 * Utility class for masking sensitive data
 */
public final class DataMaskingUtil {

  private DataMaskingUtil() {}

  /**
   * Masks authorization code showing only last 4 characters
   * @param authCode the authorization code to mask
   * @return masked authorization code (e.g., ****6789)
   */
  public static String maskAuthorizationCode(String authCode) {
    if (authCode == null || authCode.length() <= 4) {
      return "****";
    }
    return "****" + authCode.substring(authCode.length() - 4);
  }

  /**
   * Masks card number showing only last 4 digits
   * @param cardNumber the card number to mask
   * @return masked card number (e.g., ************1234)
   */
  public static String maskCardNumber(String cardNumber) {
    if (cardNumber == null || cardNumber.length() < 4) {
      return "****";
    }
    return "*".repeat(cardNumber.length() - 4) + cardNumber.substring(cardNumber.length() - 4);
  }
}
