import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.path
import java.io.File
import java.nio.file.Path

/**
 * @author Tyler Scott
 */
class CyfeCountCli : CliktCommand(
    printHelpOnEmptyArgs = true,
    help = "A commandline utility for reading access logs and creating an output format that is compatible with WebGL Globe"
) {

    private val googleAccessLogDirectory: Path by argument(
        name = "Google_Access_Logs",
        help = "The location of the google access logs"
    ).path(
        exists = true,
        folderOkay = true,
        readable = true
    )

    private val whitelist: File? by option(
        help = "A file that contains a list of ips to ignore"
    ).file(
        exists = true,
        fileOkay = true,
        readable = true
    )

    private val output: File? by argument(
        help = "A destination for the output. Defaults to stdout"
    ).file().optional()


    override fun run() {
        val globeOutput = CyfeOutput(whitelist?.readLines()?.toHashSet() ?: emptySet())

        readGoogleAccessLogs(googleAccessLogDirectory, globeOutput::collectSome)

        globeOutput.writeResult(output?.outputStream() ?: System.out)
    }
}

fun main(args: Array<String>) = CyfeCountCli().main(args)
