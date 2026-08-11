import dev.suresh.vip.VipAccess
import io.github.goquati.qr.QrCode
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

// fun main(args: Array<String>) = VpnConnect().subcommands(Provision()).main(args)

fun main() = runBlocking {
  KotlinLoggingConfiguration.logStartupMessage = false
  // println("https://inside.java/".toQrAscii("https://inside.java/"))

  val qrCode = QrCode.encodeText("Hello, World!", QrCode.Ecc.LOW)
  println("QR Code size: ${qrCode.size}")
  for (y in 0 until qrCode.size) {
    for (x in 0 until qrCode.size) {
      // "██" handles the square proportions on most monospace modern terminal fonts
      print(if (qrCode[x, y]) "██" else "  ")
    }
    println() // Move to next line
  }

  println("------")

  val token = VipAccess().provision()

  val spinner = ["⣷", "⣯", "⣟", "⡿", "⢿", "⣻", "⣽", "⣾"]
  var i = 0
  println("\n🔐 VIP Access - ${token.id}\n")

  val vip = VipAccess()
  while (true) {
    val otp = vip.generateOtp(token)
    val remaining = token.remainingSeconds
    // val bar = "█".repeat(remaining) + "░".repeat(token.period - remaining)

    print("\r ${spinner[i++ % spinner.size]} $otp (${remaining}s) ")
    delay(100.milliseconds)
  }
}
