import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.googlecode.lanterna.SGR
import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.input.KeyStroke
import com.googlecode.lanterna.input.KeyType
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
             |
             |Keys:
             |  <space> - Start/Save (and show next)
             |  q - Quit (don't save)
             |  r - Retry (clears buffer and starts this segment over)
             |  p - Plays the last saved recording
             |  s - Stops recording (without saving)
             |  <left arrow> - Previous phrase
             |  <right arrow> - Next phrase
             |  
             |Version:
             |  1.1.1""".trimMargin()
) {
    private val prompts: File by argument(help = "A csv of prompt text (col 1)").file(
        exists = true,
        fileOkay = true,
        readable = true
    )

    private val skipExisting: Boolean by option(help = "Allows you to continue if you have previously stopped by skipping inputs for which the output already exists").flag()

    private var currentMessage = "Press space to start"

    private val promptValues: List<String> by lazy {
        val parser = CSVParser(
            prompts.bufferedReader(), CSVFormat.DEFAULT.withSkipHeaderRecord(true)
                .withFirstRecordAsHeader()
        )

        parser.records.map {
            it[0]
        }
    }

    private var showFullKeymap: Boolean = false

    private var index = -1

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

                        if (handleKeyStroke(it, api)) {
                            return // Exit loop
                        }
                    }

                    if (dirty || api.recording) {
                        screen.clear()
                        if (showFullKeymap) {
                            screen.showFullKeymap()
                        } else {
                            screen.showKeymap()
                        }
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

    /**
     * Handles all of the key logic and returns true if the program should exit
     */
    private fun handleKeyStroke(keyStroke: KeyStroke, api: AudioRecordingAPI): Boolean {
        when (keyStroke.character) {
            ' ' -> recordNext(api)
            'r' -> {
                api.stopRecording()
                api.startRecording(currentMessage.replace(' ', '_'))
            }
            'p' -> {
                currentMessage = if (index - 1 < 0) {
                    "No previous recordings"
                } else {
                    // We should always be able to play previous if we aren't at the beginning because
                    // the index should never progress more than 1 past the end
                    api.playLastFile(promptValues[index - 1].replace(' ', '_'))
                    "Playing..."
                }
            }
            's' -> {
                api.stopRecording()
                currentMessage = "Stopped"
            }
            'h' -> showFullKeymap = !showFullKeymap // invert showFullKeymap
            'q' -> return true
            else -> {
                when (keyStroke.keyType) {
                    KeyType.ArrowLeft -> {
                        api.stopRecording()
                        index -= 2
                        recordNext(api)
                    }
                    KeyType.ArrowRight -> {
                        api.stopRecording()
                        recordNext(api)
                    }
                    else -> {
                    } // Do nothing
                }
            }
        }

        return false
    }

    /**
     * Logic for the next thing to record
     */
    private fun recordNext(api: AudioRecordingAPI) {
        index++

        val validIndex = if (index >= promptValues.size) {
            currentMessage = "You have reached the end!"
            index = promptValues.size // Never let it progress more than 1 past the end
            false
        } else if (index <= -1) {
            currentMessage = "You are at the beginning"
            index = -1 // Never let it progress more than 1 before the beginning
            false
        } else {
            currentMessage = promptValues[index]
            true
        }

        if (api.recording || validIndex) {
            api.startRecording(currentMessage.replace(' ', '_'))
        }
        if (!validIndex) {
            api.stopRecording()
        }
    }

    private fun TerminalScreen.showCenteredMessage() {
        newTextGraphics().putString(
            TerminalPosition(terminalSize.columns / 2 - currentMessage.length / 2, terminalSize.rows / 2),
            currentMessage
        )
    }

    private fun TerminalScreen.showKeymap() {
        val message =
            "q - Quit, <space> - Save and continue, h - Toggle full keymap text"
        newTextGraphics().putString(TerminalPosition(0, terminalSize.rows - 1), message)
    }

    private fun TerminalScreen.showFullKeymap() {
        var row = 1
        newTextGraphics()
            .putString(TerminalPosition(0, row++), "q            - Quit")
            .putString(TerminalPosition(0, row++), "<space>      - Save and continue")
            .putString(TerminalPosition(0, row++), "h            - Toggle this text")
            .putString(TerminalPosition(0, row++), "r            - Retry")
            .putString(TerminalPosition(0, row++), "p            - Play last recording")
            .putString(TerminalPosition(0, row++), "s            - Stop recording")
            .putString(TerminalPosition(0, row++), "<left arrow> - Previous phrase")
            .putString(TerminalPosition(0, row++), "right arrow> - Next phrase")
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
