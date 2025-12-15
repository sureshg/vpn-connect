package vip

import kotlin.math.abs

/**
 * Pure Kotlin QR Code generator.
 *
 * Implements ISO/IEC 18004 with byte mode encoding and error correction level L.
 *
 * ## How It Works
 *
 * The input string flows through 5 stages to produce a scannable QR code:
 *
 * **1. Encode Data** — Convert input to a bit stream: a mode indicator tells the scanner it's byte
 * data, followed by character count, the actual bytes, and padding to fill capacity.
 *
 * **2. Error Correction** — Append Reed-Solomon codes so scanners can recover ~7% damage.
 *
 * **3. Build Matrix** — Create the grid. First place fixed patterns (finder squares at corners,
 * timing stripes, alignment marks), then fill encoded data in a zigzag pattern.
 *
 * **4. Apply Mask** — XOR data area with one of 8 patterns to break up large uniform regions that
 * confuse scanners. Pick the mask with lowest penalty score.
 *
 * **5. Add Format** — Write 15 bits encoding the error correction level and mask ID around finders
 * so scanners know how to decode.
 */
object QrCode {

  private val CAPACITY =
      intArrayOf(
          0,
          17,
          32,
          53,
          78,
          106,
          134,
          154,
          192,
          230,
          271,
          321,
          367,
          425,
          458,
          520,
          586,
          644,
          718,
          792,
          858,
      )
  private val DATA_COUNT =
      intArrayOf(
          0,
          19,
          34,
          55,
          80,
          108,
          136,
          156,
          194,
          232,
          274,
          324,
          370,
          428,
          461,
          523,
          589,
          647,
          721,
          795,
          861,
      )
  private val EC_COUNT =
      intArrayOf(0, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28)
  private val FORMAT_BITS =
      intArrayOf(0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976)
  private val ALIGNMENT =
      arrayOf(
          intArrayOf(),
          intArrayOf(),
          intArrayOf(6, 18),
          intArrayOf(6, 22),
          intArrayOf(6, 26),
          intArrayOf(6, 30),
          intArrayOf(6, 34),
          intArrayOf(6, 22, 38),
          intArrayOf(6, 24, 42),
          intArrayOf(6, 26, 46),
          intArrayOf(6, 28, 50),
          intArrayOf(6, 30, 54),
          intArrayOf(6, 32, 58),
          intArrayOf(6, 34, 62),
          intArrayOf(6, 26, 46, 66),
          intArrayOf(6, 26, 48, 70),
          intArrayOf(6, 26, 50, 74),
          intArrayOf(6, 30, 54, 78),
          intArrayOf(6, 30, 56, 82),
          intArrayOf(6, 30, 58, 86),
          intArrayOf(6, 34, 62, 90),
      )
  private val MASKS: Array<(Int, Int) -> Boolean> =
      arrayOf(
          { y, x -> (y + x) % 2 == 0 },
          { y, _ -> y % 2 == 0 },
          { _, x -> x % 3 == 0 },
          { y, x -> (y + x) % 3 == 0 },
          { y, x -> (y / 2 + x / 3) % 2 == 0 },
          { y, x -> y * x % 2 + y * x % 3 == 0 },
          { y, x -> (y * x % 2 + y * x % 3) % 2 == 0 },
          { y, x -> ((y + x) % 2 + y * x % 3) % 2 == 0 },
      )

  private val GF_EXP = IntArray(512)
  private val GF_LOG = IntArray(256)

  init {
    var x = 1
    for (i in 0..<255) {
      GF_EXP[i] = x
      GF_LOG[x] = i
      x = if (x < 128) x * 2 else (x * 2) xor 0x11D
    }
    for (i in 255..<512) GF_EXP[i] = GF_EXP[i - 255]
  }

  private fun gfMul(a: Int, b: Int) = if (a == 0 || b == 0) 0 else GF_EXP[GF_LOG[a] + GF_LOG[b]]

  /** Encodes string data into a QR code matrix. */
  fun encode(data: String): QrMatrix {
    val bytes = data.encodeToByteArray()
    val version = (1..20).first { CAPACITY[it] >= bytes.size }
    val matrix = QrMatrix(version * 4 + 17)

    placePatterns(matrix, version)
    val encoded = encodeData(bytes, version)
    placeData(matrix, encoded + computeEC(encoded, EC_COUNT[version]))

    val mask = selectMask(matrix)
    applyMask(matrix, mask)
    placeFormat(matrix, mask)

    return matrix
  }

