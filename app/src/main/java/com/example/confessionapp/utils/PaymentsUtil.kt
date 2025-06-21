package com.example.confessionapp.utils

import com.google.android.gms.wallet.WalletConstants
import org.json.JSONArray
import org.json.JSONObject

object PaymentsUtil {

    // Replace with your actual merchant name and ID from Google Pay Business Console
    private const val MERCHANT_NAME = "Your Merchant Name" // Placeholder
    private const val MERCHANT_ID = "YOUR_MERCHANT_ID"     // Placeholder

    // Replace with your gateway and gateway merchant ID (e.g., from Stripe)
    private const val PAYMENT_GATEWAY_NAME = "stripe" // Example, use your actual gateway
    private const val GATEWAY_MERCHANT_ID = "YOUR_STRIPE_MERCHANT_ID" // Placeholder for Stripe's gatewayMerchantId

    val CENTS = (1000 * 1000).toLong() // Microunits (value * 1_000_000)

    // Supported payment networks
    private val SUPPORTED_NETWORKS = listOf(
        "AMEX", "DISCOVER", "JCB", "MASTERCARD", "VISA"
    )

    // Supported authentication methods for cards
    private val SUPPORTED_METHODS = listOf(
        "PAN_ONLY", "CRYPTOGRAM_3DS"
    )

    // Environment for Google Pay API (TEST or PRODUCTION)
    // As per Setup.md, this should be WalletConstants.ENVIRONMENT_PRODUCTION for release
    // For development, WalletConstants.ENVIRONMENT_TEST is often used.
    const val PAYMENTS_ENVIRONMENT = WalletConstants.ENVIRONMENT_TEST // Or WalletConstants.ENVIRONMENT_PRODUCTION


    /**
     * Creates a basic Google Pay API base request object with properties used in all requests.
     */
    private fun getBaseRequest(): JSONObject {
        return JSONObject().apply {
            put("apiVersion", 2)
            put("apiVersionMinor", 0)
        }
    }

    /**
     * Information about the merchant performing the transaction.
     */
    private fun getMerchantInfo(): JSONObject {
        return JSONObject().apply {
            put("merchantName", MERCHANT_NAME)
            // A merchant ID is required for ENVIRONMENT_PRODUCTION.
            if (PAYMENTS_ENVIRONMENT == WalletConstants.ENVIRONMENT_PRODUCTION) {
                put("merchantId", MERCHANT_ID)
            }
        }
    }

    /**
     * Describes an allowed payment method.
     * This example is for card payments.
     */
    private fun getAllowedPaymentMethods(): JSONArray {
        val cardPaymentMethod = JSONObject().apply {
            put("type", "CARD")
            put("parameters", JSONObject().apply {
                put("allowedAuthMethods", JSONArray(SUPPORTED_METHODS))
                put("allowedCardNetworks", JSONArray(SUPPORTED_NETWORKS))
                // Optionally, require billing address.
                // put("billingAddressRequired", true)
                // put("billingAddressParameters", JSONObject().apply {
                //     put("format", "FULL")
                // })
            })
            put("tokenizationSpecification", gatewayTokenizationSpecification())
        }
        return JSONArray().put(cardPaymentMethod)
    }

    /**
     * Specifies how payment data should be tokenized.
     * This example uses a PAYMENT_GATEWAY tokenization type.
     */
    private fun gatewayTokenizationSpecification(): JSONObject {
        if (PAYMENT_GATEWAY_NAME.isBlank() || GATEWAY_MERCHANT_ID.isBlank()) {
            throw IllegalStateException(
                "Payment gateway name and merchant ID must be set for tokenization. " +
                        "Update PAYMENTS_GATEWAY_NAME and GATEWAY_MERCHANT_ID in PaymentsUtil.kt"
            )
        }
        return JSONObject().apply {
            put("type", "PAYMENT_GATEWAY")
            put("parameters", JSONObject().apply {
                put("gateway", PAYMENT_GATEWAY_NAME)
                put("gatewayMerchantId", GATEWAY_MERCHANT_ID)
            })
        }
    }

    /**
     * Creates a request object for IsReadyToPay.
     */
    fun isReadyToPayRequest(): JSONObject? {
        return try {
            getBaseRequest().apply {
                put("allowedPaymentMethods", getAllowedPaymentMethods())
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Information about the transaction.
     */
    private fun getTransactionInfo(priceCents: String): JSONObject {
        return JSONObject().apply {
            put("totalPrice", priceCents) // Price in string format, e.g., "10.00"
            put("totalPriceStatus", "FINAL")
            put("currencyCode", "USD") // Change as needed
            // put("countryCode", "US") // Optional
        }
    }

    /**
     * Creates a request object for PaymentDataRequest.
     */
    fun getPaymentDataRequest(priceCents: String): JSONObject? {
        if (priceCents.toLongOrNull()?.let { it <= 0 } != false) {
             // throw IllegalArgumentException("Price must be a positive value in cents.")
            return null // Or handle error appropriately
        }
        return try {
            getBaseRequest().apply {
                put("allowedPaymentMethods", getAllowedPaymentMethods())
                put("transactionInfo", getTransactionInfo(priceCents))
                put("merchantInfo", getMerchantInfo())

                // Optionally, request email, shipping address, etc.
                // put("emailRequired", true)
            }
        } catch (e: Exception) {
            null
        }
    }
}
