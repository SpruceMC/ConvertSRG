package xyz.qalcyo.convertsrg.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import xyz.qalcyo.convertsrg.types.ClassType
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL
import java.util.zip.*
import kotlin.math.pow


const val newSrgUrl = "https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/{mc_ver}/mcp_config-{mc_ver}.zip"
const val oldSrgUrl = "http://export.mcpbot.bspk.rs/mcp/{mc_ver}/mcp-{mc_ver}-srg.zip"
const val oldMappingUrl = "http://export.mcpbot.bspk.rs/export/{mcp_channel}/{mcp_ver}-{mc_ver}/{mcp_channel}-{mcp_ver}-{mc_ver}.zip"
const val versionManifest = "https://launchermeta.mojang.com/mc/game/version_manifest.json"

val cacheLocation = File(System.getProperty("user.home"), ".cache/convertsrg")

fun convertVersionStringToInt(version: String): Int {
    val split = version.split(".")
        .map { it.toInt() }

    var num = 0

    for (i in split.indices) {
        val part = split[i]
        num += part * 100f.pow(split.size - i - 1).toInt()
    }

    return num
}

fun downloadFile(file: File, replace: Boolean = false, bytes: () -> ByteArray) {
    if (file.exists()) {
        if (replace) file.delete()
        else return
    }

    file.parentFile.mkdirs()
    file.createNewFile()
    file.writeBytes(bytes())
}

fun unzipAndFindFile(url: String, fileName: String): InputStream? {
    val file = File(cacheLocation, url.substringAfterLast('/'))

    downloadFile(file) { URL(url).openStream().use { it.readBytes() } }

    val zip = ZipFile(file)
    return zip.getInputStream(zip.getEntry(fileName))
}

fun <T : JsonElement> getJson(url: String): T =
    JsonParser.parseString(URL(url).openStream().use { it.readBytes().decodeToString() }) as T

fun readZip(out: ZipOutputStream, file: File) {
    val inStream = ZipInputStream(FileInputStream(file))
    val buffer = ByteArray(1024)
    var len: Int

    var e: ZipEntry?
    while (inStream.nextEntry.also { e = it } != null) {
        if (e!!.name.startsWith("META-INF")) continue
        out.putNextEntry(e!!)
        while (inStream.read(buffer).also { len = it } > 0) {
            out.write(buffer, 0, len)
        }
    }
    inStream.close()
}

fun convertDescriptorToType(desc: String, modifier: (String) -> String = { it }): String {
    val noArray = desc.substringAfterLast('[').replace('/', '.')
    val arrayDim = desc.length - noArray.length

    return when (noArray) {
        "I" -> "int"
        "F" -> "float"
        "Z" -> "boolean"
        "B" -> "byte"
        "S" -> "short"
        "C" -> "char"
        "D" -> "double"
        "J" -> "long"
        "V" -> "void"
        else -> modifier(noArray.substring(1 until noArray.length - 1))
    } + "[]".repeat(arrayDim)
}

fun String.withinBrackets(depth: Int = 0): String {
    return Regex("\\(([^)]+)\\)").find(this)!!.groupValues[depth]
}

fun getMcpName(obf: String, classes: Map<String, ClassType>): String {
    val clazz = classes[obf]

    return if (clazz != null) "${clazz.pkg.replace('/', '.')}.${clazz.name}"
        else obf
}

