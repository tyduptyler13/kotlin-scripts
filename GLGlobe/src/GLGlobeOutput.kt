import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import java.io.OutputStream
import java.net.InetAddress

/**
 * @author Tyler Scott
 */

class GLGlobeOutput(
    private val database: DatabaseReader,
    private val whitelist: Set<String>
) {

    private val zipDownloads: MutableMap<Pair<Double, Double>, Int> = mutableMapOf()

    private val rpmDownloads: MutableMap<Pair<Double, Double>, Int> = mutableMapOf()

    private val debDownloads: MutableMap<Pair<Double, Double>, Int> = mutableMapOf()

    fun collectSome(entries: Sequence<GoogleAccessLogEntry>) {
        entries
            .filter {
                it.cs_object.contains("fusionauth-app")
            }
            .filter {
                !whitelist.contains(it.c_ip)
            }
            .forEach {
                val location = getLongLat(InetAddress.getByName(it.c_ip)) ?: 0.0 to 0.0
                // Fallback to the middle of the ocean for unknown locations

                when (it.cs_object.takeLast(3)) {
                    "zip" -> zipDownloads.compute(location, this::incrementOrCreate)
                    "deb" -> debDownloads.compute(location, this::incrementOrCreate)
                    "rpm" -> rpmDownloads.compute(location, this::incrementOrCreate)
                    else -> println("WARN: Unknown extension [$it]")
                }
            }
    }

    private fun incrementOrCreate(@Suppress("UNUSED_PARAMETER") key: Any?, value: Int?): Int? {
        return value?.let {
            it + 1
        } ?: 1
    }

    fun writeResult(output: OutputStream) {
        val factory = JsonFactory()

        factory.createGenerator(output, JsonEncoding.UTF8).use { generator ->
            generator.writeStartObject()
            generator.writeWebGLNamedArray("zip", zipDownloads)
            generator.writeWebGLNamedArray("rpm", rpmDownloads)
            generator.writeWebGLNamedArray("deb", debDownloads)
            generator.writeEndObject()
        }
    }

    private fun JsonGenerator.writeWebGLNamedArray(name: String, data: Map<Pair<Double, Double>, Int>) {
        writeArrayFieldStart(name)
        data.forEach { (key, value) ->
            writeNumber(key.first)
            writeNumber(key.second)
            writeNumber(value)
        }
        writeEndArray()
    }

    private fun getLongLat(ip: InetAddress): Pair<Double, Double>? {
        return try {
            database.city(ip).location.let {
                it.latitude to it.longitude
            }
        } catch (e: AddressNotFoundException) {
            null
        }
    }
}

