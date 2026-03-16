package cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt

class VpnConnect : CliktCommand(name = "vpn-connect") {
  val password by
      option()
          .prompt(
              text = "Enter your password:",
              hideInput = true,
          )
          .help("Password for provisioning")

  override fun run() {
    echo("Password: $password")
  }
}

class Provision : CliktCommand(name = "provision") {
  override fun help(context: Context) = "Provision a new VIP Access token"

  override fun run() {
    echo("Provisioning...")
  }
}
