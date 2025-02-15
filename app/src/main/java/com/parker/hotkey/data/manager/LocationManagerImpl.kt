package com.parker.hotkey.data.manager

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.parker.hotkey.domain.model.Location as DomainLocation
import com.parker.hotkey.domain.repository.LocationManager
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
) : LocationManager {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun getLocationUpdates(): Flow<DomainLocation> = callbackFlow {
        if (!hasLocationPermission()) {
            Timber.e("위치 권한이 없습니다.")
            throw SecurityException("위치 권한이 필요합니다.")
        }

        Timber.d("위치 업데이트 시작")
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateDistanceMeters(0f)  // 거리 제한 없음
            .setMaxUpdateDelayMillis(1000L)  // 최대 업데이트 지연 시간
            .setMinUpdateIntervalMillis(500L) // 최소 업데이트 간격
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    try {
                        val domainLocation = location.toDomain()
                        Timber.d("새로운 위치 업데이트: lat=${domainLocation.latitude}, lng=${domainLocation.longitude}")
                        trySend(domainLocation)
                    } catch (e: Exception) {
                        Timber.e(e, "위치 데이터 전송 실패")
                    }
                }
            }
        }

        try {
            Timber.d("위치 업데이트 요청 시작")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnSuccessListener {
                Timber.d("위치 업데이트 요청 성공")
            }.addOnFailureListener { e ->
                Timber.e(e, "위치 업데이트 요청 실패")
                close(e)
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
            timestamp = time
        )
    }
} 