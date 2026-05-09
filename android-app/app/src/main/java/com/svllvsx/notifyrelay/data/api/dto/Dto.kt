package com.svllvsx.notifyrelay.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val ok: Boolean, val version: String)

@Serializable
data class RegisterDeviceRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_name") val deviceName: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class RegisterDeviceResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_token") val deviceToken: String,
    @SerialName("telegram_linked") val telegramLinked: Boolean,
)

@Serializable
data class EventsBatchRequest(val events: List<EventDto>)

@Serializable
data class EventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    val type: String,
    val source: EventSourceDto,
    val content: EventContentDto,
    val timestamp: Long,
)

@Serializable
data class EventSourceDto(
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("app_label") val appLabel: String? = null,
    val sender: String? = null,
)

@Serializable
data class EventContentDto(
    val title: String? = null,
    val text: String? = null,
    @SerialName("big_text") val bigText: String? = null,
    @SerialName("sub_text") val subText: String? = null,
)

@Serializable
data class EventsBatchResponse(
    val accepted: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val rejected: List<RejectedEventDto> = emptyList(),
)

@Serializable
data class RejectedEventDto(
    @SerialName("event_id") val eventId: String,
    val reason: String,
)

@Serializable
data class TestMessageRequest(val message: String)

@Serializable
data class TestMessageResponse(val ok: Boolean)

@Serializable
data class DeviceStatusResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("telegram_linked") val telegramLinked: Boolean,
    val active: Boolean,
)