  private fun encodeData(bytes: ByteArray, version: Int): ByteArray {
    val capacity = DATA_COUNT[version]
    val countBits = if (version < 10) 8 else 16

    val bits = buildList {
      add(false)
      add(true)
      add(false)
      add(false)
      for (i in countBits - 1 downTo 0) add((bytes.size shr i and 1) == 1)
      for (b in bytes) for (i in 7 downTo 0) add((b.toInt() shr i and 1) == 1)
      repeat(minOf(4, capacity * 8 - size)) { add(false) }
      while (size % 8 != 0) add(false)
    }

    val dataBytes = bits.size / 8
    return ByteArray(capacity) { i ->
      when {
        i < dataBytes -> {
          var byte = 0
          for (j in 0..<8) if (bits[i * 8 + j]) byte = byte or (1 shl (7 - j))
          byte.toByte()
        }
        (i - dataBytes) % 2 == 0 -> 0xEC.toByte()
        else -> 0x11.toByte()
      }
    }
  }

  private fun computeEC(data: ByteArray, ecCount: Int): ByteArray {
    val gen = IntArray(ecCount) { if (it == ecCount - 1) 1 else 0 }
    var root = 1
    repeat(ecCount) {
      for (j in 0..<ecCount) {
        gen[j] = gfMul(gen[j], root)
        if (j + 1 < ecCount) gen[j] = gen[j] xor gen[j + 1]
      }
      root = gfMul(root, 2)
    }

    val work = IntArray(ecCount)
    for (b in data) {
      val lead = (b.toInt() and 0xFF) xor work[0]
      for (i in 0..<ecCount - 1) work[i] = work[i + 1]
      work[ecCount - 1] = 0
      for (i in 0..<ecCount) work[i] = work[i] xor gfMul(gen[i], lead)
    }

    return ByteArray(ecCount) { work[it].toByte() }
  }

  private fun placePatterns(m: QrMatrix, version: Int) {
    val s = m.size

    for ((cy, cx) in listOf(3 to 3, 3 to s - 4, s - 4 to 3)) {
      for (dy in -4..4) for (dx in -4..4) {
        val y = cy + dy
        val x = cx + dx
        if (y in 0..<s && x in 0..<s) {
          val ring = maxOf(abs(dy), abs(dx))
          m.fix(y, x, ring != 2 && ring != 4)
        }
      }
    }

    for (i in 8..<s - 8) {
      m.fix(6, i, i % 2 == 0)
      m.fix(i, 6, i % 2 == 0)
    }

    for (cy in ALIGNMENT[version]) {
      for (cx in ALIGNMENT[version]) {
        if (m.isFixed(cy, cx)) continue
        for (dy in -2..2) for (dx in -2..2) {
          m.fix(cy + dy, cx + dx, maxOf(abs(dy), abs(dx)) != 1)
        }
      }
    }

    m.fix(s - 8, 8, true)
    for (i in 0..8) {
      if (!m.isFixed(8, i)) m.fix(8, i, false)
      if (!m.isFixed(i, 8)) m.fix(i, 8, false)
    }
    for (i in 0..7) {
      m.fix(8, s - 1 - i, false)
      m.fix(s - 1 - i, 8, false)
    }
  }

  private fun placeData(m: QrMatrix, data: ByteArray) {
    val bits = BooleanArray(data.size * 8) { i -> (data[i / 8].toInt() shr (7 - i % 8) and 1) == 1 }

    var idx = 0
    var x = m.size - 1
    var upward = true

    while (x >= 1) {
      if (x == 6) x--
      for (y in if (upward) m.size - 1 downTo 0 else 0..<m.size) {
        for (dx in 0..1) {
          val col = x - dx
          if (!m.isFixed(y, col)) {
            if (idx < bits.size) m[y, col] = bits[idx]
            idx++
          }
        }
      }
      upward = !upward
      x -= 2
    }
  }

  private fun placeFormat(m: QrMatrix, mask: Int) {
    val bits = FORMAT_BITS[mask]
    val s = m.size

    for (i in 0..5) m.fix(8, i, (bits shr (14 - i)) and 1 == 1)
    m.fix(8, 7, (bits shr 8) and 1 == 1)
    m.fix(8, 8, (bits shr 7) and 1 == 1)
    m.fix(7, 8, (bits shr 6) and 1 == 1)
    for (i in 0..5) m.fix(5 - i, 8, (bits shr (5 - i)) and 1 == 1)
    for (i in 0..7) m.fix(s - 1 - i, 8, (bits shr i) and 1 == 1)
    for (i in 0..7) m.fix(8, s - 8 + i, (bits shr (14 - i)) and 1 == 1)
  }

