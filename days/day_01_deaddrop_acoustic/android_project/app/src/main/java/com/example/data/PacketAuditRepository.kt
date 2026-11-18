package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.dsp.PacketProtocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class PacketAuditRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val mutex = Mutex()

  private val _entries = MutableStateFlow<List<AuditLogEntry>>(emptyList())
  val entries: StateFlow<List<AuditLogEntry>> = _entries.asStateFlow()

  private val _persistenceEnabled = MutableStateFlow(readPersistenceFlag())
  val persistenceEnabled: StateFlow<Boolean> = _persistenceEnabled.asStateFlow()

  private fun readPersistenceFlag(): Boolean = prefs.getBoolean(KEY_PERSIST_OPT_IN, false)

  fun isPersistenceEnabled(): Boolean = _persistenceEnabled.value

  suspend fun setPersistenceEnabled(enabled: Boolean) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        _persistenceEnabled.value = enabled
        if (enabled) {
          prefs.edit().putBoolean(KEY_PERSIST_OPT_IN, true).apply()
          writeToPrefs(_entries.value)
        } else {
          prefs.edit().clear().apply()
        }
      }
    }
  }

  suspend fun loadInitial() {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val enabled = readPersistenceFlag()
        _persistenceEnabled.value = enabled
        if (enabled) {
          _entries.value = readFromPrefs()
        } else {
          _entries.value = emptyList()
        }
      }
    }
  }

  suspend fun append(
    direction: LogDirection,
    packetType: String,
    callsign: String,
    payload: String,
    crcHex: String,
    crcValid: Boolean,
    hexFrame: String,
    timestampMs: Long = System.currentTimeMillis(),
  ) {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        val current = _entries.value.toMutableList()
        val entry =
          AuditLogEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = timestampMs,
            direction = direction,
            packetType = PacketProtocol.sanitizeText(packetType, 20),
            callsign = PacketProtocol.sanitizeText(callsign, 16),
            payload = PacketProtocol.sanitizeText(payload, 240),
            crcHex = PacketProtocol.sanitizeText(crcHex, 12),
            crcValid = crcValid,
            hexFrame = PacketProtocol.sanitizeText(hexFrame, 600),
          )
        current.add(0, entry)
        val trimmed = if (current.size > MAX_ENTRIES) current.take(MAX_ENTRIES) else current
        if (_persistenceEnabled.value) {
          writeToPrefs(trimmed)
        }
        _entries.value = trimmed
      }
    }
  }

  suspend fun clear() {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        prefs.edit().remove(KEY_AUDIT_LOG).apply()
        _entries.value = emptyList()
      }
    }
  }

  suspend fun wipeAllData() {
    withContext(Dispatchers.IO) {
      mutex.withLock {
        prefs.edit().clear().apply()
        _persistenceEnabled.value = false
        _entries.value = emptyList()
      }
    }
  }

  fun exportFormattedLog(): String {
    val current = _entries.value
    if (current.isEmpty()) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return current.joinToString("\n") { e ->
      val ts = fmt.format(Date(e.timestampMs))
      val crcStatus = if (e.crcHex == "N/A") "MORSE" else if (e.crcValid) "${e.crcHex} OK" else "${e.crcHex} FAIL"
      val safePayload = sanitizeFormulaPrefix(PacketProtocol.sanitizeText(e.payload, 240))
      "[$ts] [${e.direction.label}] [${e.packetType}] [$crcStatus] $safePayload"
    }
  }

  private fun sanitizeFormulaPrefix(text: String): String {
    if (text.isEmpty()) return text
    return when (text[0]) {
      '=', '+', '-', '@' -> "'$text"
      else -> text
    }
  }

  private fun readFromPrefs(): List<AuditLogEntry> {
    val raw = prefs.getString(KEY_AUDIT_LOG, null) ?: return emptyList()
    return try {
      val arr = JSONArray(raw)
      val list = ArrayList<AuditLogEntry>(arr.length().coerceAtMost(MAX_ENTRIES))
      for (i in 0 until arr.length().coerceAtMost(MAX_ENTRIES)) {
        val obj = arr.optJSONObject(i) ?: continue
        val dirStr = obj.optString("direction", LogDirection.TX.name)
        val dir =
          try {
            LogDirection.valueOf(dirStr)
          } catch (_: IllegalArgumentException) {
            LogDirection.TX
          }
        list.add(
          AuditLogEntry(
            id = PacketProtocol.sanitizeText(obj.optString("id", UUID.randomUUID().toString()), 64),
            timestampMs = obj.optLong("timestampMs", 0L),
            direction = dir,
            packetType = PacketProtocol.sanitizeText(obj.optString("packetType", "TEXT"), 20),
            callsign = PacketProtocol.sanitizeText(obj.optString("callsign", ""), 16),
            payload = PacketProtocol.sanitizeText(obj.optString("payload", ""), 240),
            crcHex = PacketProtocol.sanitizeText(obj.optString("crcHex", "0x0000"), 12),
            crcValid = obj.optBoolean("crcValid", true),
            hexFrame = PacketProtocol.sanitizeText(obj.optString("hexFrame", ""), 600),
          )
        )
      }
      list
    } catch (_: Throwable) {
      emptyList()
    }
  }

  private fun writeToPrefs(entries: List<AuditLogEntry>) {
    val arr = JSONArray()
    for (entry in entries) {
      val obj =
        JSONObject()
          .put("id", entry.id)
          .put("timestampMs", entry.timestampMs)
          .put("direction", entry.direction.name)
          .put("packetType", entry.packetType)
          .put("callsign", entry.callsign)
          .put("payload", entry.payload)
          .put("crcHex", entry.crcHex)
          .put("crcValid", entry.crcValid)
          .put("hexFrame", entry.hexFrame)
      arr.put(obj)
    }
    prefs.edit().putString(KEY_AUDIT_LOG, arr.toString()).apply()
  }

  companion object {
    private const val PREFS_NAME = "deaddrop_acoustic_prefs"
    private const val KEY_AUDIT_LOG = "packet_audit_log_v1"
    private const val KEY_PERSIST_OPT_IN = "persist_history_opt_in_v1"
    private const val MAX_ENTRIES = 200
  }
}
