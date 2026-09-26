package com.xgh.app.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xgh.app.R
import com.xgh.app.data.ApiClient
import com.xgh.app.data.InspectionCorrectionRequest
import com.xgh.app.data.InspectionDetailResponse
import com.xgh.app.databinding.ActivityCorrectionBinding
import com.xgh.app.util.Ui
import kotlinx.coroutines.launch

/**
 * 人工核对修正 AI 结果：对齐后端 POST /dorm/inspections/:id/correct。
 * 校验与网页端一致：reason 必填 ≤500 字、severity 四档、points 0-30、
 * 无变化拦截、已 converted 的记录后端会拒绝。
 */
class CorrectionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INSPECTION_ID = "inspection_id"
        const val RESULT_CORRECTED = Activity.RESULT_FIRST_USER
        private val SEVERITY_VALUES = listOf("low", "medium", "high", "critical")
    }

    private lateinit var binding: ActivityCorrectionBinding
    private var inspectionId: Long = 0

    // 服务器当前基线（用于只提交变化字段）
    private var baseCategory: String = ""
    private var baseSeverity: String = ""
    private var basePoints: Int = 0
    private var baseSummary: String = ""
    private var baseAdvice: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCorrectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inspectionId = intent.getLongExtra(EXTRA_INSPECTION_ID, 0)
        if (inspectionId <= 0) {
            Ui.toast(this, "缺少上报记录 ID")
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmit.setOnClickListener { submit() }
        loadDetail()
    }

    private fun currentSeverity(): String = when {
        binding.btnSevLow.isChecked -> "low"
        binding.btnSevMedium.isChecked -> "medium"
        binding.btnSevHigh.isChecked -> "high"
        binding.btnSevCritical.isChecked -> "critical"
        else -> baseSeverity
    }

    private fun selectSeverity(value: String?) {
        when (value) {
            "low" -> binding.tgSeverity.check(R.id.btnSevLow)
            "medium" -> binding.tgSeverity.check(R.id.btnSevMedium)
            "high" -> binding.tgSeverity.check(R.id.btnSevHigh)
            "critical" -> binding.tgSeverity.check(R.id.btnSevCritical)
        }
    }

    private fun loadDetail() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            binding.progress.visibility = View.GONE
            val detail = Ui.request(this@CorrectionActivity) {
                ApiClient.get().service.inspectionDetail(inspectionId)
            } as InspectionDetailResponse?
            if (detail?.record == null) {
                Ui.toast(this@CorrectionActivity, "记录加载失败")
                finish()
                return@launch
            }
            val rec = detail.record
            val st = detail.structured
            binding.tvRecordInfo.text = buildString {
                append("${rec.building ?: ""} ${rec.room_number ?: ""} · ")
                append(rec.created_at?.take(16)?.replace('T', ' ') ?: "")
                rec.note_text?.takeIf { it.isNotBlank() }?.let { append("\n$it") }
            }
            baseCategory = st?.category ?: rec.category ?: ""
            baseSeverity = st?.severity ?: rec.severity ?: ""
            basePoints = st?.deduct_points ?: rec.deduct_points ?: 0
            baseSummary = st?.summary ?: ""
            baseAdvice = st?.action_advice ?: ""
            binding.etCategory.setText(baseCategory)
            binding.etPoints.setText(if (basePoints > 0) basePoints.toString() else "")
            binding.etSummary.setText(baseSummary)
            binding.etAdvice.setText(baseAdvice)
            selectSeverity(baseSeverity)
        }
    }

    private fun submit() {
        val reason = binding.etReason.text?.toString()?.trim().orEmpty()
        if (reason.isEmpty()) {
            Ui.toast(this, getString(R.string.correct_need_reason))
            return
        }

        val category = binding.etCategory.text?.toString()?.trim().orEmpty()
        val points = binding.etPoints.text?.toString()?.trim()?.toIntOrNull() ?: 0
        if (points < 0 || points > 30) {
            Ui.toast(this, getString(R.string.correct_points_range))
            return
        }
        val severity = currentSeverity()
        if (severity.isBlank()) {
            Ui.toast(this, "请选择严重程度")
            return
        }
        val summary = binding.etSummary.text?.toString()?.trim().orEmpty()
        val advice = binding.etAdvice.text?.toString()?.trim().orEmpty()

        // 与网页端一致：只提交有变化的字段，无变化直接拦截
        val req = InspectionCorrectionRequest(
            vision_analysis = null,
            category = category.takeIf { it != baseCategory },
            severity = severity.takeIf { it != baseSeverity },
            deduct_points = points.takeIf { it != basePoints },
            summary = summary.takeIf { it != baseSummary },
            action_advice = advice.takeIf { it != baseAdvice },
            reason = reason
        )
        val changed = listOfNotNull(
            req.category, req.severity, req.deduct_points, req.summary, req.action_advice
        )
        if (changed.isEmpty()) {
            Ui.toast(this, getString(R.string.correct_no_change))
            return
        }

        binding.progress.visibility = View.VISIBLE
        binding.btnSubmit.isEnabled = false
        lifecycleScope.launch {
            try {
                ApiClient.get().service.correctInspection(inspectionId, req)
                Ui.toast(this@CorrectionActivity, getString(R.string.correct_ok))
                setResult(RESULT_CORRECTED)
                finish()
            } catch (e: retrofit2.HttpException) {
                val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                Ui.toast(this@CorrectionActivity, Ui.extractError(body) ?: getString(R.string.net_error))
            } catch (e: Exception) {
                Ui.toast(this@CorrectionActivity, getString(R.string.net_error))
            } finally {
                binding.progress.visibility = View.GONE
                binding.btnSubmit.isEnabled = true
            }
        }
    }
}
