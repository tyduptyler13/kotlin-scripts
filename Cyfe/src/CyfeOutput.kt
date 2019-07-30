import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import com.fasterxml.jackson.dataformat.csv.CsvSchema.ColumnType.NUMBER
import com.fasterxml.jackson.dataformat.csv.CsvSchema.ColumnType.STRING
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * @author Tyler Scott
 */

private data class CyfeDataOutput(
    var zip: Int = 0,
    var rpm: Int = 0,
    var deb: Int = 0
)

class CyfeOutput(
    private val whitelist: Set<String>
) {
    private val analytics: SortedMap<LocalDate, CyfeDataOutput> = sortedMapOf()

    fun collectSome(entries: Sequence<GoogleAccessLogEntry>, dateTime: LocalDateTime) {

        val date = dateTime.toLocalDate()
        val data = analytics.getOrPut(date, { CyfeDataOutput() })

        entries
            .filter {
                it.cs_object.contains("fusionauth-app")
            }
            .filter {
                !whitelist.contains(it.c_ip)
            }
            .forEach {
                when (it.cs_object.takeLast(3)) {
                    "zip" -> data.zip++
                    "deb" -> data.deb++
                    "rpm" -> data.rpm++
                    else -> println("WARN: Unknown extension [$it]")
                }
            }
    }

    fun writeResult(output: OutputStream) {
        val mapper = CsvMapper()

        val schema = CsvSchema.Builder()
            .addColumn("Date", STRING)
            .addColumn("Total", NUMBER)
            .addColumn("DEB", NUMBER)
            .addColumn("RPM", NUMBER)
            .addColumn("Zip", NUMBER)
            .setUseHeader(true)
            .build()

        val data = analytics.asSequence()
            .map { (date, data) ->
                arrayOf(
                    DateTimeFormatter.BASIC_ISO_DATE.format(date),
                    data.deb + data.rpm + data.zip,
                    data.deb,
                    data.rpm,
                    data.zip
                )
            }
            .asIterable()

        mapper.writer().with(schema).writeValues(output).use { writer ->
            writer.writeAll(data)
        }
    }
}

