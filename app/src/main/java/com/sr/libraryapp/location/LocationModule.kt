package com.sr.libraryapp.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.os.CancellationSignal
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sr.libraryapp.utils.Const
import com.sr.libraryapp.utils.LocationPermissionUtils
import com.sr.libraryapp.utils.getLocationString
import com.sr.libraryapp.utils.logPrint
import com.sr.libraryapp.utils.showToast
import java.util.concurrent.TimeUnit


object LocationModule{

    private val TAG = "LocationModule"

    private lateinit var activity : AppCompatActivity

    private lateinit var locationManager : LocationManager

    private lateinit var locationRequest: LocationRequest

    private val WAKE_LOCK_TAG = "LibraryApp:WAKE_LOCK_TAG";

    private var wakeLock : PowerManager.WakeLock? = null;

    private val gadgetQ = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private var isRepeated = false

    private var updateTimeInterval : Long  = 0

    private var getLocationRequestCallBack : ()->Unit = {
        checkProvider()
    }

    private var onLocationFoundOperation : (Location)->Unit={}

    fun init(activity:AppCompatActivity){
        this.activity = activity
        LocationPermissionUtils.init(activity,getLocationRequestCallBack,onLocationFoundOperation)

    }
    private fun checkProvider(){

        if(Const.LOCATION_PERMISSION.any { LocationPermissionUtils.checkSelfPermission(it) }){
            LocationPermissionUtils.checkPermission(locationRequest)
            return
        }

        //startWakeLock()
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, updateTimeInterval, 0f, onLocationChangedCallBack)

        } else if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,updateTimeInterval,0f,onLocationChangedCallBack)
        }
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,updateTimeInterval,0f,onLocationChangedCallBack)
        var locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        var locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        var gpsLocationTime : Long = 0
        locationGPS?.let {
            gpsLocationTime = it.time
        }
        var networkLocationTime : Long = 0
        locationNetwork?.let {
            networkLocationTime = it.time
        }

        var latestLoc = if(gpsLocationTime>networkLocationTime) locationGPS else locationNetwork
        onLocationChangedCallBack.onLocationChanged(latestLoc!!)

        if(!isRepeated)
            locationManager.removeUpdates(onLocationChangedCallBack)
        //stopWakeLock()

    }

    fun getLocation(
        isRepeated : Boolean = false,
        timeIntervalInMilli : Long = TimeUnit.MINUTES.toMinutes(30),
        onLocationFoundOperation:(Location)->Unit
    ){
        if(!this::activity.isInitialized){
            logPrint(TAG, "initializeVariables: activity is not initialized")
            return
        }
        this.isRepeated = isRepeated
        this.updateTimeInterval = timeIntervalInMilli
        this.onLocationFoundOperation = onLocationFoundOperation
        locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        this.locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,timeIntervalInMilli).build()
        LocationPermissionUtils.checkPermission(locationRequest)
    }

    private fun startWakeLock(){
        if(wakeLock==null) {
            var powerManager: PowerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,WAKE_LOCK_TAG) as PowerManager.WakeLock
        }
        wakeLock?.acquire()
    }
    private fun stopWakeLock(){
        if(wakeLock?.isHeld==true)
            wakeLock?.release()
    }

    private val onLocationChangedCallBack = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationFoundOperation.invoke(location)
        }
    }
}