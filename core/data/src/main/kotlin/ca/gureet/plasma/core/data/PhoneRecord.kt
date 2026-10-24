// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

/**
 * A phone paired as a remote (brief 5.21), as the TV keeps it: its name, its token as a hash, and
 * when it paired and was last seen, in Unix ms.
 */
data class PhoneRecord(val id: String, val name: String, val tokenHash: String, val pairedAt: Long, val lastSeen: Long) {
    companion object {
        private const val FIELDS = 5

        /** One phone a line, its fields apart by tabs; a name can't hold either. */
        fun format(phones: List<PhoneRecord>): String = phones.joinToString("\n") { phone ->
            listOf(phone.id, clean(phone.name), phone.tokenHash, phone.pairedAt.toString(), phone.lastSeen.toString()).joinToString("\t")
        }

        fun parse(text: String?): List<PhoneRecord> = text.orEmpty().lines().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != FIELDS) return@mapNotNull null
            PhoneRecord(fields[0], fields[1], fields[2], fields[3].toLongOrNull() ?: return@mapNotNull null, fields[4].toLongOrNull() ?: return@mapNotNull null)
        }

        private fun clean(name: String) = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    }
}
