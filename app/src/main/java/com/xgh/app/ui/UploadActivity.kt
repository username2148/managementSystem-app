package com.xgh.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import coil.load
import com.xgh.app.R
import com.xgh.app.XghApp
import com.xgh.app.data.ApiClient
import com.xgh.app.data.UploadPhotoResponse
import com.xgh.app.databinding.ActivityUploadBinding
import com.xgh.app.util.Ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 三态上报：实拍（photo）/ 记名纸条（note）/ 纯文本（text）。
 * report_kind != "text" 时后端必要求 image（无图直接 400）。
 */
class UploadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUploadBinding
    private var kind: String = "photo"
    private var photoFile: File? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) onPhotoTaken()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tgKind.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            kind = when (checkedId) {
                R.id.btnKindNote -> "note"
                R.id.btnKindText -> "text"
                else -> "photo"
            }
            binding.photoBox.visibility = if (kind != "text") View.VISIBLE else View.GONE
            binding.tilSubjects.visibility = if (kind == "photo") View.GONE else View.VISIBLE
        }
        binding.tgKind.check(R.id.btnKindPhoto)

        binding.photoBox.setOnClickListener {
            val dir = File(cacheDir, "camera").apply { mkdirs() }
            val file = File(dir, "report_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            pendingFile = file
            takePicture.launch(uri)
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnCorrect.setOnClickListener {
            correctLauncher.launch(
                Intent(this, CorrectionActivity::class.java)
                    .putExtra(CorrectionActivity.EXTRA_INSPECTION_ID, lastRecordId)
            )
        }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    private var pendingFile: File? = null

    private fun onPhotoTaken() {
        photoFile = pendingFile
        binding.ivPhoto.load(photoFile) { crossfade(true) }
        binding.tvPhotoHint.visibility = View.GONE
    }

    private fun submit() {
        val room = binding.etRoom.text?.toString()?.trim().orEmpty()
        if (room.isEmpty()) {
            Ui.toast(this, getString(R.string.err_need_room))
            return
        }
        if (kind != "text" && (photoFile == null || !photoFile!!.exists())) {
            Ui.toast(this, getString(R.string.err_need_image))
            return
        }

        binding.progress.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false

        val app = application as XghApp
        fun resetUi() {
            binding.progress.visibility = View.GONE
            binding.btnSubmit.isEnabled = true
        }
        lifecycleScope.launch {
            // 弱网/并发拥堵下最多重试 2 次（指数退避 1s/3s）
            for (attempt in 1..3) {
                try {
                    val building = app.sessionStore.userBuilding.first() ?: ""
                    // 上传前压缩图片，单张 3-8MB → 约 200KB，避免并发时挤爆上行带宽
                    val part = photoFile
                        ?.takeIf { kind != "text" }
                        ?.let { f ->
                            val payload = com.xgh.app.util.ImageCompress.compressToTemp(this@UploadActivity, f) ?: f
                            MultipartBody.Part.createFormData(
                                "image", payload.name, payload.asRequestBody("image/jpeg".toMediaType())
                            )
                        }
                    val resp = ApiClient.get().service.uploadPhoto(
                        room.toPlain(),
                        binding.etPhotoType.text?.toString()?.trim().orEmpty().ifEmpty { "violation" }.toPlain(),
                        building.toPlain(),
                        kind.toPlain(),
                        binding.etNote.text?.toString()?.trim().orEmpty().toPlain(),
                        binding.etSubjects.text?.toString()?.trim().orEmpty().toPlain(),
                        part
                    ) as UploadPhotoResponse
                    renderResult(resp)
                    Ui.toast(this@UploadActivity, getString(R.string.submit_ok))
                    // 有 AI 结果时停留展示（可进入修正），纯文本/无结果 1 秒后自动返回
                    if (resp.structured_result != null || resp.vision_analysis != null) {
                        binding.btnSubmit.text = "已完成，可核对修正后返回"
                        resetUi()
                    } else {
                        delay(1000)
                        finish()
                    }
                    return@launch
                } catch (e: retrofit2.HttpException) {
                    // 业务错误（400/409 等）重试无意义，直接提示
                    val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                    Ui.toast(this@UploadActivity, Ui.extractError(body) ?: getString(R.string.net_error))
                    resetUi()
                    return@launch
                } catch (e: Exception) {
                    if (attempt < 3) {
                        Ui.toast(this@UploadActivity, "网络拥堵，第 $attempt 次重试…")
                        delay(1000L * attempt * attempt)
                    }
                }
            }
            Ui.toast(this@UploadActivity, getString(R.string.net_error))
            resetUi()
        }
    }

    private var lastRecordId: Long = 0

    private val correctLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == CorrectionActivity.RESULT_CORRECTED) {
                binding.tvAiStatus.append("\n✅ 已提交人工修正，副部长打表时将采用修正后的结果")
            }
        }

    private fun renderResult(resp: UploadPhotoResponse) {
        lastRecordId = resp.record?.id ?: 0L
        val text = when (resp.ai_status) {
            "real" -> getString(R.string.ai_real)
            "failed" -> getString(R.string.ai_failed)
            "unknown" -> getString(R.string.ai_unknown)
            else -> getString(R.string.ai_disabled)
        }
        binding.tvAiStatus.visibility = View.VISIBLE
        binding.tvAiStatus.text = buildString {
            append(text)
            val st = resp.structured_result
            if (st != null) {
                st.summary?.takeIf { it.isNotBlank() }?.let { append("\n识别摘要：$it") }
                val sev = when (st.severity) {
                    "low" -> "轻微"; "medium" -> "中等"; "high" -> "严重"; "critical" -> "重大"
                    else -> null
                }
                val suggest = listOfNotNull(
                    st.category?.takeIf { it.isNotBlank() },
                    sev,
                    (st.deduct_points ?: 0).takeIf { it > 0 }?.let { "建议扣 ${it} 分" }
                ).joinToString(" · ")
                if (suggest.isNotEmpty()) append("\n建议：$suggest")
                st.action_advice?.takeIf { it.isNotBlank() }?.let { append("\n处理建议：$it") }
            }
            val total = resp.subject_total ?: 0
            if (total > 0) {
                append("\n")
                append(getString(R.string.subjects_matched, resp.subject_matched ?: 0, total))
            }
            append("\n（AI 结果仅供参考，请人工核对；副部长打表前可再次修正）")
        }
        binding.btnCorrect.visibility =
            if (resp.record != null) View.VISIBLE else View.GONE
    }

    private fun String.toPlain() = this.toRequestBody("text/plain".toMediaType())
}
