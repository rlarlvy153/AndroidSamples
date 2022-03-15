package com.kgpexample.camerapreviewandcapture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.app.ActivityCompat
import com.kgpexample.camerapreviewandcapture.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var surfaceHolder: SurfaceHolder
    lateinit var cameraHandler: Handler
    lateinit var cameraExecutor: Executor
    lateinit var imageReader: ImageReader
    lateinit var previewBuilder: CaptureRequest.Builder
    lateinit var cameraSession: CameraCaptureSession
    var cameraDevice: CameraDevice? = null
    private val cameraId = CameraCharacteristics.LENS_FACING_BACK.toString() //셀카

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e("kgpp", "cannot open camera")
        }
    }

    private val sessionPreviewStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            cameraSession = session

            try {
                previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                cameraSession.setRepeatingRequest(previewBuilder.build(), null, cameraHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
    }

    private val imageAvailableListenerForCapture = object : ImageReader.OnImageAvailableListener {
        override fun onImageAvailable(reader: ImageReader?) {
            if (reader == null)
                return

            reader.acquireLatestImage().use {
                val buffer = it.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                mainExecutor.execute {
                    saveImage(bitmap)
                }
            }
        }
    }

    private val captureCallback = object: CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            cameraSession = session
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            cameraSession.setRepeatingRequest(previewBuilder.build(), null, cameraHandler)
        }

        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {
            super.onCaptureProgressed(session, request, partialResult)
            cameraSession = session
        }
    }

    private fun saveImage(bitmap: Bitmap) {
        val savePath = filesDir.absolutePath + "/temp.png"

        try {
            val file = File(savePath)
            if (file.exists()) {
                file.delete()
            }
            val created = file.createNewFile()

            if (created) {
                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermission()

        addSurfaceCallback()

        binding.capture.setOnClickListener {
            capture()
        }
    }

    private fun capture() {
        if (cameraDevice == null) {
            return
        }

        try {
            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).also {
                it.addTarget(imageReader.surface)
                it.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                it.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                it.set(CaptureRequest.JPEG_ORIENTATION, 0)
            }

            val captureRequest = captureRequestBuilder.build()

            cameraSession.capture(captureRequest, captureCallback, cameraHandler)


        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

    }

    private fun startPreview() {
        previewBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewBuilder.addTarget(surfaceHolder.surface)
        val previewConfig = OutputConfiguration(surfaceHolder.surface)
        val captureConfig = OutputConfiguration(imageReader.surface)


        val sessionConfiguration = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(previewConfig, captureConfig),
            cameraExecutor,
            sessionPreviewStateCallback
        )
        cameraDevice!!.createCaptureSession(sessionConfiguration)
    }

    private fun initCamera() {
        val cameraThread = HandlerThread("camera_thread")
        cameraThread.start()
        cameraHandler = Handler(cameraThread.looper)
        cameraExecutor = Executors.newSingleThreadExecutor()

        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val characteristicsForStream = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val availableSize = characteristicsForStream.getOutputSizes(ImageFormat.JPEG)

            for (idList in cameraManager.cameraIdList) {
                Log.d("kgpp", "id list $idList")
            }

            for (size in availableSize) {
                Log.d("kgpp", "width ${size.width} height ${size.height}");
            }
            val captureSize = availableSize[0]

            imageReader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 3)
            imageReader.setOnImageAvailableListener(imageAvailableListenerForCapture, cameraHandler)

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return
            }
            cameraManager.openCamera(cameraId, cameraExecutor, cameraStateCallback)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addSurfaceCallback() {
        surfaceHolder = binding.cameraPreviewSurface.holder
        surfaceHolder.addCallback(object : SurfaceHolder.Callback2 {
            override fun surfaceCreated(p0: SurfaceHolder) {
                initCamera()
            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                Log.d("kgpp", "surfaceCreated")
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) {
                Log.d("kgpp", "surfaceCreated")
            }

            override fun surfaceRedrawNeeded(p0: SurfaceHolder) {
                Log.d("kgpp", "surfaceRedrawNeeded")
            }

        })


    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults[0] == PackageManager.PERMISSION_DENIED || grantResults[1] == PackageManager.PERMISSION_DENIED) {

        } else {

        }
    }
}