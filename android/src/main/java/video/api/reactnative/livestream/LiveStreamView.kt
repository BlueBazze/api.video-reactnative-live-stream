package video.api.reactnative.livestream

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.constraintlayout.widget.ConstraintLayout
import com.facebook.react.uimanager.ThemedReactContext
import video.api.livestream.ApiVideoLiveStream
import video.api.livestream.enums.CameraFacingDirection
import video.api.livestream.interfaces.IConnectionChecker
import video.api.livestream.models.AudioConfig
import video.api.livestream.models.VideoConfig
import video.api.reactnative.livestream.utils.showDialog
import java.io.Closeable


@SuppressLint("MissingPermission")
class LiveStreamView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : ConstraintLayout(context, attrs, defStyle),
  Closeable {
  private val liveStream: ApiVideoLiveStream
  private val permissionsManager =
    PermissionsManager((context as ThemedReactContext).reactApplicationContext)

  var onConnectionSuccess: (() -> Unit)? = null
  var onConnectionFailed: ((reason: String?) -> Unit)? = null
  var onDisconnected: (() -> Unit)? = null

  private val connectionListener = object : IConnectionChecker {
    override fun onConnectionSuccess() {
      onConnectionSuccess?.let { it() }
    }

    override fun onConnectionFailed(reason: String) {
      onConnectionFailed?.let { it(reason) }
    }

    override fun onDisconnect() {
      onDisconnected?.let { it() }
    }
  }

  init {
    inflate(context, R.layout.react_native_livestream, this)
    liveStream = ApiVideoLiveStream(
      context = context,
      connectionChecker = connectionListener,
      apiVideoView = findViewById(R.id.apivideo_view)
    )
  }

  var videoBitrate: Int
    get() = liveStream.videoBitrate
    set(value) {
      liveStream.videoBitrate = value
    }

  var videoConfig: VideoConfig?
    get() = liveStream.videoConfig
    set(value) {
      permissionsManager.requestPermission(
        Manifest.permission.CAMERA,
        onGranted = {
          liveStream.videoConfig = value
        },
        onShowPermissionRationale = { onRequiredPermissionLastTime ->
          context.showDialog(
            R.string.permission_required,
            R.string.camera_permission_required_message,
            android.R.string.ok,
            onPositiveButtonClick = { onRequiredPermissionLastTime() }
          )
        },
        onDenied = {
          Log.e(TAG, "Missing permissions Manifest.permission.CAMERA")
          throw UnsupportedOperationException("Missing permission Manifest.permission.CAMERA")
        })
    }


  var audioConfig: AudioConfig?
    get() = liveStream.audioConfig
    set(value) {
      permissionsManager.requestPermission(
        Manifest.permission.RECORD_AUDIO,
        onGranted = {
          liveStream.audioConfig = value
        },
        onShowPermissionRationale = { onRequiredPermissionLastTime ->
          context.showDialog(
            R.string.permission_required,
            R.string.record_audio_permission_required_message,
            android.R.string.ok,
            onPositiveButtonClick = { onRequiredPermissionLastTime() }
          )
        },
        onDenied = {
          Log.e(TAG, "Missing permissions Manifest.permission.RECORD_AUDIO")
          throw UnsupportedOperationException("Missing permission Manifest.permission.RECORD_AUDIO")
        })
    }

  val isStreaming: Boolean
    get() = liveStream.isStreaming

  var camera: CameraFacingDirection = CameraFacingDirection.BACK
    get() = liveStream.camera
    set(value) {
      liveStream.camera = value
      field = value
    }

  var isMuted: Boolean = false
    get() = liveStream.isMuted
    set(value) {
      liveStream.isMuted = value
      field = value
    }

  var zoomRatio: Float
    get() = liveStream.zoomRatio
    set(value) {
      liveStream.zoomRatio = value
    }

  var enablePinchedZoom: Boolean = false
    @SuppressLint("ClickableViewAccessibility")
    set(value) {
      if (value) {
        this.setOnTouchListener { _, event ->
          pinchGesture.onTouchEvent(event)
        }
      } else {
        this.setOnTouchListener(null)
      }
      field = value
    }

  private val pinchGesture: ScaleGestureDetector by lazy {
    ScaleGestureDetector(
      context,
      object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var savedZoomRatio: Float = 1f
        override fun onScale(detector: ScaleGestureDetector): Boolean {
          zoomRatio = if (detector.scaleFactor < 1) {
            savedZoomRatio * detector.scaleFactor
          } else {
            savedZoomRatio + ((detector.scaleFactor - 1))
          }
          return super.onScale(detector)
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
          savedZoomRatio = zoomRatio
          return super.onScaleBegin(detector)
        }
      })
  }

  fun startStreaming(streamKey: String, url: String?) {
    url?.let { liveStream.startStreaming(streamKey, it) }
      ?: liveStream.startStreaming(streamKey)
  }

  fun stopStreaming() {
    liveStream.stopStreaming()
  }

  override fun close() {
    liveStream.release()
  }

  companion object {
    private const val TAG = "RNLiveStreamView"
  }
}
