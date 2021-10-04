package xyz.qalcyo.convertsrg

import com.google.gson.JsonObject
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.qalcyo.convertsrg.types.ClassType
import xyz.qalcyo.convertsrg.types.Field
import xyz.qalcyo.convertsrg.types.PackageType
import xyz.qalcyo.convertsrg.utils.*
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.lang.NullPointerException
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile
import java.util.zip.ZipOutputStream

fun convertSrg(minecraftVersion: String, mcpVersion: String, mcpChannel: String): String {
    val versionNum = convertVersionStringToInt(minecraftVersion)

    return if (versionNum > 11102) convertNewSrg(minecraftVersion)
        else convertOldSrg(minecraftVersion, mcpVersion, mcpChannel)
}

fun convertOldSrg(minecraftVersion: String, mcpVersion: String, mcpChannel: String): String {
    println("Finding Minecraft jar locations")
    val versionEntry = getJson<JsonObject>(versionManifest)
        .getAsJsonArray("versions")
        .map { it.asJsonObject }
        .find { it["id"].asString == minecraftVersion }!!

    val downloads = getJson<JsonObject>(versionEntry["url"].asString)
        .getAsJsonObject("downloads")

    println("Downloading Minecraft Client jar")
    val clientFile = File(cacheLocation, "$minecraftVersion/client.jar")
    downloadFile(clientFile) {
        URL(downloads.getAsJsonObject("client")["url"].asString).openStream().use { it.readBytes() }
    }

    println("Downloading Minecraft Server jar")
    val serverFile = File(cacheLocation, "$minecraftVersion/server.jar")
    downloadFile(serverFile) {
        URL(downloads.getAsJsonObject("server")["url"].asString).openStream().use { it.readBytes() }
    }

    println("Merging minecraft jars")
//    val mergedJarFile = File(cacheLocation, "$minecraftVersion/merged.jar")
//    ZipOutputStream(FileOutputStream(mergedJarFile)).use {
//        readZip(it, clientFile)
//        readZip(it, serverFile)
//    }
    val mergedJar = JarFile(clientFile)

    println("Downloading MCP files")
    val joined = unzipAndFindFile(oldSrgUrl.replace("{mc_ver}", minecraftVersion), "joined.srg")!!
        .readBytes().decodeToString()

    val fields = unzipAndFindFile(oldMappingUrl
        .replace("{mc_ver}", minecraftVersion)
        .replace("{mcp_ver}", mcpVersion)
        .replace("{mcp_channel}", mcpChannel),

        "fields.csv"
    )!!.readBytes().decodeToString()
    val methods = unzipAndFindFile(oldMappingUrl
        .replace("{mc_ver}", minecraftVersion)
        .replace("{mcp_ver}", mcpVersion)
        .replace("{mcp_channel}", mcpChannel),

        "methods.csv"
    )!!.readBytes().decodeToString()

    println("Mapping SRG -> MCP")
    val friendlyFields = mutableMapOf<String, String>()
    for (line in fields.lines()) {
        val split = line.split(',')
        if (split.size < 2) continue

        friendlyFields[split[0]] = split[1]
    }

    val friendlyMethods = mutableMapOf<String, String>()
    for (line in methods.lines()) {
        val split = line.split(',')
        if (split.size < 2) continue

        friendlyMethods[split[0]] = split[1]
    }

    val classes = mutableMapOf<String, ClassType>()

    println("Mapping OBF -> SRG")
    for (line in joined.lines()) {
        val type = SrgType.find(line) ?: continue
        val trimmed = line.substring(4)

        val split = trimmed.split(' ')

        when (type) {
            SrgType.Class ->
                classes[split[0]] = ClassType(split[1].substringAfterLast('/'), split[1].substringBeforeLast('/'))

            SrgType.Field -> {
                val obfSplit = split[0].split('/')
                val className = obfSplit[0]
                val obfFieldName = obfSplit.last()
                val fieldName = split[1].substringAfterLast('/')

                val classReader = try {
                    ClassReader(mergedJar.getInputStream(mergedJar.getJarEntry("$className.class")))
                } catch (e: NullPointerException) {
                    continue
                }
                val node = ClassNode()
                classReader.accept(node, 0)
                val fieldType = convertDescriptorToType(node.fields.find { it.name == obfFieldName }!!.desc) {
                    val clazz = classes[it] ?: return@convertDescriptorToType it
                    "${clazz.pkg.replace('/', '.')}.${clazz.name}"
                }

                classes[className]!!.fields[obfFieldName] = Field(friendlyFields[fieldName] ?: fieldName, fieldType)
            }
            SrgType.Method -> {

            }
        }
    }

    println("Converting to Proguard format!")
    val builder = StringBuilder()
    for ((obfClass, clazz) in classes) {
        builder.appendLine("${clazz.pkg.replace('/', '.')}.${clazz.name} -> $obfClass:")
        for ((obfField, field) in clazz.fields) {
            builder.appendLine("    ${field.type} ${field.name} -> $obfField")
        }
    }

    println("Finished")
    return builder.toString()
}

fun convertNewSrg(version: String): String {
    return "e"
}
