package korlibs.audio.sound

//import mystdio.*
import cnames.structs.OpaqueAudioQueue
import kotlinx.cinterop.Arena
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioQueueAllocateBuffer
import platform.AudioToolbox.AudioQueueBuffer
import platform.AudioToolbox.AudioQueueBufferRef
import platform.AudioToolbox.AudioQueueDispose
import platform.AudioToolbox.AudioQueueEnqueueBuffer
import platform.AudioToolbox.AudioQueueNewOutput
import platform.AudioToolbox.AudioQueueRef
import platform.AudioToolbox.AudioQueueStart
import platform.AudioToolbox.kAudioFormatUnsupportedDataFormatError
import platform.AudioToolbox.kAudioQueueErr_BufferEmpty
import platform.AudioToolbox.kAudioQueueErr_BufferInQueue
import platform.AudioToolbox.kAudioQueueErr_CannotStart
import platform.AudioToolbox.kAudioQueueErr_CodecNotFound
import platform.AudioToolbox.kAudioQueueErr_DisposalPending
import platform.AudioToolbox.kAudioQueueErr_EnqueueDuringReset
import platform.AudioToolbox.kAudioQueueErr_InvalidBuffer
import platform.AudioToolbox.kAudioQueueErr_InvalidCodecAccess
import platform.AudioToolbox.kAudioQueueErr_InvalidDevice
import platform.AudioToolbox.kAudioQueueErr_InvalidOfflineMode
import platform.AudioToolbox.kAudioQueueErr_InvalidParameter
import platform.AudioToolbox.kAudioQueueErr_InvalidProperty
import platform.AudioToolbox.kAudioQueueErr_InvalidPropertySize
import platform.AudioToolbox.kAudioQueueErr_InvalidPropertyValue
import platform.AudioToolbox.kAudioQueueErr_InvalidQueueType
import platform.AudioToolbox.kAudioQueueErr_InvalidRunState
import platform.AudioToolbox.kAudioQueueErr_Permissions
import platform.AudioToolbox.kAudioQueueErr_PrimeTimedOut
import platform.AudioToolbox.kAudioQueueErr_QueueInvalidated
import platform.AudioToolbox.kAudioQueueErr_RecordUnderrun
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsPacked
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreAudioTypes.kLinearPCMFormatFlagIsSignedInteger
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.kCFRunLoopCommonModes
import platform.darwin.OSStatus
import kotlin.coroutines.CoroutineContext

actual val nativeSoundProvider: NativeSoundProvider get() = CORE_AUDIO_NATIVE_SOUND_PROVIDER
expect fun appleInitAudio()

@ThreadLocal
val CORE_AUDIO_NATIVE_SOUND_PROVIDER: CoreAudioNativeSoundProvider by lazy { CoreAudioNativeSoundProvider() }

class CoreAudioNativeSoundProvider : NativeSoundProvider() {
    init {
        appleInitAudio()
    }

    //override suspend fun createSound(data: ByteArray, streaming: Boolean, props: AudioDecodingProps): NativeSound = AVFoundationNativeSoundNoStream(CoroutineScope(coroutineContext), audioFormats.decode(data))

    override fun createPlatformAudioOutput(coroutineContext: CoroutineContext, freq: Int): PlatformAudioOutput = CoreAudioPlatformAudioOutput(coroutineContext, freq)
}

