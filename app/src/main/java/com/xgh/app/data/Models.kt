package com.xgh.app.data

import com.google.gson.annotations.SerializedName

/** 通用：GORM 空切片序列化为 null，全部按可空处理 */

data class User(
    val id: Long,
    val username: String?,
    val real_name: String?,
    val phone: String?,
    val role: String?,
    val building: String?,
    val floor: String?,
    val class_name: String?,
    val department: String?,
    val position: String?,
    val total_score: Int?,
    val status: String?
)

data class LoginResponse(
    val message: String?,
    val token: String,
    val user: User
)

data class ProfileResponse(
    val token: String? = null,
    val user: User? = null
)

data class TaskCard(
    val id: Long,
    val title: String?,
    val period: String?,
    val building: String?,
    val duty_members: String?,
    val is_work_time: Boolean?,
    val priority: String?,
    val action_type: String?,
    val prompt_text: String?
)

data class TodayTasksResponse(
    val is_in_work_time: Boolean?,
    val current_time: String?,
    val cards: List<TaskCard>?,
    val building: String?
)

data class SlotRule(
    val id: Long,
    val name: String?,
    val period_type: String?,
    val start_time: String?,
    val end_time: String?,
    val is_enabled: Boolean?
)

data class SlotStatus(
    val config: SlotRule?,
    val is_active_now: Boolean?,
    val remaining_minutes: Int?,
    val today_submitted_count: Int?,
    val has_submitted: Boolean?
)

data class SlotNoticeResponse(
    val server_time: String?,
    val active_slot: SlotStatus?,
    val next_slot: SlotStatus?,
    val building: String?,
    val is_weekend: Boolean?
)

data class InspectionPhoto(
    val id: Long,
    val manager_name: String?,
    val building: String?,
    val room_number: String?,
    val image_url: String?,
    val photo_type: String?,
    val report_kind: String?,
    val note_text: String?,
    val ai_status: String?,
    val vision_ai_output: String?,
    val structured_json: String?,
    val category: String?,
    val deduct_points: Int?,
    val severity: String?,
    val status: String?,
    val review_note: String?,
    val created_at: String?
)

data class InspectionsResponse(
    val total: Int?,
    val items: List<InspectionPhoto>?
)

data class StructuredResult(
    val category: String?,
    val severity: String?,
    val deduct_points: Int?,
    val summary: String?,
    val action_advice: String?
)

data class UploadPhotoResponse(
    val message: String?,
    val record: InspectionPhoto?,
    val ai_status: String?,
    val vision_analysis: String?,
    val structured_result: StructuredResult?,
    val subject_total: Int?,
    val subject_matched: Int?,
    val subject_unmatched: Int?
)

data class InspectionSubject(
    val id: Long,
    val raw_name: String?,
    val match_status: String?,
    val match_note: String?
)

data class InspectionDetailResponse(
    val record: InspectionPhoto?,
    val subjects: List<InspectionSubject>?,
    val structured: StructuredResult?
)

/** 修正请求：仅传变化字段；severity ∈ low/medium/high/critical；deduct_points 0-30 */
data class InspectionCorrectionRequest(
    val vision_analysis: String? = null,
    val category: String? = null,
    val severity: String? = null,
    val deduct_points: Int? = null,
    val summary: String? = null,
    val action_advice: String? = null,
    val reason: String
)

data class ApiError(
    @SerializedName("error") val error: String?
)
