package xyz.qalcyo.convertsrg

import com.google.gson.JsonObject
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import xyz.qalcyo.convertsrg.types.ClassType
import xyz.qalcyo.convertsrg.types.Field
import xyz.qalcyo.convertsrg.types.Method
import xyz.qalcyo.convertsrg.utils.*
import java.io.File
import java.lang.NullPointerException
import java.net.URL
import java.util.jar.JarFile

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
                    getMcpName(it, classes)
                }

                classes[className]!!.fields[obfFieldName] = Field(friendlyFields[fieldName] ?: fieldName, fieldType)
            }
            SrgType.Method -> {
                val obfSplit = split[0].split('/')
                val obfClassName = obfSplit[0]
                val obfMethodName = obfSplit.last()
                val srgMethodName = split[2].split('/').last()
                val obfParams = Type.getArgumentTypes(split[1]).map { it.className }
                val obfReturnValue = Type.getReturnType(split[1]).className

                val methodName = friendlyMethods[srgMethodName] ?: srgMethodName
                val params = obfParams.map {
                    getMcpName(it, classes)
                }
                val returnValue = getMcpName(obfReturnValue, classes)

                println(split[0])
                classes[obfClassName]?.methods?.add(Method(methodName, obfMethodName, returnValue, params))
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
        for (method in clazz.methods) {
            builder.appendLine("    ${method.returnValue} ${method.name}(${method.params.joinToString(", ")}) -> ${method.obfName}")
        }
    }

    println("Finished")
    return builder.toString()
}

fun convertNewSrg(version: String): String {
    return "e"
}
