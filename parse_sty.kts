import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

val bytes = File("/app/applet/app/YamahaAnalise.sty").readBytes()
val str = String(bytes, StandardCharsets.US_ASCII)
val regex = Regex("([A-Z][a-zA-Z ]{3,15})")
val matches = regex.findAll(str).map { it.value }.toList()
println("Potential markers:")
matches.filter { it.contains("Main") || it.contains("Intro") || it.contains("Fill") || it.contains("Ending") || it.contains("CASM") }.distinct().forEach { println(it) }
