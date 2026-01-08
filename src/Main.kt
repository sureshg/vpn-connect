import io.github.oshai.kotlinlogging.*
import vip.toQrAscii

// fun main(args: Array<String>) = VpnConnect().subcommands(Provision()).main(args)

fun main() {
  KotlinLoggingConfiguration.logStartupMessage = false
  println("https://inside.java/".toQrAscii("https://inside.java/"))
}
