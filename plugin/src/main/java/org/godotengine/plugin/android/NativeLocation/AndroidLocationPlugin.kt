package org.godotengine.plugin.android.AndroidLocationPlugin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot


class AndroidLocationPlugin(godot: Godot): GodotPlugin(godot) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_ACCESS_DIRECT_APPEAL_ID: Int = 0x1
    private val LOCATION_PERMISSION_ID: Int = 42


    override fun getPluginName() = "AndroidLocationPlugin"

    override fun getPluginSignals(): Set<SignalInfo> {
        val signal = mutableSetOf<SignalInfo>()
        signal.add(SignalInfo("LocationUpdated",java.lang.Double::class.java,java.lang.Double::class.java))
        signal.add(SignalInfo("AuthorizationStatusUpdated",Integer::class.java))
        signal.add( SignalInfo("LocationStatusUpdated",Integer::class.java))
        signal.add(SignalInfo("DialogueResultUpdated",Integer::class.java))
        return signal
    }

    override fun onMainCreate(activity: Activity?): View? {
        activity?.let {fusedLocationClient = LocationServices.getFusedLocationProviderClient(it)}
        return super.onMainCreate(activity)
    }

    override fun onMainRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>?,
        grantResults: IntArray?
    ) {
        if (requestCode==LOCATION_PERMISSION_ID) {
            // Check if all required permissions are granted
            val somePermissionsGranted = grantResults?.any { it == PackageManager.PERMISSION_GRANTED }

            if (somePermissionsGranted == true) {
                emitSignal("AuthorizationStatusUpdated", Integer.valueOf(1))
            } else {
                emitSignal("AuthorizationStatusUpdated", Integer.valueOf(0))
                emitSignal("LocationStatusUpdated", Integer.valueOf(0))
            }

        }
    }

    override fun onMainActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onMainActivityResult(requestCode, resultCode, data)

        if (requestCode==LOCATION_ACCESS_DIRECT_APPEAL_ID) {
            if (resultCode == Activity.RESULT_OK) {
                // Location settings are satisfied, proceed with your app's location functionality
                emitSignal("DialogueResultUpdated", Integer.valueOf(1))
                StartLocationService()
            } else {
                // direct appeal rejected. show soft appeal.
                showSoftLocationDialogueAppeal()
                emitSignal("DialogueResultUpdated", Integer.valueOf(0))
            }
        }
    }

    //--------------------------------------------------------------------------------------------------------
// --------------------------------            FUNCTION START HERE           ---------------------------------
//------------------------------------------------------------------------------------------------------------

    @UsedByGodot
    private fun StartLocationService() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    val location: Location? = task.result
                    if (location == null) {
                        LocationAccessDetails()
                    } else {
                        emitSignal("LocationUpdated", location.latitude,location.longitude)
                        emitSignal("LocationStatusUpdated", Integer.valueOf(1))
                    }
                }
            } else {
                emitSignal("LocationStatusUpdated", Integer.valueOf(2))
                showDirectLocationDialogueAppeal()
            }
        } else {
            requestLocationPermissions()
        }
    }

    @UsedByGodot
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        emitSignal("LocationStatusUpdated", Integer.valueOf(3))
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = activity?.let { getSystemService(it, LocationManager::class.java) } as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    private fun checkPermissions(): Boolean {
        activity?.let {
            return ActivityCompat.checkSelfPermission(it,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(it,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private fun LocationAccessDetails() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(50)
            .setMaxUpdateDelayMillis(100)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }


    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation: Location = locationResult.lastLocation as Location
            emitSignal("LocationStatusUpdated", Integer.valueOf(1))
            emitSignal("LocationUpdated",lastLocation.latitude,lastLocation.longitude)
        }
    }


    @UsedByGodot
    private fun requestLocationPermissions() {
        activity?.let {
            ActivityCompat.requestPermissions(
                it,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                LOCATION_PERMISSION_ID
            )
        }
    }


    @UsedByGodot
    private fun ShowLocationPermissionAppeal(title:String, message:String) {
        activity?.let {
            it.runOnUiThread {
                val builder = AlertDialog.Builder(it, android.R.style.Theme_DeviceDefault_Dialog)
                builder.setTitle(title)
                builder.setMessage(message)
                builder.setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", it.packageName, null)
                    startActivity(it, intent, null)
                    emitSignal("DialogueResultUpdated", Integer.valueOf(1))

                }
                builder.setNegativeButton("Cancel"){_,_ ->
                    emitSignal("DialogueResultUpdated", Integer.valueOf(0))
                }
                builder.show()
            }
        }
    }

    private fun showSoftLocationDialogueAppeal() {
        activity?.let {
            val builder = AlertDialog.Builder(it,android.R.style.Theme_DeviceDefault_Dialog)
            builder.setTitle("Enable Location")
            builder.setMessage("Location services are disabled. Please enable them from settings.")
            builder.setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(it, intent, null)
                emitSignal("DialogueResultUpdated", Integer.valueOf(1))
            }
            builder.setNegativeButton("Cancel") {_,_->
                emitSignal("DialogueResultUpdated", Integer.valueOf(0))
            }
            builder.show()
        }
    }


    @Suppress("unused")
    fun showDirectLocationDialogueAppeal() {
        val mLocationRequest: LocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        )
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(500)
            .setMaxUpdateDelayMillis(1000)
            .build()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest)
        activity?.let {
            val client =  LocationServices.getSettingsClient(it)
            val task = client.checkLocationSettings(builder.build())

            task.addOnSuccessListener(it) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
            }

            task.addOnFailureListener(it) { e ->
                //Toast.makeText(currentActivity, "Unable to turn on Device Location", Toast.LENGTH_SHORT).show();
                if (e is ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        e.startResolutionForResult(it, LOCATION_ACCESS_DIRECT_APPEAL_ID)
                    } catch (sendEx: SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        }
    }

}
