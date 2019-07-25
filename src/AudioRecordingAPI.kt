import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.sound.sampled.*

class AudioRecordingAPI : AutoCloseable {

    private val line: TargetDataLine

    init {
        val format = AudioFormat(16000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            error("No supported audio recording device")
        }

        line = AudioSystem.getLine(info) as TargetDataLine
    }

    var recording = false

    var recordingStartTime: Instant? = null

    var currentRecordingThread: Thread? = null

    var clip: Clip? = null

    @Volatile
    var save: Boolean = true

    fun stopRecording() {
        stopPlaying()
        if (!recording) {
            return
        }

        save = false
        line.stop()
        recording = false
        recordingStartTime = null
    }

    fun startRecording(filePrefix: String) {
        stopPlaying()
        if (recording) {
            save = true
            line.stop()
        }

        recording = true
        recordingStartTime = Instant.now()

        currentRecordingThread = Thread {
            val tmpFile = Files.createTempFile("audio", ".wav")

            try {
                line.open()
                line.start()

                val ais = AudioInputStream(line)

                // Thread will block here until line.stop is called (or an exception)
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tmpFile.toFile())

                if (save) {
                    val path = Paths.get("$filePrefix.wav")
                    Files.copy(tmpFile, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.delete(tmpFile)
            }
        }

        currentRecordingThread!!.start()
    }

    override fun close() {
        line.close()
        clip?.close()
    }

    fun playLastFile(filePrefix: String) {
        val ais = AudioSystem.getAudioInputStream(Paths.get("$filePrefix.wav").toFile().inputStream())

        clip = AudioSystem.getClip()

        clip!!.open(ais)

        clip!!.start()
    }

    private fun stopPlaying() {
        clip?.let {
            it.stop()
            it.close()
        }
        clip = null
    }

    fun exists(filePrefix: String): Boolean {
        return Files.exists(Paths.get("$filePrefix.wav"))
    }
}