  private fun selectMask(m: QrMatrix): Int {
    var best = 0
    var bestPenalty = Int.MAX_VALUE
    for (mask in 0..7) {
      applyMask(m, mask)
      val penalty = m.penalty()
      if (penalty < bestPenalty) {
        bestPenalty = penalty
        best = mask
      }
      applyMask(m, mask)
    }
    return best
  }

  private fun applyMask(m: QrMatrix, mask: Int) {
    val fn = MASKS[mask]
    for (y in 0..<m.size) {
      for (x in 0..<m.size) {
        if (!m.isFixed(y, x) && fn(y, x)) m[y, x] = !m[y, x]
      }
    }
  }
}

/**
 * QR code module matrix.
 *
 * Stores the 2D grid of dark/light modules and tracks which modules are "fixed" (finder patterns,
 * timing, alignment) vs data modules.
 */
class QrMatrix(val size: Int) {
  private val modules = BooleanArray(size * size)
  private val fixed = BooleanArray(size * size)

  operator fun get(y: Int, x: Int) = modules[y * size + x]

  operator fun set(y: Int, x: Int, v: Boolean) {
    modules[y * size + x] = v
  }

  fun isFixed(y: Int, x: Int) = fixed[y * size + x]

  fun fix(y: Int, x: Int, v: Boolean) {
    modules[y * size + x] = v
    fixed[y * size + x] = true
  }

  /**
   * Computes penalty score for mask selection. Lower is better. Checks for consecutive runs and 2×2
   * blocks.
   */
  fun penalty(): Int {
    var p = 0
    for (y in 0..<size) {
      var run = 1
      for (x in 1..<size) {
        if (this[y, x] == this[y, x - 1]) run++
        else {
          if (run >= 5) p += run - 2
          run = 1
        }
      }
      if (run >= 5) p += run - 2
    }

    for (x in 0..<size) {
      var run = 1
      for (y in 1..<size) {
        if (this[y, x] == this[y - 1, x]) run++
        else {
          if (run >= 5) p += run - 2
          run = 1
        }
      }
      if (run >= 5) p += run - 2
    }

    for (y in 0..<size - 1) {
      for (x in 0..<size - 1) {
        val v = this[y, x]
        if (v == this[y, x + 1] && v == this[y + 1, x] && v == this[y + 1, x + 1]) p += 3
      }
    }
    return p
  }

  /**
   * Renders QR code as compact ASCII with a decorative frame. Uses Unicode half-block characters,
   * inverted for dark terminal backgrounds.
   *
   * @param margin internal margin around the QR code
   * @param label optional label to display in the frame header
   */
  fun toAscii(margin: Int = 1, label: String = "") = buildString {
    val contentWidth = size + margin * 2
    val innerWidth = contentWidth + 2 // +2 for space padding inside borders

    // Top border with optional centered label
    append('╭')
    val maxLen = innerWidth - 2 // space for padding around label
    val header =
        when {
          label.isEmpty() -> ""
          label.length > maxLen -> " ${label.take(maxLen - 1)}… "
          else -> " $label "
        }

    val pad = innerWidth - header.length
    append("─".repeat(pad / 2)).append(header).append("─".repeat(pad - pad / 2))
    appendLine('╮')

    // QR code rows
    for (y in -margin..<size + margin step 2) {
      append("│ ")
      for (x in -margin..<size + margin) {
        val top = !isDark(y, x)
        val bot = !isDark(y + 1, x)
        append(if (top && bot) '█' else if (top) '▀' else if (bot) '▄' else ' ')
      }
      appendLine(" │")
    }

    // Bottom border
    append('╰').append("─".repeat(innerWidth)).appendLine('╯')
  }

  private fun isDark(y: Int, x: Int) = y in 0..<size && x in 0..<size && this[y, x]
}

/** Generates ASCII QR code from string. */
fun String.toQrAscii(label: String = "", margin: Int = 1) =
    QrCode.encode(this)
        .toAscii(
            margin = margin,
            label = label,
        )