class CoreAudioPlatformAudioOutput(
    coroutineContext: CoroutineContext,
    freq: Int
) : DequeBasedPlatformAudioOutput(coroutineContext, freq) {
    val generator = CoreAudioGenerator(freq, nchannels, coroutineContext = coroutineContext) { data, dataSize ->
        val temp = ShortArray(dataSize / nchannels)
        for (m in 0 until nchannels) {
            readShorts(m, temp)
            for (n in 0 until dataSize / nchannels) {
                data[n * nchannels + m] = temp[n]
            }
        }
        //for (n in 0 until dataSize / nchannels) {
        //    for (m in 0 until nchannels) {
        //        data[n * nchannels + m] = readShort(m)
        //    }
        //}
    }
    override fun start() {
        generator.start()
    }
    override fun stop() {
        generator.dispose()
    }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////

// https://github.com/spurious/SDL-mirror/blob/master/src/audio/coreaudio/SDL_coreaudio.m
// https://gist.github.com/soywiz/b4f91d1e99e4ff61ea674b777c31f289
interface MyCoreAudioOutputCallback {
    fun generateOutput(data: CPointer<ShortVar>, dataSize: Int)
}

private fun coreAudioOutputCallback(custom_data: COpaquePointer?, queue: AudioQueueRef?, buffer: AudioQueueBufferRef?) {
    initRuntimeIfNeeded()
    val output = custom_data?.asStableRef<MyCoreAudioOutputCallback>() ?: return println("outputCallback null[0]")
    val buf = buffer?.pointed ?: return println("outputCallback null[1]")
    val dat = buf.mAudioDataByteSize.toInt() / Short.SIZE_BYTES
    val shortBuf = buf.mAudioData?.reinterpret<ShortVar>() ?: return println("outputCallback null[2]")
    output.get().generateOutput(shortBuf, dat)
    AudioQueueEnqueueBuffer(queue, buffer, 0.convert(), null).checkError("AudioQueueEnqueueBuffer")
}
fun osStatusToString(status: OSStatus): String = when (status) {
    kAudioQueueErr_InvalidBuffer -> "InvalidBuffer"
    kAudioQueueErr_BufferEmpty -> "BufferEmpty"
    kAudioQueueErr_DisposalPending -> "DisposalPending"
    kAudioQueueErr_InvalidProperty -> "InvalidProperty"
    kAudioQueueErr_InvalidPropertySize -> "InvalidPropertySize"
    kAudioQueueErr_InvalidParameter -> "InvalidParameter"
    kAudioQueueErr_CannotStart -> "CannotStart"
    kAudioQueueErr_InvalidDevice -> "InvalidDevice"
    kAudioQueueErr_BufferInQueue -> "BufferInQueue"
    kAudioQueueErr_InvalidRunState -> "InvalidRunState"
    kAudioQueueErr_InvalidQueueType -> "InvalidQueueType"
    kAudioQueueErr_Permissions -> "Permissions"
    kAudioQueueErr_InvalidPropertyValue -> "InvalidPropertyValue"
    kAudioQueueErr_PrimeTimedOut -> "PrimeTimedOut"
    kAudioQueueErr_CodecNotFound -> "CodecNotFound"
    kAudioQueueErr_InvalidCodecAccess -> "InvalidCodecAccess"
    kAudioQueueErr_QueueInvalidated -> "QueueInvalidated"
    kAudioQueueErr_RecordUnderrun -> "RecordUnderrun"
    kAudioQueueErr_EnqueueDuringReset -> "EnqueueDuringReset"
    kAudioQueueErr_InvalidOfflineMode -> "InvalidOfflineMode"
    kAudioFormatUnsupportedDataFormatError -> "UnsupportedDataFormatError"
    else -> "Unknown$status"
}

private fun OSStatus.checkError(name: String): OSStatus {
    if (this != 0) println("ERROR: $name ($this)(${osStatusToString(this)})")
    return this
}

class CoreAudioGenerator(
    val sampleRate: Int,
    val nchannels: Int,
    val numBuffers: Int = 3,
    val bufferSize: Int = 4096,
    val coroutineContext: CoroutineContext,
    val generatorCore: CoreAudioGenerator.(data: CPointer<ShortVar>, dataSize: Int) -> Unit
) : MyCoreAudioOutputCallback {
    val arena = Arena()
    var queue: CPointerVarOf<CPointer<OpaqueAudioQueue>>? = null
    var buffers: CPointer<CPointerVarOf<CPointer<AudioQueueBuffer>>>? = null
    var running = false

    var thisStableRef: StableRef<MyCoreAudioOutputCallback>? = null

    fun start() {
        if (running) return
        running = true

        queue = arena.alloc()
        buffers = arena.allocArray(numBuffers)
        thisStableRef = StableRef.create(this)
        memScoped {
            val format = alloc<AudioStreamBasicDescription>()
            format.mSampleRate = sampleRate.toDouble()
            format.mFormatID = kAudioFormatLinearPCM;
            format.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger or kAudioFormatFlagIsPacked;
            format.mBitsPerChannel = (8 * Short.SIZE_BYTES).convert();
            format.mChannelsPerFrame = nchannels.convert()
            format.mBytesPerFrame = (Short.SIZE_BYTES * format.mChannelsPerFrame.toInt()).convert()
            format.mFramesPerPacket = 1.convert()
            format.mBytesPerPacket = format.mBytesPerFrame * format.mFramesPerPacket
            format.mReserved = 0.convert()

            AudioQueueNewOutput(
                format.ptr, staticCFunction(::coreAudioOutputCallback), thisStableRef!!.asCPointer(),
                CFRunLoopGetCurrent(), kCFRunLoopCommonModes, 0.convert(), queue!!.ptr
            ).also {
                if (it != 0) error("Error in AudioQueueNewOutput")
            }
        }

        for (n in 0 until numBuffers) {
            AudioQueueAllocateBuffer(queue!!.value, bufferSize.convert(), buffers + n).checkError("AudioQueueAllocateBuffer")
            buffers!![n]!!.pointed.mAudioDataByteSize = bufferSize.convert()
            //println(buffers[n])
            //AudioQueueEnqueueBuffer(queue.value, buffers[n], 0.convert(), null).also { println("AudioQueueEnqueueBuffer: $it") }

            //callback()
            coreAudioOutputCallback(thisStableRef!!.asCPointer(), queue!!.value, buffers!![n])
        }

        //println("AudioQueueStart")
        AudioQueueStart(queue!!.value, null)
    }

    fun dispose() {
        if (running) {
            if (queue != null) {
                //println("AudioQueueFlush/AudioQueueStop/AudioQueueDispose")
                //AudioQueueFlush(queue!!.value).checkError("AudioQueueFlush")
                //AudioQueueStop(queue!!.value, false).checkError("AudioQueueStop")
                //for (n in 0 until NUM_BUFFERS) AudioQueueFreeBuffer(queue.value, buffers[n]).also { println("AudioQueueFreeBuffer: $it") }
                AudioQueueDispose(queue!!.value, false).checkError("AudioQueueDispose")
            }
            queue = null
            thisStableRef?.dispose()
            thisStableRef = null
            arena.clear()
            running = false
        }
    }

    override fun generateOutput(data: CPointer<ShortVar>, dataSize: Int) = generatorCore(this, data, dataSize)
}
