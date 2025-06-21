package com.example.confessionapp.ui.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityGooglePayDonationBinding
import com.example.confessionapp.ui.viewmodels.GooglePayDonationViewModel
import com.example.confessionapp.utils.PaymentsUtil
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.*
import org.json.JSONException
import org.json.JSONObject
import java.text.DecimalFormat

class GooglePayDonationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGooglePayDonationBinding
    private val viewModel: GooglePayDonationViewModel by viewModels()
    private lateinit var paymentsClient: PaymentsClient

    private lateinit var googlePayButtonLauncher: ActivityResultLauncher<IntentSenderRequest>

    companion object {
        private const val TAG = "GooglePayDonation"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGooglePayDonationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        paymentsClient = createPaymentsClient()
        possiblyShowGooglePayButton()
        setupObservers()

        googlePayButtonLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let { intent ->
                    PaymentData.getFromIntent(intent)?.let { paymentData ->
                        handlePaymentSuccess(paymentData)
                    }
                }
            } else if (result.resultCode == Activity.RESULT_CANCELED) {
                // Payment cancelled by user
                Toast.makeText(this, "Payment cancelled", Toast.LENGTH_SHORT).show()
            } else if (result.resultCode == AutoResolveHelper.RESULT_ERROR) {
                 AutoResolveHelper.getStatusFromIntent(result.data)?.let { status ->
                    handleError(status.statusCode, status.statusMessage)
                }
            }
        }
    }

    private fun createPaymentsClient(): PaymentsClient {
        val walletOptions = Wallet.WalletOptions.Builder()
            .setEnvironment(PaymentsUtil.PAYMENTS_ENVIRONMENT)
            .build()
        return Wallet.getPaymentsClient(this, walletOptions)
    }

    private fun possiblyShowGooglePayButton() {
        val isReadyToPayJson = PaymentsUtil.isReadyToPayRequest() ?: return
        val request = IsReadyToPayRequest.fromJson(isReadyToPayJson.toString())

        val task = paymentsClient.isReadyToPay(request)
        task.addOnCompleteListener { completedTask ->
            try {
                if (completedTask.isSuccessful) {
                    setGooglePayAvailable(completedTask.getResult(ApiException::class.java))
                } else {
                    setGooglePayAvailable(false)
                    // Log error or handle gracefully
                    Log.w(TAG, "isReadyToPay failed: ${completedTask.exception?.message}")
                }
            } catch (exception: ApiException) {
                setGooglePayAvailable(false)
                Log.w(TAG, "isReadyToPay failed with exception: $exception")
            }
        }
    }

    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            binding.tvGooglePayStatus.visibility = View.GONE
            val googlePayButton = createGooglePayButton()
            binding.googlePayButtonContainer.addView(googlePayButton)
            googlePayButton.setOnClickListener { requestPayment() }
        } else {
            binding.tvGooglePayStatus.text = "Google Pay is not available on this device or not configured."
            binding.tvGooglePayStatus.visibility = View.VISIBLE
            binding.googlePayButtonContainer.visibility = View.GONE
        }
    }

    private fun createGooglePayButton(): View {
        // This is a helper function from Google's examples,
        // you might need to implement or use a library for the actual button view.
        // For now, using a standard button, but ideally use Google's branded button.
        val button = com.google.android.material.button.MaterialButton(this)
        button.text = "Donate with Google Pay" // Replace with Google Pay branding if possible
        button.setOnClickListener { requestPayment() }
        return button
        // Or, use:
        // return WalletFragmentOptions.newBuilder()
        //         .setEnvironment(WalletConstants.ENVIRONMENT_TEST)
        //         .setMode(WalletFragmentMode.BUTTON_BLACK) // Or other modes
        //         .build();
        // And add it as a fragment. Simpler way for now is a direct button.
    }


    private fun requestPayment() {
        val amountText = binding.etDonationAmount.text.toString()
        val amount = amountText.toDoubleOrNull()

        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid donation amount.", Toast.LENGTH_SHORT).show()
            return
        }
        // Format amount to cents string for PaymentsUtil (e.g., "5.00" -> "500" if util expects cents, or keep as "5.00" if util handles it)
        // PaymentsUtil.getPaymentDataRequest expects price in "totalPrice" format like "10.00"
        val priceString = DecimalFormat("0.00").format(amount)

        val paymentDataRequestJson = PaymentsUtil.getPaymentDataRequest(priceString)
        if (paymentDataRequestJson == null) {
            Log.e(TAG, "Can't fetch payment data request")
            Toast.makeText(this, "Error preparing payment request.", Toast.LENGTH_SHORT).show()
            return
        }
        val request = PaymentDataRequest.fromJson(paymentDataRequestJson.toString())

        AutoResolveHelper.resolveTask(
            paymentsClient.loadPaymentData(request), this, LOAD_PAYMENT_DATA_REQUEST_CODE
        )
    }

    // This companion object value is used with AutoResolveHelper if not using ActivityResultLauncher
     private val LOAD_PAYMENT_DATA_REQUEST_CODE = 991

    // This onActivityResult is kept if AutoResolveHelper is used without ActivityResultLauncher
    // However, with ActivityResultLauncher (googlePayButtonLauncher), this is not directly called for that launcher.
    // @Deprecated("Deprecated in Java")
    // override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    //     super.onActivityResult(requestCode, resultCode, data)
    //     when (requestCode) {
    //         LOAD_PAYMENT_DATA_REQUEST_CODE -> {
    //             when (resultCode) {
    //                 Activity.RESULT_OK ->
    //                     data?.let { intent ->
    //                         PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
    //                     }
    //                 Activity.RESULT_CANCELED -> {
    //                     // The user cancelled the payment attempt
    //                 }
    //                 AutoResolveHelper.RESULT_ERROR -> {
    //                     AutoResolveHelper.getStatusFromIntent(data)?.let {
    //                         handleError(it.statusCode, it.statusMessage)
    //                     }
    //                 }
    //             }
    //         }
    //     }
    // }


    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson() ?: return
        Log.d(TAG, "Payment Success: $paymentInformation")

        try {
            val paymentMethodData = JSONObject(paymentInformation).getJSONObject("paymentMethodData")
            val token = paymentMethodData.getJSONObject("tokenizationData").getString("token")
            val orderId = JSONObject(paymentInformation).optString("transactionInfo", paymentMethodData.getJSONObject("info").optString("cardDetails") ) // Example: could be Google's transaction ID or from gateway

            val amount = binding.etDonationAmount.text.toString().toDoubleOrNull() ?: 0.0
            val currency = "USD" // Assuming USD from PaymentsUtil

            viewModel.recordDonation(amount, currency, token, orderId) // Pass relevant data
            Toast.makeText(this, "Thank you for your donation!", Toast.LENGTH_LONG).show()

        } catch (e: JSONException) {
            Log.e(TAG, "Error parsing payment data: $e")
            Toast.makeText(this, "Error processing payment.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleError(statusCode: Int, message: String?) {
        Log.e(TAG, "Payment Error: $statusCode, Message: $message")
        val userMessage = when (statusCode) {
            CommonStatusCodes.API_NOT_CONNECTED -> "API not connected. Please try again."
            CommonStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection."
            CommonStatusCodes.INTERNAL_ERROR -> "Internal error. Please try again later."
            WalletConstants.ERROR_CODE_SPENDING_LIMIT_EXCEEDED -> "Spending limit exceeded."
            WalletConstants.ERROR_CODE_INVALID_PARAMETERS -> "Invalid parameters in payment request."
            // Add more specific WalletConstants error codes if needed
            else -> "An unexpected error occurred: ${message ?: "Unknown"}"
        }
        Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarDonation.visibility = if (isLoading) View.VISIBLE else View.GONE
            // Disable donation amount EditText and Google Pay button while loading
             binding.etDonationAmount.isEnabled = !isLoading
             val googlePayButton = binding.googlePayButtonContainer.getChildAt(0)
             googlePayButton?.isEnabled = !isLoading
        }

        viewModel.donationRecordResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Donation recorded successfully!", Toast.LENGTH_LONG).show()
                finish() // Close activity after successful donation
            } else {
                Toast.makeText(this, "Failed to record donation. Please try again or contact support.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
