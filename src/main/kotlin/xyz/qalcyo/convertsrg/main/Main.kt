package xyz.qalcyo.convertsrg.main

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.mainBody
import xyz.qalcyo.convertsrg.convertSrg
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::Arguments).run {
        val output = File("./client.txt")
        if (output.exists()) output.delete()
        output.parentFile.mkdirs()
        output.writeBytes(convertSrg(minecraft, mcpVersion, mcpChannel).toByteArray())
    }
}

class Arguments(parser: ArgParser) {
    val minecraft by parser.storing(
        "--minecraft",
        help = "Version of minecraft that is converted. (e.g. 1.8.9)"
    )

    val mcpChannel by parser.storing(
        "--channel",
        help = "MCP channel to use (e.g. mcp_stable)"
    )

    val mcpVersion by parser.storing(
        "--mcp",
        help = "Version of MCP to map SRG names. (e.g. 22)"
    )
}
