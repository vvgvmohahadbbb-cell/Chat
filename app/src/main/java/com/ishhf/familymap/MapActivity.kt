package com.ishhf.familymap

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** خريطة العائلة عبر OpenStreetMap (osmdroid) - مجانية بالكامل بدون مفتاح API */
class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var prefs: SharedPreferences
    private val db = FirebaseFirestore.getInstance()
    private val markers = HashMap<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
        Configuration.getInstance().osmdroidTileCache = File(Configuration.getInstance().osmdroidBasePath, "tiles")
        Configuration.getInstance().userAgentValue = packageName

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        prefs = getSharedPreferences("family_map_prefs", MODE_PRIVATE)

        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(6.0)
        mapView.controller.setCenter(GeoPoint(24.7136, 46.6753))

        val shareSwitch = findViewById<Switch>(R.id.shareSwitch)
        // نعيد ضبط حالة المفتاح متل ما كانت آخر مرة، قبل ما نعلّق المستمع
        // عشان ما تنرجع الشاشة تبين "موقف" وهي فعلياً شغالة بالخلفية
        shareSwitch.isChecked = prefs.getBoolean("sharing_enabled", false)
        shareSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableSharing() else disableSharing()
        }

        findViewById<Button>(R.id.btnChat).setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        findViewById<Button>(R.id.btnMembers).setOnClickListener {
            startActivity(Intent(this, MembersActivity::class.java))
        }

        requestLocationPermissions()
        listenToFamilyLocations()
        ContextCompat.startForegroundService(this, Intent(this, ChatNotificationService::class.java))
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun myUsername(): String? = prefs.getString("my_username", null)

    private fun requestLocationPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                10
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 12)
            }
        }
    }

    private fun enableSharing() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            Toast.makeText(this, "لازم توافق على صلاحية الموقع أولاً", Toast.LENGTH_LONG).show()
            requestLocationPermissions()
            return
        }
        requestBackgroundLocationIfNeeded()
        val username = myUsername() ?: return
        db.collection("users").document(username).set(mapOf("sharing" to true), SetOptions.merge())
        ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
        prefs.edit().putBoolean("sharing_enabled", true).apply()
        Toast.makeText(this, "تم تفعيل مشاركة موقعك 📍", Toast.LENGTH_SHORT).show()
    }

    private fun disableSharing() {
        val username = myUsername() ?: return
        db.collection("users").document(username).set(mapOf("sharing" to false), SetOptions.merge())
        stopService(Intent(this, LocationService::class.java))
        prefs.edit().putBoolean("sharing_enabled", false).apply()
        Toast.makeText(this, "تم إيقاف مشاركة موقعك", Toast.LENGTH_SHORT).show()
    }

    private fun listenToFamilyLocations() {
        db.collection("users").addSnapshotListener { snapshots, error ->
            if (error != null || snapshots == null) return@addSnapshotListener

            for (change in snapshots.documentChanges) {
                val doc = change.document
                val uid = doc.id
                val name = doc.getString("name") ?: "بدون اسم"
                val sharing = doc.getBoolean("sharing") ?: false
                val lat = doc.getDouble("lat")
                val lng = doc.getDouble("lng")

                if (!sharing || lat == null || lng == null) {
                    markers[uid]?.let { mapView.overlays.remove(it) }
                    markers.remove(uid)
                    mapView.invalidate()
                    continue
                }

                val pos = GeoPoint(lat, lng)
                val existing = markers[uid]
                if (existing != null) {
                    existing.position = pos
                    existing.title = name
                } else {
                    val marker = Marker(mapView)
                    marker.position = pos
                    marker.title = name
                    mapView.overlays.add(marker)
                    markers[uid] = marker
                }
                mapView.invalidate()
            }
        }
    }
}
