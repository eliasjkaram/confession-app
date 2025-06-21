package com.example.confessionapp.ui.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.confessionapp.databinding.ActivityPriestVerificationBinding
import com.example.confessionapp.ui.viewmodels.PriestVerificationViewModel
import com.example.confessionapp.R

class PriestVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPriestVerificationBinding
    private val viewModel: PriestVerificationViewModel by viewModels()
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String? = null

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedFileUri = uri
                // Get file name
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    selectedFileName = cursor.getString(nameIndex)
                    binding.tvSelectedFileName.text = "Selected file: $selectedFileName"
                    binding.tvSelectedFileName.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPriestVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinner()
        setupClickListeners()
        setupObservers()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, viewModel.documentTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDocumentType.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnSelectDocument.setOnClickListener {
            openFilePicker()
        }

        binding.btnUploadDocument.setOnClickListener {
            uploadDocument()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBarVerificationUpload.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSelectDocument.isEnabled = !isLoading
            binding.btnUploadDocument.isEnabled = !isLoading
            binding.spinnerDocumentType.isEnabled = !isLoading
        }

        viewModel.uploadResult.observe(this) { result ->
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            if (result.isSuccess) {
                // Optionally navigate back or update UI to show pending status
                if (result.isVerificationPending) {
                    // Disable further uploads or change UI
                    binding.btnUploadDocument.isEnabled = false
                    binding.btnSelectDocument.isEnabled = false
                    binding.tvVerificationInfo.text = "Verification pending. You will be notified once reviewed."
                }
                 // finish() // or navigate to dashboard which should reflect new status
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Or specific MIME types like "application/pdf", "image/*"
            // Optionally, specify MIME types for better filtering
            // val mimeTypes = arrayOf("application/pdf", "image/jpeg", "image/png")
            // putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        filePickerLauncher.launch(intent)
    }

    private fun uploadDocument() {
        val docType = binding.spinnerDocumentType.selectedItem.toString()
        if (selectedFileUri != null && selectedFileName != null) {
            viewModel.uploadVerificationDocument(selectedFileUri!!, docType, selectedFileName!!)
        } else {
            Toast.makeText(this, "Please select a document first.", Toast.LENGTH_SHORT).show()
        }
    }
}
