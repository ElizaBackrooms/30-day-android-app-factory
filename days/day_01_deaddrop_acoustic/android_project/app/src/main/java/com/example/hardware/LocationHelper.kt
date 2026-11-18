package com.example.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationFetchResult {
  data class Success(val coordinates: String, val provider: String) : LocationFetchResult
  data object PermissionDenied : LocationFetchResult
  data class Unavailable(val reason: String) : LocationFetchResult
}

class LocationHelper(private val context: Context) {

  fun hasLocationPermission(): Boolean {
    val fine =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarse =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return fine || coarse
  }

  @SuppressLint("MissingPermission")
  suspend fun fetchCurrentCoordinates(): LocationFetchResult =
    withContext(Dispatchers.Main) {
      if (!hasLocationPermission()) {
        return@withContext LocationFetchResult.PermissionDenied
      }

      val manager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
          ?: return@withContext LocationFetchResult.Unavailable("LocationManager unavailable")

      val bestLast = getBestLastKnownLocation(manager)
      if (bestLast != null && (System.currentTimeMillis() - bestLast.time) < 120_000L) {
        return@withContext LocationFetchResult.Success(
          coordinates = formatLocation(bestLast),
          provider = bestLast.provider ?: "cached",
        )
      }

      val activeProviders =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { provider ->
          try {
            manager.isProviderEnabled(provider)
          } catch (_: Throwable) {
            false
          }
        }

      for (provider in activeProviders) {
        val fresh =
          withTimeoutOrNull(3500L) {
            requestSingleFix(manager, provider)
          }
        if (fresh != null) {
          return@withContext LocationFetchResult.Success(
            coordinates = formatLocation(fresh),
            provider = fresh.provider ?: provider,
          )
        }
      }

      if (bestLast != null) {
        return@withContext LocationFetchResult.Success(
          coordinates = formatLocation(bestLast),
          provider = "${bestLast.provider ?: "last"} (cached)",
        )
      }

      LocationFetchResult.Unavailable("No GPS/Network fix available — enter Grid/Coords manually")
    }

  @SuppressLint("MissingPermission")
  private fun getBestLastKnownLocation(manager: LocationManager): Location? {
    val providers =
      listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
      )
    var best: Location? = null
    for (provider in providers) {
      val loc =
        try {
          manager.getLastKnownLocation(provider)
        } catch (_: Throwable) {
          null
        } ?: continue
      if (best == null || loc.time > best.time) {
        best = loc
      }
    }
    return best
  }

  @SuppressLint("MissingPermission")
  private suspend fun requestSingleFix(
    manager: LocationManager,
    provider: String,
  ): Location? =
    suspendCancellableCoroutine { cont ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        try {
          manager.getCurrentLocation(
            provider,
            signal,
            context.mainExecutor,
          ) { location ->
            if (cont.isActive) {
              cont.resume(location)
            }
          }
        } catch (_: Throwable) {
          if (cont.isActive) cont.resume(null)
        }
      } else {
        var listenerRef: LocationListener? = null
        val listener =
          object : LocationListener {
            override fun onLocationChanged(location: Location) {
              try {
                manager.removeUpdates(this)
              } catch (_: Throwable) {}
              if (cont.isActive) cont.resume(location)
            }

            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
          }
        listenerRef = listener
        cont.invokeOnCancellation {
          try {
            listenerRef?.let { manager.removeUpdates(it) }
          } catch (_: Throwable) {}
        }
        try {
          @Suppress("DEPRECATION")
          manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: Throwable) {
          if (cont.isActive) cont.resume(null)
        }
      }
    }

  private fun formatLocation(location: Location): String =
    String.format(Locale.US, "%.5f,%.5f", location.latitude, location.longitude)
}
