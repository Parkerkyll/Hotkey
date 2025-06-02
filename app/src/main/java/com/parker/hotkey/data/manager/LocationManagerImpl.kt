package com.parker.hotkey.data.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.common.api.ResolvableApiException
import com.parker.hotkey.domain.model.Location as DomainLocation
import com.parker.hotkey.domain.repository.LocationManager as AppLocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppLocationManager {

    // 초기화 시점 분리 - 지연 초기화 사용
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        Timber.d("FusedLocationProviderClient 초기화")
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    // 시스템 위치 서비스 참조 추가
    private val systemLocationManager: android.location.LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    }

    override fun hasLocationPermission(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasPermission = hasFineLocation || hasCoarseLocation
        
        if (!hasPermission) {
            Timber.w("위치 권한이 없음: FINE_LOCATION=$hasFineLocation, COARSE_LOCATION=$hasCoarseLocation")
        }
        
        return hasPermission
    }
    
    // 위치 서비스 활성화 여부 체크 메소드 추가
    private fun isLocationEnabled(): Boolean {
        val isGpsEnabled = systemLocationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = systemLocationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        
        val isEnabled = isGpsEnabled || isNetworkEnabled
        
        if (!isEnabled) {
            Timber.w("위치 서비스 비활성화: GPS=$isGpsEnabled, NETWORK=$isNetworkEnabled")
        } else {
            Timber.d("위치 서비스 활성화: GPS=$isGpsEnabled, NETWORK=$isNetworkEnabled")
        }
        
        return isEnabled
    }

    /**
     * 위치 설정 페이지 열기 위한 Intent 반환
     */
    fun getLocationSettingsIntent(): Intent {
        // 안드로이드 버전에 따라 다른 설정 페이지로 이동
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // 안드로이드 10 이상에서는 위치 설정 페이지로 이동
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        } else {
            // 그 이하 버전에서는 일반 설정 페이지로 이동
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    override fun getLocationUpdates(): Flow<DomainLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            Timber.e("위치 권한이 없습니다.")
            throw SecurityException("위치 권한이 필요합니다.")
        }
        
        // 위치 서비스 활성화 여부 체크 추가
        if (!isLocationEnabled()) {
            Timber.e("위치 서비스가 비활성화되어 있습니다.")
            throw IllegalStateException("위치 서비스가 비활성화되어 있습니다.")
        }

        Timber.d("위치 업데이트 시작 (GPS 우선, 네트워크 백업)")
        
        // 위치 요청 설정 최적화 (API 레벨에 따른 분기)
        val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 이상
            LocationRequest.Builder(1000L)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY) // GPS 우선
                .setMinUpdateDistanceMeters(0f)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateDelayMillis(1000L)
                .build()
        } else {
            // Android 11 이하 - 호환성 방식으로 구현 (deprecated API 제거)
            LocationRequest.Builder(1000L).apply {
                setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                setMinUpdateIntervalMillis(500L)
                setMaxUpdateDelayMillis(1000L)
                setMinUpdateDistanceMeters(0f)
                setWaitForAccurateLocation(true)
            }.build()
        }
        
        // 위치 설정 요청 객체 생성 및 설정
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true) // 위치 설정 다이얼로그 항상 표시

        // 위치 정보 소스 추적용 변수
        var lastLocationSource = ""

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    try {
                        val provider = location.provider ?: "unknown"
                        val accuracy = location.accuracy
                        val domainLocation = location.toDomain()
                        
                        // 위치 정보 소스와 정확도 로깅
                        Timber.d("새로운 위치 업데이트: 소스=$provider, 정확도=${accuracy}m, lat=${domainLocation.latitude}, lng=${domainLocation.longitude}")
                        
                        // 위치 소스가 변경되었을 때만 로그 기록
                        if (lastLocationSource != provider) {
                            Timber.i("위치 소스 변경: $lastLocationSource -> $provider")
                            lastLocationSource = provider
                        }
                        
                        // 위치 데이터 전송
                        trySend(domainLocation)
                    } catch (e: Exception) {
                        Timber.e(e, "위치 데이터 전송 실패")
                    }
                }
            }
        }

        try {
            // 위치 설정 체크 - API 29 이상에서 중요
            LocationServices.getSettingsClient(context)
                .checkLocationSettings(builder.build())
                .addOnSuccessListener {
                    Timber.d("위치 설정 확인 성공")
                    
                    try {
                        Timber.d("위치 업데이트 요청 시작")
                        // 위치 업데이트 요청
                        fusedLocationClient.requestLocationUpdates(
                            locationRequest,
                            locationCallback,
                            Looper.getMainLooper()
                        ).addOnSuccessListener {
                            Timber.d("위치 업데이트 요청 성공")
                        }.addOnFailureListener { e ->
                            Timber.e(e, "위치 업데이트 요청 실패")
                        }
                    } catch (e: SecurityException) {
                        Timber.e(e, "위치 업데이트 요청 중 권한 오류")
                    } catch (e: Exception) {
                        Timber.e(e, "위치 업데이트 요청 중 오류")
                    }
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "위치 설정 확인 실패")
                    if (e is ResolvableApiException) {
                        // 위치 설정 변경 필요 - 액티비티에서 처리 필요
                        Timber.w("위치 설정 변경 필요")
                    }
                }
                
            // 안드로이드 10에서 위치 서비스 추가 확인
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                Timber.d("안드로이드 10 기기 감지: 추가 위치 서비스 검사 실행")
                
                // 이미 마지막 위치가 있다면 즉시 보내기 (흐름 시작 가속화)
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            Timber.d("마지막 위치 즉시 전송 (안드로이드 10): lat=${location.latitude}, lng=${location.longitude}")
                            trySend(location.toDomain())
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "마지막 위치 확인 중 오류 (안드로이드 10)")
                }
            }
        } catch (e: SecurityException) {
            Timber.e(e, "위치 권한이 없습니다.")
            throw SecurityException("위치 권한이 필요합니다.", e)
        } catch (e: Exception) {
            Timber.e(e, "위치 업데이트 초기화 중 오류 발생")
            throw e
        }

        awaitClose {
            Timber.d("위치 업데이트 중단")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun Location.toDomain(): DomainLocation {
        return DomainLocation(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = time,
            provider = provider ?: "unknown" // 위치 제공자 정보 추가
        )
    }
} 