package com.parker.hotkey.presentation.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.naver.maps.map.LocationSource
import com.naver.maps.map.util.FusedLocationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class LocationPermissionDelegate @Inject constructor(
    private val fragment: Fragment,
    private val coroutineScope: CoroutineScope,
    private val locationSource: FusedLocationSource
) {
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }

    private var onPermissionGranted: (() -> Unit)? = null
    private var onPermissionDenied: (() -> Unit)? = null
    private var showError: ((String) -> Unit)? = null
    private var isRequestingPermission = false
    private var hasCheckedPermission = false
    private var onFirstLocationUpdate: ((android.location.Location) -> Unit)? = null
    private var isFirstLocationUpdate = true

    private val locationSourceCallback = object : LocationSource.OnLocationChangedListener {
        override fun onLocationChanged(location: android.location.Location?) {
            location?.let { loc ->
                Timber.d("위치 변경: ${loc.latitude}, ${loc.longitude}")
                if (isFirstLocationUpdate) {
                    isFirstLocationUpdate = false
                    onFirstLocationUpdate?.invoke(loc)
                }
            }
        }
    }

    private fun activateLocationSource() {
        try {
            if (!locationSource.isActivated) {
                Timber.d("위치 소스 활성화 시도")
                // 권한 재확인
                val fineLocationPermission = fragment.requireContext().checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                val coarseLocationPermission = fragment.requireContext().checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                
                val hasPermission = fineLocationPermission == PackageManager.PERMISSION_GRANTED ||
                        coarseLocationPermission == PackageManager.PERMISSION_GRANTED

                Timber.d("위치 권한 상태 - FINE: ${fineLocationPermission == PackageManager.PERMISSION_GRANTED}, COARSE: ${coarseLocationPermission == PackageManager.PERMISSION_GRANTED}")

                if (!hasPermission) {
                    Timber.w("위치 권한이 없어 위치 소스를 활성화할 수 없습니다.")
                    hasCheckedPermission = false
                    requestLocationPermissions()
                    return
                }

                try {
                    isFirstLocationUpdate = true
                    locationSource.activate(locationSourceCallback)
                    kotlinx.coroutines.runBlocking {
                        kotlinx.coroutines.delay(100) // 위치 소스 활성화를 위한 짧은 대기
                    }
                    
                    if (locationSource.isActivated) {
                        Timber.d("위치 소스 활성화 성공")
                        onPermissionGranted?.invoke()
                    } else {
                        Timber.w("위치 소스 활성화 실패")
                        showError?.invoke("위치 서비스를 시작할 수 없습니다.")
                        hasCheckedPermission = false
                    }
                } catch (e: SecurityException) {
                    Timber.e(e, "위치 소스 활성화 중 권한 오류 발생")
                    showError?.invoke("위치 권한이 필요합니다.")
                    hasCheckedPermission = false
                    requestLocationPermissions()
                }
            } else {
                Timber.d("위치 소스가 이미 활성화되어 있음")
                onPermissionGranted?.invoke()
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 소스 활성화 중 오류 발생")
            showError?.invoke("위치 서비스를 시작할 수 없습니다.")
            hasCheckedPermission = false
        }
    }

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isRequestingPermission = false
        val allGranted = permissions.entries.all { it.value }
        
        Timber.d("위치 권한 요청 결과: ${if (allGranted) "허용" else "거부"}")
        
        if (allGranted) {
            Timber.d("모든 위치 권한이 허용됨")
            hasCheckedPermission = true
            // 권한이 허용되면 약간의 지연 후 위치 소스 활성화 시도
            coroutineScope.launch {
                kotlinx.coroutines.delay(500) // 0.5초 대기
                activateLocationSource()
            }
        } else {
            handlePermissionDenial()
        }
    }

    private fun handlePermissionDenial() {
        val shouldShowRationale = shouldShowRationale()
        
        if (shouldShowRationale) {
            Timber.d("권한 거부됨 (다시 묻지 않음 미선택)")
            showError?.invoke("위치 권한이 필요합니다. 권한을 허용해주세요.")
            coroutineScope.launch {
                kotlinx.coroutines.delay(1500)
                if (!isRequestingPermission) {
                    Timber.d("권한 재요청 시작")
                    requestLocationPermissions()
                }
            }
        } else {
            Timber.d("권한 영구 거부됨 (다시 묻지 않음 선택)")
            showError?.invoke("위치 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            showSettingsDialog()
        }
        onPermissionDenied?.invoke()
    }

    fun setCallbacks(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
        showError: (String) -> Unit,
        onFirstLocationUpdate: (android.location.Location) -> Unit
    ) {
        this.onPermissionGranted = onPermissionGranted
        this.onPermissionDenied = onPermissionDenied
        this.showError = showError
        this.onFirstLocationUpdate = onFirstLocationUpdate
    }

    fun checkLocationPermission() {
        if (isRequestingPermission) {
            Timber.d("이미 권한 요청이 진행 중입니다.")
            return
        }

        if (hasCheckedPermission && locationSource.isActivated) {
            Timber.d("이미 권한이 확인되었고 위치 소스가 활성화되어 있습니다.")
            onPermissionGranted?.invoke()
            return
        }

        val hasLocationPermission = hasLocationPermissions()
        Timber.d("현재 권한 상태: $hasLocationPermission")

        when {
            hasLocationPermission -> {
                Timber.d("위치 권한이 이미 허용되어 있습니다.")
                hasCheckedPermission = true
                // 권한이 있으면 약간의 지연 후 위치 소스 활성화 시도
                coroutineScope.launch {
                    kotlinx.coroutines.delay(500) // 0.5초 대기
                    activateLocationSource()
                }
            }
            shouldShowRationale() -> {
                Timber.d("권한 설명 필요 - 다시 요청")
                showError?.invoke("위치 권한이 필요합니다. 권한을 허용해주세요.")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(1500)
                    if (!isRequestingPermission) {
                        requestLocationPermissions()
                    }
                }
            }
            else -> {
                Timber.d("최초 권한 요청")
                requestLocationPermissions()
            }
        }
    }

    private fun hasLocationPermissions(): Boolean {
        val context = fragment.requireContext()
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun shouldShowRationale(): Boolean {
        return fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
               fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun requestLocationPermissions() {
        if (isRequestingPermission) {
            Timber.d("이미 권한 요청 중입니다.")
            return
        }
        
        isRequestingPermission = true
        Timber.d("위치 권한 요청 시작")
        
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun showSettingsDialog() {
        coroutineScope.launch {
            fragment.context?.let { context ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                fragment.startActivity(intent)
            }
        }
    }

    fun hasLocationPermission(): Boolean {
        val hasPermissions = hasLocationPermissions()
        val isSourceActive = locationSource.isActivated
        Timber.d("위치 권한 상태 확인 - 권한: $hasPermissions, 위치 소스 활성화: $isSourceActive")
        return hasPermissions && isSourceActive
    }

    fun getLocationSource(): FusedLocationSource {
        return locationSource
    }

    fun resetPermissionCheck() {
        hasCheckedPermission = false
        isRequestingPermission = false
        isFirstLocationUpdate = true
    }
} 