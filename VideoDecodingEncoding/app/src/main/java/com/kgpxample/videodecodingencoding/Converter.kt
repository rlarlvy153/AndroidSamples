package com.kgpxample.videodecodingencoding

import android.content.Context
import android.graphics.ImageFormat
import android.media.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class Converter(context: Context, surface: Surface, val uri: Uri, val sourcePath: String, val outputDir: String) {
    val outputFilePath = outputDir + "/result.mp4"

    private val extractor = MediaExtractor()
    private val videoExtractor = MediaExtractor()
    private val audioExtractor = MediaExtractor()
    private lateinit var mediaMuxer: MediaMuxer
//    private lateinit var videoDecoder: MediaCodec
//    private lateinit var audioDecoder: MediaCodec
//    private lateinit var videoEncoder: MediaCodec
//    private lateinit var audioEncoder: MediaCodec

    val onlyDecoding = false

    //    private val videoThread = DecodingThread()
//    private val videoThread: Thread = if (onlyDecoding) DecodingThread() else ProcessingThread()
    private val videoThread = ProcessingThread()
    private val audioThread = ProcessingThread()
    private var videoMuxerIndex = -1
    private var audioMuxerIndex = -1

    companion object {
        const val VIDEO_COLOR_FORMAT = 0x7F000789
        const val VIDEO_BIT_RATE = 4000000
        const val VIDEO_FRAME_RATE = 30
        const val VIDEO_I_FRAME_INTERVAL = 5
        const val VIDEO_WIDTH = 720
        const val VIDEO_HEIGHT = 1280

        const val AUDIO_SAMPLING_RATE = 48000
        const val AUDIO_CHANNEL_COUNT = 2
        const val AUDIO_BITRATE = 320000
    }

    private val outputVideoFormat = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC,
        VIDEO_WIDTH, VIDEO_HEIGHT
    ).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, VIDEO_COLOR_FORMAT)
        setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
        setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_I_FRAME_INTERVAL)
        setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
    }

    private val outputAudioFormat = MediaFormat.createAudioFormat(
        MediaFormat.MIMETYPE_AUDIO_AAC,
        AUDIO_SAMPLING_RATE,
        AUDIO_CHANNEL_COUNT
    ).apply {
        setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)

    }

    init {
        mediaMuxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mediaMuxer.setOrientationHint(90)

        extractor.setDataSource(sourcePath)
        videoExtractor.setDataSource(sourcePath)
        audioExtractor.setDataSource(sourcePath)

        val trackCount = extractor.trackCount
        for (track in 0 until trackCount) {
            val trackFormat = extractor.getTrackFormat(track)
            val mimeType = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            Log.d("kgpp", "mime " + mimeType)


            if (mimeType.startsWith("video")) {
                val duration = trackFormat.getLong(MediaFormat.KEY_DURATION)
                var ss: Surface? = null
                trackFormat.setInteger(MediaFormat.KEY_ROTATION, 0)

                val videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
                    outputVideoFormat.setLong(MediaFormat.KEY_DURATION, duration)
                    val complexityLevel = it.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    val range = complexityLevel.encoderCapabilities.complexityRange.upper
                    outputVideoFormat.setInteger(MediaFormat.KEY_COMPLEXITY, range)
                    outputVideoFormat.setInteger(MediaFormat.KEY_WIDTH, trackFormat.getInteger(MediaFormat.KEY_WIDTH))
                    outputVideoFormat.setInteger(MediaFormat.KEY_HEIGHT, trackFormat.getInteger(MediaFormat.KEY_HEIGHT))
                    if(trackFormat.containsKey(MediaFormat.KEY_ROTATION)){
                        outputVideoFormat.setInteger(MediaFormat.KEY_ROTATION, trackFormat.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    it.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    ss = it.createInputSurface()
                    Log.d("kgpp", "video encoder " + it.name)
                    it.start()

                }

                val videoDecoder = MediaCodec.createDecoderByType(mimeType).also {
                    if(trackFormat.containsKey(MediaFormat.KEY_ROTATION)){
                        Log.d("kgpp","rotation " + trackFormat.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    it.configure(trackFormat, ss, null, 0)
                    Log.d("kgpp", "video decoder " + it.name)
                    it.start()
                }
                videoMuxerIndex = mediaMuxer.addTrack(outputVideoFormat)
                videoExtractor.selectTrack(track)
                if (videoThread is ProcessingThread) {
                    (videoThread as ProcessingThread).init(videoDecoder, videoEncoder, mediaMuxer, videoExtractor, false)
                } else {
                    (videoThread as DecodingThread).init(videoDecoder, videoEncoder, mediaMuxer, videoExtractor, false)
                }

            } else if (mimeType.startsWith("audio")) {
                if (onlyDecoding)
                    continue
                var ss: Surface? = null

                val channel = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                Log.d("kgpp", "channel " + channel)

                val bit = trackFormat.getInteger(MediaFormat.KEY_BIT_RATE)
                Log.d("kgpp", "bit " + bit)

                val samplingRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)

                val audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).also {
                    outputAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channel)
                    outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bit)
                    outputAudioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, samplingRate)
                    it.configure(outputAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    Log.d("kgpp", "audio encoder " + it.name)
                    it.start()
                }

                val audioDecoder = MediaCodec.createDecoderByType(mimeType).also {
                    it.configure(trackFormat, null, null, 0)
                    Log.d("kgpp", "audio decoder " + it.name)
                    it.start()
                }

                audioMuxerIndex = mediaMuxer.addTrack(outputAudioFormat)
                audioExtractor.selectTrack(track)
                audioThread.init(audioDecoder, audioEncoder, mediaMuxer, audioExtractor, true)
            }
        }
    }


    fun start() {
        if (onlyDecoding) {
            Thread {

//                videoDecoder.start()

//                videoThread.start()

//                videoThread.join()

            }.start()
        } else {
            Thread {


                mediaMuxer.start()

                videoThread.start()
                audioThread.start()

                videoThread.join()
                audioThread.join()

                Log.d("kgpp", "muxer stop")
                mediaMuxer.stop()
                mediaMuxer.release()

            }.start()
        }


    }

    var isStart = AtomicBoolean(false)

    inner class ProcessingThread() : Thread() {
        private val timeout: Long = 15000
        private val waitTime = 100L
        private lateinit var decoder: MediaCodec
        private lateinit var encoder: MediaCodec
        private lateinit var muxer: MediaMuxer
        private lateinit var extractor: MediaExtractor
        private var trackIndex: Int = -1
        private var isAudio = false
        private var readDone = AtomicBoolean(false)

        fun init(decoder: MediaCodec, encoder: MediaCodec, muxer: MediaMuxer, extractor: MediaExtractor, isAudio: Boolean) {
            this.decoder = decoder
            this.encoder = encoder
            this.muxer = muxer
            this.extractor = extractor
            this.trackIndex = extractor.sampleTrackIndex
            this.isAudio = isAudio
        }

        override fun run() {
            var endDecoding = false
            var endEncoding = false

            Log.d("kgpp", "start thread isAudio : " + isAudio)

            val decodeThread = Thread {

                while (!endDecoding) {
                    //Log.d("kgpp", "decoding")
                    val decInputBufferIndex = decoder.dequeueInputBuffer(timeout)
                    if (decInputBufferIndex < 0) {
                        Thread.sleep(waitTime)
                        continue
                    }
                    val inputBuffer = decoder.getInputBuffer(decInputBufferIndex)!!

                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) { //there is no sampled data or end of stream
                        decoder.queueInputBuffer(decInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        endDecoding = true
                        readDone.set(true)

                        while (true) {
                            Thread.sleep(waitTime)
                            val incInputBufferIndex = encoder.dequeueInputBuffer(timeout)
                            if (incInputBufferIndex >= 0) {
                                Log.d("kgpp", "wait free encoder input buffer isAudio:" + isAudio + " " + incInputBufferIndex)
                                encoder.queueInputBuffer(incInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                break
                            }
                        }
                        break
                    } else {
                        decoder.queueInputBuffer(decInputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                    }
                    val decOutputBufferInfo = MediaCodec.BufferInfo()

                    var decOutputBufferIndex = decoder.dequeueOutputBuffer(decOutputBufferInfo, timeout)
                    if (decOutputBufferIndex < 0) {
                        Thread.sleep(waitTime)
                        continue
                    }
                    val outputBuffer = decoder.getOutputBuffer(decOutputBufferIndex)!!

                    val decodedData = ByteArray(decOutputBufferInfo.size)
                    outputBuffer.get(decodedData, 0, decOutputBufferInfo.size)
                    decoder.releaseOutputBuffer(decOutputBufferIndex, !isAudio)


                    var remain = decOutputBufferInfo.size
                    var offset = 0
                    var copySize = 0

                    var stop = false
                    while (remain > 0 && isAudio) {
                        val incInputBufferIndex = encoder.dequeueInputBuffer(timeout)

                        if (incInputBufferIndex < 0) {

                            Thread.sleep(waitTime)
                            Log.d("kgpp", "1 wait free encoder input buffer isAudio:" + isAudio + " " + incInputBufferIndex)
                            continue
                        }


                        val encInputBuffer = encoder.getInputBuffer(incInputBufferIndex)!!

                        if (encInputBuffer.capacity() < remain) {
                            encInputBuffer.put(decodedData, offset, encInputBuffer.capacity())
                            offset += encInputBuffer.capacity()
                            remain -= encInputBuffer.capacity()
                            copySize = encInputBuffer.capacity()
                        } else {
                            encInputBuffer.put(decodedData, offset, remain)
                            copySize = remain
                            remain -= encInputBuffer.capacity()
                        }
                        encoder.queueInputBuffer(incInputBufferIndex, 0, copySize, decOutputBufferInfo.presentationTimeUs, 0)
                    }
                    if (!extractor.advance()) {
                        endDecoding = true
                        readDone.set(true)

                        if (isAudio) {
                            var tryAgain = 0
                            while (tryAgain < 10) {
                                val incInputBufferIndex = encoder.dequeueInputBuffer(timeout)
                                if (incInputBufferIndex >= 0) {
                                    tryAgain += 1
                                    Thread.sleep(waitTime)
                                    Log.d("kgpp", "2 wait free encoder input buffer isAudio:" + isAudio + " " + incInputBufferIndex)
                                    encoder.queueInputBuffer(incInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    break
                                }

                            }
                        }
                    }
                }

                Log.d("kgpp", "decode done")
            }

            val encodeThread = Thread {
                while (!endEncoding) {

                    val encOutputBufferInfo = MediaCodec.BufferInfo()
                    var encOutputBufferIndex = encoder.dequeueOutputBuffer(encOutputBufferInfo, timeout)
                    if (encOutputBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                        endEncoding = true
                        Log.d("kgpp", "encoder read done stop end of stream")
                        break
                    }

                    if (encOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER && readDone.get()) {
                        endEncoding = true
                        Log.d("kgpp", "encoder read done stop index done")
                        break
                    }

                    if (encOutputBufferIndex >= 0) {
                        val encOutputBuffer = encoder.getOutputBuffer(encOutputBufferIndex)
                        if (encOutputBuffer != null) {
//                            Log.d("kgpp","encoding size " + encOutputBufferInfo.size)
                            muxer.writeSampleData(if (isAudio) audioMuxerIndex else videoMuxerIndex, encOutputBuffer, encOutputBufferInfo)
                            encoder.releaseOutputBuffer(encOutputBufferIndex, false)
                        }
                    }
                }
                Log.d("kgpp", "encode done")
            }


            decodeThread.start()
            encodeThread.start()

            decodeThread.join()
            encodeThread.join()

            Log.d("kgpp", "done finish isAudio : " + isAudio)


            extractor.release()
            encoder.stop()
            encoder.release()
            decoder.stop()
            decoder.release()

        }
    }

    fun decodeYUV420SP(
        rgba: IntArray, yuv420sp: ByteArray, width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var j = 0
        var yp = 0
        while (j < height) {
            var uvp = frameSize + (j shr 1) * width
            var u = 0
            var v = 0
            var i = 0
            while (i < width) {
                var y = (0xff and yuv420sp[yp].toInt()) - 16
                if (y < 0) y = 0
                if (i and 1 == 0) {
                    v = (0xff and yuv420sp[uvp++].toInt()) - 128
                    u = (0xff and yuv420sp[uvp++].toInt()) - 128
                }
                val y1192 = 1192 * y
                var r = y1192 + 1634 * v
                var g = y1192 - 833 * v - 400 * u
                var b = y1192 + 2066 * u
                if (r < 0) r = 0 else if (r > 262143) r = 262143
                if (g < 0) g = 0 else if (g > 262143) g = 262143
                if (b < 0) b = 0 else if (b > 262143) b = 262143

                // rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) &
                // 0xff00) | ((b >> 10) & 0xff);
                // rgba, divide 2^10 ( >> 10)
                rgba[yp] = (r shl 14 and -0x1000000 or (g shl 6 and 0xff0000)
                        or (b shr 2 or 0xff00))
                i++
                yp++
            }
            j++
        }
    }


    inner class DecodingThread() : Thread() {
        private val timeout: Long = -1
        private val waitTime = 50L
        private lateinit var decoder: MediaCodec
        private lateinit var encoder: MediaCodec
        private lateinit var muxer: MediaMuxer
        private lateinit var extractor: MediaExtractor
        private var trackIndex: Int = -1
        private var isAudio = false

        fun init(decoder: MediaCodec, encoder: MediaCodec, muxer: MediaMuxer, extractor: MediaExtractor, isAudio: Boolean) {
            this.decoder = decoder
            this.encoder = encoder
            this.extractor = extractor
            this.trackIndex = extractor.sampleTrackIndex
            this.isAudio = isAudio
        }

        override fun run() {
            var prev = 0L
            var end = false
            while (!end) {
                sleep(30)
                val decInputBufferIndex = decoder.dequeueInputBuffer(timeout)
                if (decInputBufferIndex < 0) {
                    sleep(waitTime)
                    continue
                }

                val inputBuffer = decoder.getInputBuffer(decInputBufferIndex)
                if (inputBuffer == null) {
                    sleep(waitTime)
                    continue
                }
                inputBuffer.clear()

                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                if (sampleSize < 0) { //there is no sampled data or end of stream
                    Log.d("kgpp", "done")

                    decoder.queueInputBuffer(decInputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    end = true
                    break
                } else {
                    decoder.queueInputBuffer(decInputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }

                Log.d("kgpp", "processing " + extractor.sampleTime)


                val decOutputBufferInfo = MediaCodec.BufferInfo()

                var decOutputBufferIndex = decoder.dequeueOutputBuffer(decOutputBufferInfo, timeout)
                while (decOutputBufferIndex < 0) {
                    sleep(waitTime)
                    decOutputBufferIndex = decoder.dequeueOutputBuffer(decOutputBufferInfo, timeout)
                    //TODO need to catch exception...
                }

                val outputBuffer = decoder.getOutputBuffer(decOutputBufferIndex)!!

                val decodedData = ByteArray(decOutputBufferInfo.size)
                outputBuffer.position(0)
                outputBuffer.get(decodedData, 0, decOutputBufferInfo.size)
                Log.d("kgpp", "output size " + decOutputBufferInfo.size)
                decoder.releaseOutputBuffer(decOutputBufferIndex, true)

                extractor.advance()
            }

            extractor.release()
            decoder.stop()
            decoder.release()

        }
    }
}