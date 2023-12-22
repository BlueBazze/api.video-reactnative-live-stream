package video.api.reactnative.livestream

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.PermissionListener

/**
 * Check if the app has the given permissions.
 * For a single permission or multiple permissions.
 */
class PermissionsManager(
  private val reactContext: ReactApplicationContext,
) : PermissionListener {
  private var uniqueRequestCode = 1

  // To request permission, we need the activity
  private val activity = reactContext.currentActivity!!

  private val listeners = mutableMapOf<Int, IListener>()
  private fun hasPermission(permission: String) =
    ContextCompat.checkSelfPermission(reactContext, permission) == PackageManager.PERMISSION_GRANTED

  private fun hasAllPermissions(permissions: List<String>) = permissions.all { permission ->
    ContextCompat.checkSelfPermission(
      reactContext,
      permission
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun shouldShowRequestPermissionRationale(
    activity: Activity,
    permissions: List<String>
  ) =
    permissions.filter { permission ->
      ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

  fun requestPermissions(
    permissions: List<String>,
    onAllGranted: () -> Unit,
    onShowPermissionRationale: (List<String>, () -> Unit) -> Unit,
    onAtLeastOnePermissionDenied: () -> Unit
  ) {
    requestPermissions(permissions, object : IListener {
      override fun onAllGranted() {
        onAllGranted()
      }

      override fun onShowPermissionRationale(
        permissions: List<String>,
        onRequiredPermissionLastTime: () -> Unit
      ) {
        onShowPermissionRationale(permissions, onRequiredPermissionLastTime)
      }

      override fun onAtLeastOnePermissionDenied() {
        onAtLeastOnePermissionDenied()
      }
    })
  }

  private fun requestPermissions(
    permissions: List<String>,
    listener: IListener
  ) {
    val currentRequestCode = synchronized(this) {
      uniqueRequestCode++
    }
    listeners[currentRequestCode] = listener
    when {
      hasAllPermissions(permissions) -> listener.onAllGranted()
      shouldShowRequestPermissionRationale(activity, permissions).isNotEmpty() -> {
        val missingPermissions = shouldShowRequestPermissionRationale(activity, permissions)
        listener.onShowPermissionRationale(missingPermissions) {
          ActivityCompat.requestPermissions(
            activity,
            missingPermissions.toTypedArray(),
            currentRequestCode
          )
        }
      }

      else -> ActivityCompat.requestPermissions(
        activity,
        permissions.toTypedArray(),
        currentRequestCode
      )
    }
  }

  fun requestPermission(
    permission: String,
    onGranted: () -> Unit,
    onShowPermissionRationale: (() -> Unit) -> Unit,
    onDenied: () -> Unit
  ) {
    requestPermissions(listOf(permission), object : IListener {
      override fun onAllGranted() {
        onGranted()
      }

      override fun onShowPermissionRationale(
        permissions: List<String>,
        onRequiredPermissionLastTime: () -> Unit
      ) {
        onShowPermissionRationale(onRequiredPermissionLastTime)
      }

      override fun onAtLeastOnePermissionDenied() {
        onDenied()
      }
    })
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ): Boolean {
    val listener = listeners[requestCode] ?: return false
    listeners.remove(requestCode)

    grantResults.forEach {
      if (it == PackageManager.PERMISSION_GRANTED) {
        listener.onGranted(permissions[grantResults.indexOf(it)])
      } else {
        listener.onDenied(permissions[grantResults.indexOf(it)])
      }
    }
    if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
      listener.onAllGranted()
    } else {
      listener.onAtLeastOnePermissionDenied()
    }

    return listeners.isEmpty()
  }

  interface IListener {
    fun onAllGranted() {}
    fun onGranted(permission: String) {}
    fun onShowPermissionRationale(
      permissions: List<String>,
      onRequiredPermissionLastTime: () -> Unit
    ) {
    }

    fun onDenied(permission: String) {}
    fun onAtLeastOnePermissionDenied() {}
  }
}
