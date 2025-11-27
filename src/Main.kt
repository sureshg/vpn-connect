import cmd.*
import com.github.ajalt.clikt.core.*

fun main(args: Array<String>) = VpnConnect().subcommands(Provision()).main(args)
