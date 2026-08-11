package cmd

fun vpnCmd() {
  //    println("Connecting to 2FA VPN...Username: ${getUsername()}")
  //
  //// Disconnect first
  //    println("Disconnecting...")
  //    Command("/opt/cisco/secureclient/bin/vpn")
  //        .arg("disconnect")
  //        .stdout(Stdio.Null)
  //        .stderr(Stdio.Null)
  //        .spawn()
  //        .waitWithOutput()
  //        .also { println("Disconnected! ${it.status}") }
  //
  //    println("Killing Cisco Secure Client processes...")
  //    Command("pkill")
  //        .arg("Cisco Secure Client")
  //        .stdout(Stdio.Null)
  //        .stderr(Stdio.Null)
  //        .spawn()
  //        .wait()
  //
  //// Connect with 2FA
  //    println("Waiting for 2FA...")
  //    val child = Command("/opt/cisco/secureclient/bin/vpn")
  //        .arg("-s")
  //        .stdin(Stdio.Pipe)
  //        .stdout(Stdio.Pipe)
  //        .stderr(Stdio.Pipe)
  //        .spawn()
  //
  //    val stdin = child.bufferedStdin()
  //    stdin?.writeLine("connect \"WeC 2 Step Verification\"")
  //    stdin?.writeLine(getUsername().orEmpty())
  //    stdin?.writeLine("xxxx")
  //    stdin?.writeLine("xxxx")
  //    stdin?.writeLine("y")
  //// DON'T call stdin?.close() here - waitWithOutput() does it
  //
  //    val output = child.waitWithOutput()
  //    println("Status: ${output.status}")
  //    println("Output: ${output.stdout}")
  //    println("Errors: ${output.stderr}")
}

// fun getUsername(): String? {
//    return getenv("USER")?.toKString() ?: getenv("USERNAME")?.toKString()  // Windows fallback
// }
