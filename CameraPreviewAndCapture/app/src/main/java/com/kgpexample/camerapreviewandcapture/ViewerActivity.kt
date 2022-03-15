package com.kgpexample.camerapreviewandcapture

import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import com.kgpexample.camerapreviewandcapture.databinding.ActivityViewerBinding
import java.io.File

class ViewerActivity : AppCompatActivity() {
    lateinit var binding: ActivityViewerBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra("path")
        val file = File(imagePath)
        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(file))
        binding.imageView.setImageBitmap(bitmap)
    }
}