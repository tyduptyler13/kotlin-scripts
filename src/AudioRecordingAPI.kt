import java.nio.file.Files
import java.nio.file.Paths
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

    fun stopRecording() {
        stopPlaying()
        line.stop()
        currentRecordingThread!!.join()
    }

    fun startRecording(filePrefix: String) {
        stopPlaying()
        if (recording) {
            line.stop() // The thread will continue to exist until it finishes writing to that file
        }

        recording = true
        recordingStartTime = Instant.now()

        currentRecordingThread = Thread {
            line.open()
            line.start()

            val ais = AudioInputStream(line)

            val path = Paths.get("$filePrefix.wav")
            Files.deleteIfExists(path)

            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, path.toFile())
        }

        currentRecordingThread!!.start()
    }

    override fun close() {
        line.close()
        clip?.close()
    }

    fun deleteLastFile(filePrefix: String) {
        stopPlaying()
        Files.delete(Paths.get("$filePrefix.wav"))
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