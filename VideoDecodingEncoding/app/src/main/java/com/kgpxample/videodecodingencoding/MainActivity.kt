package com.kgpxample.videodecodingencoding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.kgpxample.videodecodingencoding.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback{

    lateinit var binding: ActivityMainBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private var fromUri = Uri.EMPTY
    private var fromPath = ""
    private var outputDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
    private var converter : Converter? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestPermission()

        addListener()

        init()
    }

    private fun addListener() {
        binding.chooseInput.setOnClickListener {
            startPickerActivity()
        }

        binding.convert.setOnClickListener {
            if (fromPath.isBlank() || fromUri == Uri.EMPTY) {
                Toast.makeText(this, "please choose source video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startConvert()
        }
    }

    private fun startConvert() {

        converter = Converter(this, binding.outputSurface.holder.surface, fromUri, fromPath, outputDir)
        converter?.start()
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 100)
    }

    private fun init() {
        binding.outputSurface.holder.addCallback(this)
        activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->

            if (result.resultCode == VideoPickerActivity.RESULT_CODE) {
                val resultData = result.data
                val resultPath = resultData?.getStringExtra(VideoPickerActivity.KEY_RESULT_PATH) ?: ""
                val resultUri = resultData?.getParcelableExtra(VideoPickerActivity.KEY_RESULT_URI) ?: Uri.EMPTY
                Log.d("kgpp", "resultUri $resultUri")
                Log.d("kgpp", "resultPath $resultPath")
                binding.inputDirText.text = resultPath

                fromUri = resultUri
                fromPath = resultPath

            }
        }
    }

    private fun startPickerActivity() {
        val intent = Intent(this, VideoPickerActivity::class.java)
        activityResultLauncher.launch(intent)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }
}