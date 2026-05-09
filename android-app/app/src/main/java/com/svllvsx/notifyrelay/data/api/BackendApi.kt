package com.svllvsx.notifyrelay.data.api

import com.svllvsx.notifyrelay.data.api.dto.DeviceStatusResponse
import com.svllvsx.notifyrelay.data.api.dto.EventsBatchRequest
import com.svllvsx.notifyrelay.data.api.dto.EventsBatchResponse
import com.svllvsx.notifyrelay.data.api.dto.HealthResponse
import com.svllvsx.notifyrelay.data.api.dto.RegisterDeviceRequest
import com.svllvsx.notifyrelay.data.api.dto.RegisterDeviceResponse
import com.svllvsx.notifyrelay.data.api.dto.TestMessageRequest
import com.svllvsx.notifyrelay.data.api.dto.TestMessageResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

interface BackendApi {
    @GET("health") suspend fun health(): Response<HealthResponse>
    @POST("v1/devices/register") suspend fun registerDevice(@Body body: RegisterDeviceRequest): Response<RegisterDeviceResponse>
    @POST("v1/events/batch") suspend fun sendEvents(@Body body: EventsBatchRequest): Response<EventsBatchResponse>
    @POST("v1/devices/me/test") suspend fun sendTestMessage(@Body body: TestMessageRequest): Response<TestMessageResponse>
    @GET("v1/devices/me") suspend fun getDeviceStatus(): Response<DeviceStatusResponse>
    @DELETE("v1/devices/me") suspend fun revokeDevice(): Response<TestMessageResponse>
}
