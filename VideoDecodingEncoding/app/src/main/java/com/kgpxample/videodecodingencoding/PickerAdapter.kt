package com.kgpxample.videodecodingencoding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kgpxample.videodecodingencoding.databinding.VideoItemBinding

class PickerAdapter : RecyclerView.Adapter<PickerAdapter.VideoViewHolder>() {

    var items = listOf<VideoItem>()

    var listener: ((VideoItem) -> Unit)? = null

    inner class VideoViewHolder(private val binding: VideoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                listener?.invoke(items[adapterPosition])
            }
        }

        fun bind(videoPath: VideoItem) {
            Glide.with(itemView.context)
                .load(videoPath.uri)
                .thumbnail(0.3f)
                .into(binding.videoItem)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = VideoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }
}