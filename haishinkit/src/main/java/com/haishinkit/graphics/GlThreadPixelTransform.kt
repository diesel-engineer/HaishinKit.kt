package com.haishinkit.graphics

import android.content.res.AssetManager
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import java.lang.ref.WeakReference

class GlThreadPixelTransform(
    override var assetManager: AssetManager? = null,
    override var fpsControllerClass: Class<*>? = null,
) : PixelTransform, PixelTransform.Listener {
    override var surface: Surface?
        get() = pixelTransform.surface
        set(value) {
            handler?.let {
                it.sendMessage(it.obtainMessage(MSG_SET_SURFACE, value))
            }
        }
    override var listener: PixelTransform.Listener? = null
    override var imageOrientation: ImageOrientation
        get() = pixelTransform.imageOrientation
        set(value) {
            handler?.let {
                it.sendMessage(it.obtainMessage(MSG_SET_IMAGE_ORIENTATION, value))
            }
        }
    override var surfaceRotation: Int
        get() = pixelTransform.surfaceRotation
        set(value) {
            handler?.let {
                it.sendMessage(it.obtainMessage(MSG_SET_SURFACE_ORIENTATION, value))
            }
        }
    override var videoGravity: VideoGravity
        get() = pixelTransform.videoGravity
        set(value) {
            handler?.let {
                it.sendMessage(it.obtainMessage(MSG_SET_VIDEO_GRAVITY, value))
            }
        }
    override var extent: Size
        get() = pixelTransform.extent
        set(value) {
            handler?.let {
                it.sendMessage(
                    it.obtainMessage(
                        MSG_SET_CURRENT_EXTENT,
                        value.width,
                        value.height,
                        null
                    )
                )
            }
        }
    override var resampleFilter: ResampleFilter
        get() = pixelTransform.resampleFilter
        set(value) {
            handler?.let {
                it.sendMessage(
                    it.obtainMessage(MSG_SET_RESAMPLE_FILTER, value)
                )
            }
        }
    private var handler: Handler? = null
        get() {
            if (field == null) {
                val thread = HandlerThread(TAG)
                thread.start()
                field = Handler(pixelTransform, thread.looper)
                pixelTransform.handler = field
            }
            return field
        }
        set(value) {
            field?.looper?.quitSafely()
            field = value
        }
    private val pixelTransform: GlPixelTransform by lazy {
        val pixelTransform = GlPixelTransform()
        pixelTransform.listener = this
        pixelTransform
    }

    override fun createInputSurface(width: Int, height: Int, format: Int) {
        handler?.let {
            it.sendMessage(it.obtainMessage(MSG_CREATE_INPUT_SURFACE, width, height, format))
        }
    }

    private class Handler(frame: GlPixelTransform, looper: Looper) : android.os.Handler(looper) {
        private val weakTransform: WeakReference<GlPixelTransform> =
            WeakReference<GlPixelTransform>(frame)

        override fun handleMessage(message: Message) {
            val transform = weakTransform.get() ?: return
            when (message.what) {
                MSG_SET_SURFACE -> {
                    if (message.obj == null) {
                        transform.surface = null
                    } else {
                        transform.surface = message.obj as Surface
                    }
                }
                MSG_SET_IMAGE_ORIENTATION -> {
                    transform.imageOrientation = message.obj as ImageOrientation
                }
                MSG_SET_SURFACE_ORIENTATION -> {
                    transform.surfaceRotation = message.obj as Int
                }
                MSG_SET_VIDEO_GRAVITY -> {
                    transform.videoGravity = message.obj as VideoGravity
                }
                MSG_SET_CURRENT_EXTENT -> {
                    transform.extent = Size(message.arg1, message.arg2)
                }
                MSG_CREATE_INPUT_SURFACE -> {
                    val obj = message.obj
                    transform.createInputSurface(message.arg1, message.arg2, obj as Int)
                }
                MSG_SET_RESAMPLE_FILTER -> {
                    transform.resampleFilter = message.obj as ResampleFilter
                }
                else ->
                    throw RuntimeException("Unhandled msg what=$message.what")
            }
        }
    }

    override fun onPixelTransformSetUp(pixelTransform: PixelTransform) {
        listener?.onPixelTransformSetUp(this)
    }

    override fun onPixelTransformInputSurfaceCreated(pixelTransform: PixelTransform, surface: Surface) {
        listener?.onPixelTransformInputSurfaceCreated(this, surface)
    }

    companion object {
        private const val MSG_SET_SURFACE = 0
        private const val MSG_CREATE_INPUT_SURFACE = 1
        private const val MSG_SET_IMAGE_ORIENTATION = 2
        private const val MSG_SET_SURFACE_ORIENTATION = 3
        private const val MSG_SET_VIDEO_GRAVITY = 4
        private const val MSG_SET_CURRENT_EXTENT = 5
        private const val MSG_SET_RESAMPLE_FILTER = 6

        private val TAG = GlThreadPixelTransform::class.java.simpleName
    }
}
