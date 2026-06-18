package com.example.gpsmock

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gpsmock.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private var googleMap: GoogleMap? = null
    private var selectedLatLng = LatLng(25.0330, 121.5654)

    companion object {
        private const val PERM_REQUEST = 100

        // 台灣常用地標
        private val PRESETS = listOf(
            Triple("台北101",    25.033611,  121.565000),
            Triple("台北車站",   25.047924,  121.517081),
            Triple("桃園機場",   25.077732,  121.232822),
            Triple("台中火車站", 24.136502,  120.685547),
            Triple("高雄車站",   22.639017,  120.302017),
            Triple("花蓮市區",   23.991700,  121.601600),
            Triple("日月潭",     23.865000,  120.917000),
            Triple("阿里山",     23.508000,  120.804000),
            Triple("墾丁",       21.946000,  120.814000),
            Triple("台南安平",   22.994000,  120.161000)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMapFragment()
        setupPresetChips()
        setupInputFields()
        setupButtons()
        updateStatusUI()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    // ── 地圖 ────────────────────────────────────────────────────────────────
    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true

        map.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            fillCoordFields(latLng.latitude, latLng.longitude)
            placeMarker(latLng)
        }

        fillCoordFields(selectedLatLng.latitude, selectedLatLng.longitude)
        moveCamera(selectedLatLng, zoom = 14f)
        placeMarker(selectedLatLng)
    }

    private fun placeMarker(latLng: LatLng) {
        googleMap?.run {
            clear()
            addMarker(MarkerOptions().position(latLng).title("模擬位置"))
        }
    }

    private fun moveCamera(latLng: LatLng, zoom: Float = 14f) {
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    // ── 快速地標 Chip ────────────────────────────────────────────────────────
    private fun setupPresetChips() {
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        PRESETS.forEach { (name, lat, lng) ->
            val chip = Chip(this).apply {
                text = name
                isClickable = true
                isCheckable = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = dp8 }
            }
            chip.setOnClickListener {
                selectedLatLng = LatLng(lat, lng)
                fillCoordFields(lat, lng)
                placeMarker(selectedLatLng)
                moveCamera(selectedLatLng, zoom = 14f)
            }
            binding.llPresets.addView(chip)
        }
    }

    // ── 輸入框 ───────────────────────────────────────────────────────────────
    private fun setupInputFields() {
        binding.etLatitude.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        binding.etLongitude.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED

        binding.etLongitude.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { applyInputToMap(); true } else false
        }
    }

    private fun fillCoordFields(lat: Double, lng: Double) {
        binding.etLatitude.setText(String.format("%.6f", lat))
        binding.etLongitude.setText(String.format("%.6f", lng))
    }

    private fun applyInputToMap(): Boolean {
        val lat = binding.etLatitude.text.toString().toDoubleOrNull()
        val lng = binding.etLongitude.text.toString().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "座標格式錯誤（緯度 -90~90，經度 -180~180）", Toast.LENGTH_LONG).show()
            return false
        }
        selectedLatLng = LatLng(lat, lng)
        placeMarker(selectedLatLng)
        moveCamera(selectedLatLng)
        return true
    }

    // ── 按鈕 ─────────────────────────────────────────────────────────────────
    private fun setupButtons() {
        binding.btnApply.setOnClickListener { applyInputToMap() }

        binding.btnStart.setOnClickListener {
            if (!isMockLocationEnabled()) { showMockSettingDialog(); return@setOnClickListener }
            val lat = binding.etLatitude.text.toString().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(this, "請先輸入有效座標", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startMockService(lat, lng, readAlt(), readAcc(), readSpd())
            updateStatusUI()
        }

        binding.btnUpdate.setOnClickListener {
            val lat = binding.etLatitude.text.toString().toDoubleOrNull()
            val lng = binding.etLongitude.text.toString().toDoubleOrNull()
            if (lat == null || lng == null) {
                Toast.makeText(this, "請先輸入有效座標", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateMockService(lat, lng, readAlt(), readAcc(), readSpd())
            placeMarker(LatLng(lat, lng))
            moveCamera(LatLng(lat, lng))
            Toast.makeText(this, "座標已更新", Toast.LENGTH_SHORT).show()
        }

        binding.btnStop.setOnClickListener {
            stopMockService()
            updateStatusUI()
        }

        binding.btnDevOptions.setOnClickListener { openDeveloperOptions() }
    }

    private fun readAlt() = binding.etAltitude.text.toString().toDoubleOrNull() ?: 10.0
    private fun readAcc() = binding.etAccuracy.text.toString().toFloatOrNull() ?: 3.0f
    private fun readSpd() = binding.etSpeed.text.toString().toFloatOrNull() ?: 0.0f

    // ── Service 控制 ─────────────────────────────────────────────────────────
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
    }

    private fun stopMockService() {
        startService(Intent(this, MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        })
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

    // ── Mock 位置權限檢查 ─────────────────────────────────────────────────────
    private fun isMockLocationEnabled(): Boolean {
        return try {
            val opsManager = getSystemService(APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                opsManager.unsafeCheckOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
            } else {
                @Suppress("DEPRECATION")
                opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun showMockSettingDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要開啟模擬位置權限")
            .setMessage(
                "請至：\n設定 → 開發人員選項 → 選取模擬位置應用程式\n" +
                "→ 選擇「GPS座標修改器」，再回來按「開始」。"
            )
            .setPositiveButton("前往設定") { _, _ -> openDeveloperOptions() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "請手動前往「設定 → 開發人員選項」", Toast.LENGTH_LONG).show()
        }
    }

    // ── 位置權限 ──────────────────────────────────────────────────────────────
    private fun checkPermissions() {
        val missing = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "需要位置權限才能使用本 App", Toast.LENGTH_LONG).show()
        }
    }
}
