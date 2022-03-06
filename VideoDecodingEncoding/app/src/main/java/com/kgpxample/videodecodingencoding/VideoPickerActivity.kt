package com.kgpxample.videodecodingencoding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.database.getLongOrNull
import androidx.recyclerview.widget.GridLayoutManager
import com.kgpxample.videodecodingencoding.databinding.ActivityVideoPickerBinding

class VideoPickerActivity : AppCompatActivity() {

    companion object {
        const val RESULT_CODE = 1234
        const val KEY_RESULT_PATH = "path"
        const val KEY_RESULT_URI = "uri"
    }

    private lateinit var binding: ActivityVideoPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()
    }

    private fun initRecyclerView() {
        val adapter = PickerAdapter().apply {
            items = loadMedia()
            listener = { videoItem ->
                val uri = videoItem.uri
                val path = videoItem.filePath

                Log.d("kgpp", "uri " + uri)
                Log.d("kgpp", "path " + path)

                val intent = Intent().apply {
                    putExtra(KEY_RESULT_PATH, path)
                    putExtra(KEY_RESULT_URI, uri)
                }
                setResult(RESULT_CODE, intent)

                finish()
            }
        }
        binding.mediaListRecyclerView.adapter = adapter
        binding.mediaListRecyclerView.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false)
    }

    private fun loadMedia(): List<VideoItem> {
        val resultMedia = ArrayList<VideoItem>()
        val queryUri = MediaStore.Files.getContentUri("external")
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.Video.Media.DATA)
        val cursor = contentResolver.query(queryUri, projection, selection, null, null) ?: return emptyList()
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

        while (cursor.moveToNext()) {
            val videoId = cursor.getLongOrNull(idIndex)
            val videoUri = Uri.withAppendedPath(queryUri, videoId.toString())
            val fileName = cursor.getString(dataIndex)
            val videoItem = VideoItem(videoUri, fileName)
            resultMedia.add(videoItem)
        }
        cursor.close()

        return resultMedia
    }
}