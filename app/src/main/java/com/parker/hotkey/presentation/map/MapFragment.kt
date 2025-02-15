package com.parker.hotkey.presentation.map

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.parker.hotkey.R
import com.parker.hotkey.util.GeohashUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MapFragment : Fragment(), OnMapReadyCallback {
    
    companion object {
        const val DEFAULT_ZOOM = 17.2
        const val MARKER_CREATION_ZOOM = 18.0
        const val MIN_ZOOM = 14.0
        const val MAX_ZOOM = 19.0
        const val GEOHASH_RADIUS = 1200.0 // 1.2km를 미터 단위로 geohash6
    }
    
    private var mapView: MapView? = null
    private var naverMap: NaverMap? = null
    private val viewModel: MapViewModel by viewModels()
    private var progressBar: ProgressBar? = null
    private val markers = mutableMapOf<String, Marker>()
    private var lastMarkerCreationTime = 0L
    private val MARKER_CREATION_DELAY = 2000L // 2초
    private var cameraUpdateJob: Job? = null
    private val CAMERA_UPDATE_DELAY = 300L // 300ms
    
    // 위치 추적을 위한 변수들
    private lateinit var locationSource: FusedLocationSource
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    
    // 위치 권한 요청을 위한 launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    enableLocationTracking()
                    moveToCurrentLocation()
                }
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    enableLocationTracking()
                    moveToCurrentLocation()
                }
            }
            else -> {
                showError("위치 권한이 필요합니다.")
            }
        }
    }
    
    private lateinit var infoWindow: InfoWindow
    
    @Inject
    lateinit var locationPermissionDelegate: LocationPermissionDelegate
    
    @Inject
    lateinit var markerUIDelegate: MarkerUIDelegate
    
    @Inject
    lateinit var mapConfigDelegate: MapConfigDelegate
    
    private var currentGeohashCircle: CircleOverlay? = null
    private var currentLocationGeohash: String? = null
    private var currentLocationNeighbors: List<String> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)
        locationPermissionDelegate.setCallbacks(
            onPermissionGranted = {
                viewLifecycleOwner.lifecycleScope.launch {
                    naverMap?.let { map ->
                        mapConfigDelegate.enableLocationTracking()
                        moveToCurrentLocation(true)
                    }
                }
            },
            onPermissionDenied = {
                mapConfigDelegate.disableLocationTracking()
                showError("위치 권한이 필요합니다. 설정에서 권한을 허용해주세요.")
            },
            showError = { message ->
                showError(message)
            },
            onFirstLocationUpdate = { location ->
                Timber.d("첫 위치 업데이트 수신: lat=${location.latitude}, lng=${location.longitude}")
                viewLifecycleOwner.lifecycleScope.launch {
                    naverMap?.let { map ->
                        val latLng = LatLng(location.latitude, location.longitude)
                        val cameraUpdate = CameraUpdate.scrollAndZoomTo(latLng, DEFAULT_ZOOM)
                            .animate(CameraAnimation.Easing)
                        map.moveCamera(cameraUpdate)
                        updateGeohashCircle(latLng)
                    }
                }
            }
        )
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.map_view)
        progressBar = view.findViewById(R.id.progress_bar)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)
        
        observeState()
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mapState.collect { state ->
                    when (state) {
                        is MapState.Initial -> {
                            progressBar?.isVisible = false
                            Timber.d("초기 상태")
                        }
                        is MapState.Loading -> {
                            progressBar?.isVisible = true
                            Timber.d("로딩 중")
                        }
                        is MapState.Success -> {
                            progressBar?.isVisible = false
                            Timber.d("마커 데이터 업데이트: ${state.markers.size}개의 마커")
                            
                            // 마커 UI 업데이트
                            markerUIDelegate.updateMarkers(state.markers)
                            
                            // 현재 위치의 geohash 범위에 있는 마커만 표시
                            updateMarkersVisibility()
                        }
                        is MapState.Error -> {
                            progressBar?.isVisible = false
                            when (val error = state.error) {
                                is MapError.LocationError -> showError(error.message)
                                is MapError.PermissionError -> showError(error.message)
                                is MapError.NetworkError -> showError(error.message)
                                is MapError.UnknownError -> showError(error.message)
                            }
                            Timber.e("에러 발생: ${state.error.message}")
                        }
                    }
                }
            }
        }
    }
    
    private fun showError(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun showMarkerDetail(marker: com.parker.hotkey.domain.model.Marker) {
        // 기존에 열려있는 메모장 다이얼로그가 있다면 닫기
        childFragmentManager.findFragmentByTag("memo_list")?.let { fragment ->
            (fragment as? DialogFragment)?.dismissAllowingStateLoss()
        }

        viewModel.loadMemos(marker.id)
        val currentState = viewModel.mapState.value
        val memos = if (currentState is MapState.Success) currentState.selectedMarkerMemos else emptyList()
        
        MemoListDialog.newInstance(
            markerId = marker.id,
            memos = memos,
            mapViewModel = viewModel,
            onAddMemo = { content -> 
                viewModel.addMemo(marker.id, content)
            },
            onDeleteMemo = { memo -> 
                viewModel.deleteMemo(memo)
            },
            onDeleteMarker = { 
                viewModel.deleteMarker(marker.id)
                // 마커 UI 업데이트를 위해 markerUIDelegate 호출
                markerUIDelegate.removeMarker(marker.id)
                // 마커 삭제 후 기본 줌 레벨로 복귀
                naverMap?.moveCamera(CameraUpdate.zoomTo(DEFAULT_ZOOM))
                // 다이얼로그 닫기
                childFragmentManager.findFragmentByTag("memo_list")?.let {
                    (it as? DialogFragment)?.dismissAllowingStateLoss()
                }
            }
        ).show(childFragmentManager, "memo_list")
    }
    
    override fun onMapReady(map: NaverMap) {
        naverMap = map
        
        markerUIDelegate.setNaverMap(map)
        mapConfigDelegate.setNaverMap(map)
        
        // 기본 지도 설정
        setupMapCallbacks()
        mapConfigDelegate.setupMap()
        
        // 초기 줌 레벨 설정
        map.minZoom = MIN_ZOOM
        map.maxZoom = MAX_ZOOM
        map.moveCamera(CameraUpdate.zoomTo(DEFAULT_ZOOM))
        
        // 위치 변경 이벤트 리스너 추가
        map.addOnLocationChangeListener { location ->
            if (locationPermissionDelegate.hasLocationPermission()) {
                val currentLocation = LatLng(location.latitude, location.longitude)
                updateGeohashCircle(currentLocation)
            }
        }
        
        // 지도 클릭 이벤트 처리
        map.setOnMapClickListener { point, coord ->
            if (!locationPermissionDelegate.hasLocationPermission()) {
                showError("위치 권한이 필요합니다.")
                locationPermissionDelegate.checkLocationPermission()
                return@setOnMapClickListener
            }
            map.moveCamera(CameraUpdate.zoomTo(MARKER_CREATION_ZOOM))
            viewModel.onMapClicked(coord)
        }
        
        markerUIDelegate.setOnMarkerClickListener { marker ->
            map.moveCamera(CameraUpdate.zoomTo(MARKER_CREATION_ZOOM))
            showMarkerDetail(marker)
        }
        
        // 권한 체크 및 위치 추적 시작
        if (!locationPermissionDelegate.hasLocationPermission()) {
            Timber.d("위치 권한 없음 - 권한 요청 시작")
            locationPermissionDelegate.checkLocationPermission()
        } else {
            Timber.d("위치 권한 있음 - 위치 추적 시작")
            enableLocationTracking()
            moveToCurrentLocation(true)
        }
    }
    
    private fun setupMapCallbacks() {
        naverMap?.addOnCameraChangeListener { _, _ ->
            naverMap?.let { map ->
                viewModel.onCameraPositionChanged(
                    map.cameraPosition.target,
                    map.cameraPosition.zoom
                )
            }
        }
    }
    
    private fun updateGeohashCircle(center: LatLng) {
        currentGeohashCircle?.map = null // 기존 서클 제거
        
        currentGeohashCircle = CircleOverlay().apply {
            this.center = center
            radius = GEOHASH_RADIUS // 1.2km
            color = Color.argb(20, 76, 175, 80) // 연한 초록색 배경 (알파값 20)
            outlineWidth = 5 // 외곽선 두께 증가
            outlineColor = Color.argb(255, 76, 175, 80) // 진한 초록색 외곽선
            map = naverMap
        }
        
        // 현재 위치의 geohash 계산 및 저장
        currentLocationGeohash = GeohashUtil.encode(center.latitude, center.longitude, 6)
        currentLocationNeighbors = GeohashUtil.getNeighbors(currentLocationGeohash!!)
        
        updateMarkersVisibility()
        
        Timber.d("Current Location Geohash6: $currentLocationGeohash")
        Timber.d("Neighbor Geohashes: ${currentLocationNeighbors.joinToString()}")
    }
    
    private fun updateMarkersVisibility() {
        val validGeohashes = currentLocationNeighbors + (currentLocationGeohash ?: return)
        Timber.d("마커 가시성 업데이트: 유효한 geohash 개수 = ${validGeohashes.size}")
        
        markerUIDelegate.getMarkers().forEach { (id, uiMarker) ->
            val markerGeohash = GeohashUtil.encode(uiMarker.position.latitude, uiMarker.position.longitude, 6)
            val isInCurrentArea = markerGeohash in validGeohashes
            
            uiMarker.map = if (isInCurrentArea) naverMap else null
            if (isInCurrentArea) {
                uiMarker.alpha = 0.4f
                Timber.d("마커 표시: $id (geohash: $markerGeohash)")
            } else {
                Timber.d("마커 숨김: $id (geohash: $markerGeohash)")
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated) {
                naverMap?.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    private fun moveToCurrentLocation(forceMove: Boolean = false) {
        if (!locationPermissionDelegate.hasLocationPermission()) {
            Timber.d("위치 권한 없음 - 현재 위치로 이동 불가")
            return
        }

        try {
            Timber.d("현재 위치로 이동 시도")
            val lastLocation = locationPermissionDelegate.getLocationSource().lastLocation
            if (lastLocation != null) {
                val latLng = LatLng(lastLocation.latitude, lastLocation.longitude)
                // 카메라 이동 시 애니메이션 추가
                val cameraUpdate = CameraUpdate.scrollAndZoomTo(latLng, DEFAULT_ZOOM)
                    .animate(CameraAnimation.Easing)
                naverMap?.moveCamera(cameraUpdate)
                // 현재 위치 기준으로 서클 업데이트
                updateGeohashCircle(latLng)
                Timber.d("현재 위치로 이동 완료: lat=${latLng.latitude}, lng=${latLng.longitude}")
            } else {
                Timber.d("마지막 위치 정보 없음")
                showError("현재 위치를 가져올 수 없습니다. GPS 신호를 확인해주세요.")
            }
        } catch (e: Exception) {
            Timber.e(e, "현재 위치로 이동 중 오류 발생")
            showError("현재 위치로 이동할 수 없습니다.")
        }
    }
    
    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        
        // 권한이 있는 경우에만 위치 이동 (한 번만 체크)
        if (locationPermissionDelegate.hasLocationPermission()) {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(500) // 화면 전환 후 약간의 딜레이
                moveToCurrentLocation()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        locationPermissionDelegate.getLocationSource().deactivate()
    }
    
    override fun onStop() {
        mapView?.onStop()
        super.onStop()
    }
    
    override fun onDestroyView() {
        mapView?.onDestroy()
        super.onDestroyView()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
    
    private fun enableLocationTracking() {
        try {
            Timber.d("위치 추적 활성화 시작")
            naverMap?.let { map ->
                if (locationPermissionDelegate.hasLocationPermission()) {
                    map.locationTrackingMode = LocationTrackingMode.Follow
                    mapConfigDelegate.enableLocationTracking()
                    Timber.d("위치 추적 모드 설정 완료")
                } else {
                    Timber.d("위치 권한 없음 - 위치 추적 비활성화")
                    map.locationTrackingMode = LocationTrackingMode.None
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "위치 추적 활성화 중 오류 발생")
            showError("위치 추적을 시작할 수 없습니다.")
        }
    }

    fun setMapZoomLevel(zoomLevel: Double) {
        naverMap?.moveCamera(CameraUpdate.zoomTo(zoomLevel))
    }
} 