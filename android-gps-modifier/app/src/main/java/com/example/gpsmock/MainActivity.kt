package com.example.gpsmock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gpsmock.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var googleMap: GoogleMap? = null
    private var selectedLatLng: LatLng = LatLng(25.0330, 121.5654) // 預設台北 101

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMapFragment()
        setupInputFields()
        setupButtons()
        updateStatusUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    // ── 地圖 ──────────────────────────────────────────────────────────────
    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }
        // 點擊地圖選點
        map.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            binding.etLatitude.setText(String.format("%.6f", latLng.latitude))
            binding.etLongitude.setText(String.format("%.6f", latLng.longitude))
            placeMarker(latLng)
        }
        moveCamera(selectedLatLng, zoom = 15f)
        placeMarker(selectedLatLng)
        binding.etLatitude.setText(String.format("%.6f", selectedLatLng.latitude))
        binding.etLongitude.setText(String.format("%.6f", selectedLatLng.longitude))
    }

    private fun placeMarker(latLng: LatLng) {
        googleMap?.run {
            clear()
            addMarker(MarkerOptions().position(latLng).title("模擬位置"))
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float = 15f) {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    // ── 輸入框 ─────────────────────────────────────────────────────────────
    private fun setupInputFields() {
        // 按 Enter 後觸發地圖更新
        binding.etLongitude.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyInputToMap()
                true
            } else false
        }
        binding.etLatitude.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        binding.etLongitude.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
    }

    private fun applyInputToMap() {
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "座標格式錯誤，請輸入有效的緯度 (-90~90) 和經度 (-180~180)", Toast.LENGTH_LONG).show()
            return
        }
        selectedLatLng = LatLng(lat, lng)
        placeMarker(selectedLatLng)
        moveCamera(selectedLatLng)
    }

    // ── 按鈕 ────────────────────────────────────────────────────────────────
    private fun setupButtons() {
        // 從輸入框搜尋並移動地圖標記
        binding.btnApply.setOnClickListener { applyInputToMap() }

        // 啟動 Mock
        binding.btnStart.setOnClickListener {
            if (!isMockLocationEnabled()) {
                showMockLocationSettingDialog()
                return@setOnClickListener
            }
            val lat = binding.etLatitude.text.toString().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(this, "請先輸入有效座標", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val alt = binding.etAltitude.text.toString().toDoubleOrNull() ?: 10.0
            val acc = binding.etAccuracy.text.toString().toFloatOrNull() ?: 3.0f
            val spd = binding.etSpeed.text.toString().toFloatOrNull() ?: 0.0f

            startMockService(lat, lng, alt, acc, spd)
            updateStatusUI()
        }

        // 更新座標 (服務已在執行時)
        binding.btnUpdate.setOnClickListener {
            val lat = binding.etLatitude.text.toString().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(this, "請先輸入有效座標", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val alt = binding.etAltitude.text.toString().toDoubleOrNull() ?: 10.0
            val acc = binding.etAccuracy.text.toString().toFloatOrNull() ?: 3.0f
            val spd = binding.etSpeed.text.toString().toFloatOrNull() ?: 0.0f

            updateMockService(lat, lng, alt, acc, spd)
            Toast.makeText(this, "座標已更新", Toast.LENGTH_SHORT).show()
        }

        // 停止 Mock
        binding.btnStop.setOnClickListener {
            stopMockService()
            updateStatusUI()
        }

        // 前往系統開發者選項
        binding.btnDevOptions.setOnClickListener {
            openDeveloperOptions()
        }
    }

    // ── Service 控制 ────────────────────────────────────────────────────────
    private fun startMockService(lat: Double, lng: Double, alt: Double, acc: Float, spd: Float) {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_START
            putExtra(MockLocationService.EXTRA_LATITUDE, lat)
            putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
            putExtra(MockLocationService.EXTRA_ALTITUDE, alt)
            putExtra(MockLocationService.EXTRA_ACCURACY, acc)
            putExtra(MockLocationService.EXTRA_SPEED, spd)
        }.also { startForegroundService(it) }
    }

    private fun updateMockService(lat: Double, lng: Double, alt: Double, acc: Float, spd: Float) {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_UPDATE
            putExtra(MockLocationService.EXTRA_LATITUDE, lat)
            putExtra(MockLocationService.EXTRA_LONGITUDE, lng)
            putExtra(MockLocationService.EXTRA_ALTITUDE, alt)
            putExtra(MockLocationService.EXTRA_ACCURACY, acc)
            putExtra(MockLocationService.EXTRA_SPEED, spd)
        }.also { startService(it) }
        // 同步地圖
        selectedLatLng = LatLng(lat, lng)
        placeMarker(selectedLatLng)
        moveCamera(selectedLatLng)
    }

    private fun stopMockService() {
        Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }.also { startService(it) }
    }

    // ── UI 狀態 ──────────────────────────────────────────────────────────────
    private fun updateStatusUI() {
        val running = MockLocationService.isRunning
        binding.tvStatus.text = if (running) "● 執行中" else "○ 未啟動"
        binding.tvStatus.setTextColor(
            if (running) getColor(R.color.green_active) else getColor(R.color.grey_inactive)
        )
        binding.btnStart.isEnabled = !running
        binding.btnUpdate.isEnabled = running
        binding.btnStop.isEnabled = running
    }

    // ── Mock 位置檢查 ─────────────────────────────────────────────────────────
    private fun isMockLocationEnabled(): Boolean {
        return try {
            // API 23+ 用開發者選項「模擬位置應用程式」白名單
            val appOpsManager = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOp(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOp(
                    android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun showMockLocationSettingDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要設定模擬位置")
            .setMessage(
                "請至「開發者選項」→「選取模擬位置應用程式」，\n" +
                "選擇本 App（GPS座標修改器）後回來重試。"
            )
            .setPositiveButton("前往設定") { _, _ -> openDeveloperOptions() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "無法開啟開發者選項，請手動前往「設定 → 開發人員選項」", Toast.LENGTH_LONG).show()
        }
    }

    // ── 權限 ───────────────────────────────────────────────────────────────
    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "需要位置權限才能使用本 App", Toast.LENGTH_LONG).show()
            }
        }
    }
}
