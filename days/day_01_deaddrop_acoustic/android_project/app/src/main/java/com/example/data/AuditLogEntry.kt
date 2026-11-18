package com.example.data

enum class LogDirection(val label: String) {
  TX("TX"),
  RX("RX"),
  LOOPBACK("LOOPBACK"),
  BEACON("BEACON"),
}

data class AuditLogEntry(
  val id: String,
  val timestampMs: Long,
  val direction: LogDirection,
  val packetType: String,
  val callsign: String,
  val payload: String,
  val crcHex: String,
  val crcValid: Boolean,
  val hexFrame: String,
)
