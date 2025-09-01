package club.gifters.giftersclub.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.PresignRequest
import club.gifters.giftersclub.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

class ProfileSettingsFragment : Fragment(R.layout.fragment_profile_settings) {
    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""
    private lateinit var ivAvatar: ImageView
    private lateinit var etUsername: TextInputEditText
    private lateinit var etDisplayName: TextInputEditText
    private lateinit var etBio: TextInputEditText
    private lateinit var btnSaveProfile: MaterialButton
    private val REQUEST_PICK_IMAGE = 3001

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return
        ivAvatar = view.findViewById(R.id.ivAvatar)
        etUsername = view.findViewById(R.id.etUsername)
        etDisplayName = view.findViewById(R.id.etDisplayName)
        etBio = view.findViewById(R.id.etBio)
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile)

        lifecycleScope.launch {
            val profile = try {
                profileApi.getProfileByUserId(
                    select = "*",
                    userIdFilter = "eq.$userId"
                ).firstOrNull()
            } catch (e: HttpException) {
                if (e.code() == 400) null else throw e
            } catch (_: Exception) {
                null
            }
            profile?.let {
                etUsername.setText(it.username)
                etDisplayName.setText(it.name.orEmpty())
                etBio.setText(it.bio.orEmpty())
                if (it.image.isNotBlank()) {
                    ivAvatar.load(it.image) {
                        transformations(CircleCropTransformation())
                        placeholder(android.R.color.darker_gray)
                    }
                }
            }
        }

        ivAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        }

        btnSaveProfile.setOnClickListener {
            val updates = mutableMapOf<String, Any>()
            updates["username"] = etUsername.text.toString().trim()
            updates["name"] = etDisplayName.text.toString().trim()
            updates["bio"] = etBio.text.toString().trim()
        lifecycleScope.launch {
                try {
                    val updated =
                        profileApi.updateProfile(userIdFilter = "eq.$userId", updates = updates)
                    if (updated.isNotEmpty()) {
                        Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT)
                            .show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK -> data?.data?.let { uri ->
                val srcFile =
                    File(requireContext().cacheDir, "CROP_SRC_${System.currentTimeMillis()}.jpg")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(srcFile).use { output -> input.copyTo(output) }
                }
                val destFile =
                    File(requireContext().cacheDir, "CROP_DST_${System.currentTimeMillis()}.jpg")
                UCrop.of(Uri.fromFile(srcFile), Uri.fromFile(destFile))
                    .withAspectRatio(1f, 1f)
                    .start(requireContext(), this@ProfileSettingsFragment, UCrop.REQUEST_CROP)
            }

            requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK && data != null -> {
                UCrop.getOutput(data)?.let { uri ->
                    ivAvatar.load(uri) {
                        transformations(CircleCropTransformation())
                        placeholder(android.R.color.darker_gray)
                    }
                    uploadImage(uri)
                }
            }
        }
    }

    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            val ctx = context ?: return@launch
            try {
                val type = ctx.contentResolver.getType(uri).orEmpty()
                val ext = type.substringAfterLast('/', "")
                val filename = "profile-$userId.$ext"
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch

                val presignResp = RetrofitClient.functionsApi.uploadMedia(
                    PresignRequest(
                        fileName = filename,
                        fileType = type,
                        bucket = "profile",
                        overwrite = true
                    )
                )
                if (!presignResp.isSuccessful) throw HttpException(presignResp)
                val presignData = presignResp.body()!!

                val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                val putReq = Request.Builder().url(presignData.uploadUrl).put(body).build()
                val putResp = withContext(Dispatchers.IO) {
                    RetrofitClient.awsClient.newCall(putReq).execute()
                }
                if (!putResp.isSuccessful) throw Exception("Upload failed: ${putResp.code}")

                if (!isAdded) return@launch
                updateProfile(mapOf("image" to presignData.publicUrl))
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, "Failed to upload image", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun updateProfile(updates: Map<String, Any>) {
        lifecycleScope.launch {
            try {
                val updated = profileApi.updateProfile(userIdFilter = "eq.$userId", updates = updates)
                if (!isAdded) return@launch
                if (updated.isNotEmpty()) {
                    val newImage = updated[0].image
                    ivAvatar.load(newImage) {
                        transformations(CircleCropTransformation())
                        placeholder(android.R.color.darker_gray)
                    }
                    context?.let { Toast.makeText(it, "Profile updated", Toast.LENGTH_SHORT).show() }
                }
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, "Failed to update profile", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
