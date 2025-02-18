package com.haishinkit.studio

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.haishinkit.rtmp.RtmpConnection
import com.haishinkit.rtmp.RtmpStream
import com.haishinkit.event.IEventListener
import com.haishinkit.media.Camera2Source
import com.haishinkit.event.Event
import com.haishinkit.event.EventUtils
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.haishinkit.media.AudioRecordSource
import androidx.fragment.app.Fragment
import com.haishinkit.view.HkSurfaceView

class CameraTabFragment : Fragment(), IEventListener {
    private lateinit var connection: RtmpConnection
    private lateinit var stream: RtmpStream
    private lateinit var cameraView: HkSurfaceView
    private lateinit var cameraSource: Camera2Source

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.let {
            val permissionCheck = ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.CAMERA), 1)
            }
            if (ContextCompat.checkSelfPermission(
                    it,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        connection = RtmpConnection()
        stream = RtmpStream(connection)
        stream.attachAudio(AudioRecordSource())
        cameraSource = Camera2Source(requireContext()).apply {
            open(CameraCharacteristics.LENS_FACING_BACK)
        }
        stream.attachVideo(cameraSource)
        connection.addEventListener(Event.RTMP_STATUS, this)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_camera, container, false)
        val button = v.findViewById<Button>(R.id.button)
        button.setOnClickListener {
            if (button.text == "Publish") {
                connection.connect(Preference.shared.rtmpURL)
                button.text = "Stop"
            } else {
                connection.close()
                button.text = "Publish"
            }
        }
        val switchButton = v.findViewById<Button>(R.id.switch_button)
        switchButton.setOnClickListener {
            cameraSource.switchCamera()
        }
        cameraView = v.findViewById(R.id.camera)
        cameraView.attachStream(stream)
        return v
    }

    override fun onDestroy() {
        super.onDestroy()
        connection.dispose()
    }

    override fun handleEvent(event: Event) {
        Log.i("$TAG#handleEvent", event.toString())
        val data = EventUtils.toMap(event)
        val code = data["code"].toString()
        if (code == RtmpConnection.Code.CONNECT_SUCCESS.rawValue) {
            stream.publish(Preference.shared.streamName)
        }
    }

    companion object {
        fun newInstance(): CameraTabFragment {
            return CameraTabFragment()
        }

        private val TAG = CameraTabFragment::class.java.simpleName
    }
}
