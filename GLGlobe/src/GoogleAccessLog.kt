import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.streams.asSequence

/**
 * @author Tyler Scott
 */
data class GoogleAccessLogEntry @JsonCreator constructor(
    @JsonProperty("time_micros") val time_micros: String,
    @JsonProperty("c_ip") val c_ip: String,
    @JsonProperty("c_ip_type") val c_ip_type: String,
    @JsonProperty("c_ip_region") val c_ip_region: String,
    @JsonProperty("cs_method") val cs_method: String,
    @JsonProperty("cs_uri") val cs_uri: String,
    @JsonProperty("sc_status") val sc_status: String,
    @JsonProperty("cs_bytes") val cs_bytes: String,
    @JsonProperty("sc_bytes") val sc_bytes: String,
    @JsonProperty("time_taken_micros") val time_taken_micros: String,
    @JsonProperty("cs_host") val cs_host: String,
    @JsonProperty("cs_referer") val cs_referer: String,
    @JsonProperty("cs_user_agent") val cs_user_agent: String,
    @JsonProperty("s_request_id") val s_request_id: String,
    @JsonProperty("cs_operation") val cs_operation: String,
    @JsonProperty("cs_bucket") val cs_bucket: String,
    @JsonProperty("cs_object") val cs_object: String
)

private data class GoogleAccessLog(
    val path: Path,
    val date: LocalDateTime,
    val version: String
)

private val accessLogRegex =
    "^FusionAuthAccesssLog_usage_(?<year>\\d{4})_(?<month>\\d{2})_(?<day>\\d{2})_(?<hour>\\d{2})_00_00_(?<version>.+)_v0$".toRegex()


fun readGoogleAccessLogs(dir: Path, consumer: (Sequence<GoogleAccessLogEntry>, day: LocalDateTime) -> Unit) {
    val mapper = CsvMapper()
    val schema = CsvSchema.builder()
        .addColumn("time_micros")
        .addColumn("c_ip")
        .addColumn("c_ip_type")
        .addColumn("c_ip_region")
        .addColumn("cs_method")
        .addColumn("cs_uri")
        .addColumn("sc_status")
        .addColumn("cs_bytes")
        .addColumn("sc_bytes")
        .addColumn("time_taken_micros")
        .addColumn("cs_host")
        .addColumn("cs_referer")
        .addColumn("cs_user_agent")
        .addColumn("s_request_id")
        .addColumn("cs_operation")
        .addColumn("cs_bucket")
        .addColumn("cs_object")
        .setUseHeader(true)
        .build()
    val reader = mapper.readerFor(GoogleAccessLogEntry::class.java).with(schema)

    Files.list(dir)
        .asSequence()
        .filter {
            it.fileName.toString().matches(accessLogRegex)
        }
        .map {
            val groups = accessLogRegex.matchEntire(it.fileName.toString())!!.groups
            val date = LocalDateTime.of(
                Integer.parseInt(groups["year"]!!.value),
                Integer.parseInt(groups["month"]!!.value),
                Integer.parseInt(groups["day"]!!.value),
                Integer.parseInt(groups["hour"]!!.value),
                0
            )

            GoogleAccessLog(it, date, groups["version"]!!.value)
        }
//        .groupBy { it.date }
//        .mapNotNull {
//            it.value.maxBy { it.version }
//        }
        .forEach {
            val entries = reader.readValues<GoogleAccessLogEntry>(it.path.toFile()).asSequence()
            consumer(entries, it.date)
        }
}
