import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * @author Tyler Scott
 */
class AudioRecorderCli : CliktCommand(
    printHelpOnEmptyArgs = true,
    help = """This program is designed to record short pieces of audio very quickly. It will setup your microphone and wait to begin. From there, you press space to start and every time you press space it will save the last segment to disk with a numbered value. 

Keys:
  <space> - Start/Save
  q - Quit (don't save)
  r - Retry (clears buffer and starts this segment over)
  x - Deletes the last saved recording
  p - Plays the last saved recording
  s - Stops recording (without saving)
  
Version:
  1.0.1
"""
) {
    val prompts: File by argument(help = "A csv of prompt text (col 1)").file(
        exists = true,
        fileOkay = true,
        readable = true
    )

    val skipExisting: Boolean by option(help = "Allows you to continue if you have previously stopped by skipping inputs for which the output already exists").flag()

    var currentMessage = "Press space to start"

    val promptValues: List<String> by lazy {
        val parser = CSVParser(
            prompts.bufferedReader(), CSVFormat.DEFAULT.withSkipHeaderRecord(true)
                .withFirstRecordAsHeader()
        )

        parser.records.map {
            it[0]
        }
    }

    var index = -1

    override fun run() {
        AudioRecordingAPI().use { api ->
            if (skipExisting) {
                index = promptValues.indexOfFirst {
                    !api.exists(it.replace(' ', '_'))
                } - 1
            }

            runTerminalLoop(api)
        }
    }

    private fun runTerminalLoop(api: AudioRecordingAPI) {
        DefaultTerminalFactory().createTerminal().use { terminal ->
            TerminalScreen(terminal).use { screen ->
                screen.startScreen()
                screen.cursorPosition = null

                var dirty = true

                while (true) {
                    if (screen.terminalSize != terminal.terminalSize) {
                        dirty = true
                    }
                    screen.doResizeIfNecessary()

                    screen.pollInput()?.let {
                        dirty = true
                        screen.clear()
                        when (it.character) {
                            ' ' -> {
                                showNextMessage()
                                api.startRecording(currentMessage.replace(' ', '_'))
                            }
                            'r' -> {
                                val prefix = currentMessage.replace(' ', '_')
                                api.stopRecording()
                                api.deleteLastFile(prefix)
                                api.startRecording(prefix)
                            }
                            'x' -> {
                                api.stopRecording()
                                api.deleteLastFile(currentMessage.replace(' ', '_'))
                                currentMessage = "Deleted last recording"
                            }
                            'p' -> {
                                if (index - 1 < 0) {
                                    currentMessage = "No previous recordings"
                                }
                                api.playLastFile(promptValues[index - 1].replace(' ', '_'))
                                currentMessage = "Playing..."
                            }
                            's' -> {
                                api.stopRecording()
                                currentMessage = "Stopped"
                            }
                            'q' -> return
                        }
                    }

                    if (dirty || api.recording) {
                        screen.clear()
                        screen.showKeymap()
                        screen.showCenteredMessage()
                        if (api.recording) {
                            screen.showRecording()
                            screen.showRecordingTime(api.recordingStartTime!!)
                        }
                        screen.refresh()
                    }

                    Thread.sleep(17) // 60ish fps
                    dirty = false
                }
            }
        }
    }

    private fun showNextMessage() {
        index++
        currentMessage = promptValues[index]
    }


    private fun TerminalScreen.showCenteredMessage() {
        newTextGraphics().putString(
            TerminalPosition(terminalSize.columns / 2 - currentMessage.length / 2, terminalSize.rows / 2),
            currentMessage
        )
    }

    private fun TerminalScreen.showKeymap() {
        val message =
            "q - Quit, <space> - Save and continue, r - Retry, x - Delete last recording, p - Play last recording, s - Stop recording"
        newTextGraphics().putString(TerminalPosition(0, terminalSize.rows - 1), message)
    }

    private fun TerminalScreen.showRecording() {
        newTextGraphics()
            .setBackgroundColor(TextColor.ANSI.RED)
            .setForegroundColor(TextColor.ANSI.BLACK)
            .setModifiers(EnumSet.of(SGR.BLINK))
            .putString(TerminalPosition(0, 0), "Recording")
    }

    private fun TerminalScreen.showRecordingTime(startTime: Instant) {
        val duration = Duration.between(startTime, Instant.now())
        val timeString = "${duration.seconds}:${duration.nano}"
        newTextGraphics()
            .putString(TerminalPosition(terminalSize.columns - timeString.length, 0), timeString)
    }
}

fun main(args: Array<String>) = AudioRecorderCli().main(args)
