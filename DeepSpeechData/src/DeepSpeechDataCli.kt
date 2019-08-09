import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

/**
 * @author Tyler Scott
 */

class DeepSpeechDataCli : CliktCommand(
  help = "A tool for building DeepSpeech data sets"
) {

  private val inputDir: Path by argument(
    help = "A directory containing the audio sources"
  ).path(
    exists = true,
    folderOkay = true
  )

  private val outputDir: Path by argument(
    help = "Where to put the output"
  ).path(
    exists = true,
    folderOkay = true
  ).default(Paths.get("."))

  private val overwrite: Boolean by option(
    "-o",
    help = "Overwrite the output files regardless of if the destination exists or not. Defaults to false"
  ).flag()

  override fun run() {
    val fileNameFilter = "^[a-z_.]+\\.wav$".toRegex()
    Files.list(inputDir)
      .collect(Collectors.toList())
      .filter { it.fileName.toString().matches(fileNameFilter) }
      .map(::TestCase)
      .associateBy { it.path }
      .let {
        // Convert every value (which is a testcase) to its augmented form (We don't need the key)
        it.values
          .mapIndexed { index, value ->
            print("Processing: ${value.path} | $index/${it.values.size}                  \r")
            arrayOf(listOf(value), value.augment(overwrite))
          }
      }
      .let {
        it.writeCases(1, outputDir.resolve("test.csv"))
        it.writeCases(0, outputDir.resolve("train.csv"))
      }
  }
}

private fun List<Array<List<TestCase>>>.writeCases(
  selector: Int,
  outputFile: Path
) {

  CSVPrinter(outputFile.toFile().bufferedWriter(), CSVFormat.DEFAULT).use { printer ->
    printer.printRecord("wav_filename", "wav_filesize", "transcript")
    forEach { dataSets ->
      dataSets[selector].forEach { dataSet ->
        printer.printRecord(outputFile.parent.relativize(dataSet.path), dataSet.size, dataSet.phrase)
      }
    }
  }
}

private class TestCase(val path: Path) {
  val phrase: String
    get() = wordRegex.matchEntire(path.fileName.toString())!!.groups[1]!!.value
      .substringBefore('-')
      .substringBefore('.')
      .replace('_', ' ')

  val size: Long
    get() = path.toFile().length()

  fun augment(overwrite: Boolean): List<TestCase> {
    return listOf(
      createEchoVersion(overwrite), // longish delay
      createReverbVersion(overwrite), // less than .1 delay
      createPitchShift(overwrite, 0.5),
      createPitchShift(overwrite, 0.75),
      createPitchShift(overwrite, 1.5),
      createPitchShift(overwrite, 2.0)
    )
  }

  private fun createPitchShift(overwrite: Boolean, factor: Double): TestCase = ffmpegFilter(
    overwrite,
    "rubberband=pitch=$factor",
    pathWithSuffix("pitch$factor")
  )

  private fun createEchoVersion(overwrite: Boolean): TestCase = ffmpegFilter(
    overwrite,
    "aecho=0.6:0.3:30:0.5",
    pathWithSuffix("echo")
  )

  private fun createReverbVersion(overwrite: Boolean): TestCase = ffmpegFilter(
    overwrite,
    "aecho=0.6:0.3:5:0.5",
    pathWithSuffix("reverb")
  )

  private fun ffmpegFilter(overwrite: Boolean, filter: String, targetPath: Path): TestCase {
    if (overwrite || !Files.exists(targetPath)) {
      val proc = ProcessBuilder("ffmpeg", "-y", "-i", path.toString(), "-af", filter, targetPath.toString()).start()
      proc.waitFor()
    }

    return TestCase(targetPath)
  }

  private fun pathWithSuffix(suffix: String): Path {
    return path.parent.resolve(path.fileName.toString().replace(".wav", "-$suffix.wav"))
  }

  companion object {
    private val wordRegex = "([a-z_.]+)(-.+)?\\.wav".toRegex()
  }
}

fun main(args: Array<String>) = DeepSpeechDataCli().main(args)
