package com.bapegg.routinlog.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.FitnessLocal
import com.google.android.gms.fitness.LocalRecordingClient
import com.google.android.gms.fitness.data.LocalDataType
import com.google.android.gms.fitness.data.LocalField
import com.google.android.gms.fitness.request.LocalDataReadRequest
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Accountless on-device Recording API, not the retired Google Fit cloud API.
 * https://developer.android.com/health-and-fitness/recording-api */
class AndroidPhoneSteps(context:Context):PhoneSteps {
    private val app=context.applicationContext
    private val client by lazy { FitnessLocal.getLocalRecordingClient(app) }
    override fun availability():StepAvailability {
        val sensor=app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if(sensor?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)==null&&sensor?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)==null)return StepAvailability.UNSUPPORTED
        if(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app,LocalRecordingClient.LOCAL_RECORDING_CLIENT_MIN_VERSION_CODE)!=ConnectionResult.SUCCESS)return StepAvailability.SERVICES_REQUIRED
        if(Build.VERSION.SDK_INT>=29&&ContextCompat.checkSelfPermission(app,Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)return StepAvailability.PERMISSION_REQUIRED
        return StepAvailability.READY
    }
    override suspend fun start() {
        // Check again at the platform boundary; permission may change after the UI check.
        if(Build.VERSION.SDK_INT>=29&&ContextCompat.checkSelfPermission(app,Manifest.permission.ACTIVITY_RECOGNITION)!=PackageManager.PERMISSION_GRANTED)
            throw IllegalStateException("신체 활동 권한을 허용해주세요.")
        try { providerCall { client.subscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await() } }
        catch(_:SecurityException){throw IllegalStateException("신체 활동 권한이 바뀌었어요. 권한을 다시 확인해주세요.")}
    }
    override suspend fun stop() { providerCall { client.unsubscribe(LocalDataType.TYPE_STEP_COUNT_DELTA).await() } }
    override suspend fun read(from:Instant,through:Instant):Long? = providerCall {
        val request=LocalDataReadRequest.Builder().aggregate(LocalDataType.TYPE_STEP_COUNT_DELTA)
            .bucketByTime(1,TimeUnit.DAYS).setTimeRange(from.toEpochMilli(),through.toEpochMilli(),TimeUnit.MILLISECONDS).build()
        val points=client.readData(request).await().buckets.flatMap { it.dataSets }.flatMap { it.dataPoints }
        if(points.isEmpty())null else points.sumOf { it.getValue(LocalField.FIELD_STEPS).asInt().toLong() }
    }
    private suspend fun <T> providerCall(block:suspend()->T):T = try { withTimeout(20_000){block()} }
        catch(_:TimeoutCancellationException){throw IllegalStateException("휴대폰의 걸음 수집 기능 응답이 늦어요. 잠시 후 다시 시도해주세요.")}
}

/** Stores only subscription ownership, never step counts or auth tokens. App backup is disabled. */
class AndroidStepBindingStore(context:Context):StepBindingStore {
    private val preferences=context.applicationContext.getSharedPreferences("phone-step-binding",Context.MODE_PRIVATE)
    override fun read():StepBinding? {
        val owner=preferences.getString("owner",null) ?: return null
        return preferences.getString("connection",null)?.let { StepBinding(owner,it) }
    }
    override fun write(binding:StepBinding?) {
        val editor=preferences.edit().clear()
        if(binding!=null)editor.putString("owner",binding.owner).putString("connection",binding.connectionId)
        check(editor.commit()){ "걸음 연결 상태를 기기에 저장하지 못했어요." }
    }
}
