// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.core.data

/**
 * A phone paired as a remote (brief 5.21), as the TV keeps it: its name, its token as a hash, when
 * it paired and was last seen, in Unix ms, and whether it's the Plasma Remote app rather than the
 * web page (5.22), as it last connected.
 */
data class PhoneRecord(val id: String, val name: String, val tokenHash: String, val pairedAt: Long, val lastSeen: Long, val app: Boolean = false) {
    companion object {
        private const val FIELDS = 5

        /**
         * One phone a line, its fields apart by tabs; a name can't hold either. An app's line has a
         * sixth field, "app": a browser's stays as it was, so a Plasma from before keeps those.
         */
        fun format(phones: List<PhoneRecord>): String = phones.joinToString("\n") { phone ->
            listOfNotNull(phone.id, clean(phone.name), phone.tokenHash, phone.pairedAt.toString(), phone.lastSeen.toString(), APP.takeIf { phone.app }).joinToString("\t")
        }

        fun parse(text: String?): List<PhoneRecord> = text.orEmpty().lines().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != FIELDS && fields.size != FIELDS + 1) return@mapNotNull null
            PhoneRecord(
                fields[0],
                fields[1],
                fields[2],
                fields[3].toLongOrNull() ?: return@mapNotNull null,
                fields[4].toLongOrNull() ?: return@mapNotNull null,
                app = fields.getOrNull(FIELDS) == APP,
            )
        }

        private const val APP = "app"

        private fun clean(name: String) = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    }
}
