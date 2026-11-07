// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma.pairing

/**
 * The words on the phone pages (brief 5.15), from the app's string resources so they follow the
 * same copy as the TV. The ones with the TV's name in them come filled in.
 */
data class PhonePageTexts(
    /** "Living Room TV" */
    val tvName: String,
    /** "Add a Source" */
    val title: String,
    /** "Stalker Portal", "Xtream Codes", "M3U Playlist": the tabs. */
    val stalker: String,
    val xtream: String,
    val m3u: String,
    val fieldName: String,
    val fieldPortal: String,
    val fieldMac: String,
    /** Under the MAC address: "Leave it empty to use the TV's own." */
    val macHint: String,
    val fieldServer: String,
    val fieldUsername: String,
    val fieldPassword: String,
    val fieldPlaylist: String,
    val fieldGuide: String,
    /** "Send to Living Room TV" */
    val send: String,
    /** "Enter the Code on Your TV" */
    val codeTitle: String,
    /** "It's under the QR code on Add a Source." */
    val codeText: String,
    val codeLabel: String,
    /** "Continue" */
    val next: String,
    /** "That code didn't work. Check the one on your TV." */
    val wrongCode: String,
    /** "Too many tries. Your TV shows a new code now." */
    val tooManyTries: String,
    /** "Fill in the address." */
    val missingAddress: String,
    /** "Sent to Living Room TV" */
    val sentTitle: String,
    /** "Check the details on the TV, then choose Connect." */
    val sentText: String,
    /** "Not here. Scan the code on your TV again." */
    val notFound: String,
    /** "Details stay on your home network." */
    val note: String,
    /** Pairing with wireless debugging (brief 4.24): "Pair Plasma with Living Room TV". */
    val debugTitle: String = "",
    /** "Type what the TV shows under Pair device with pairing code." */
    val debugText: String = "",
    /** "Wi-Fi pairing code" */
    val fieldDebugCode: String = "",
    /** "Port" */
    val fieldDebugPort: String = "",
    /** "The number after the colon in IP address & Port." */
    val debugPortHint: String = "",
    /** "Pair" */
    val debugSend: String = "",
    /** "Sent to Living Room TV" */
    val debugSentTitle: String = "",
    /** "Keep the code on the TV until it says it's paired." */
    val debugSentText: String = "",
    /** "Type the six-digit code and the port." */
    val debugMissing: String = "",
    /** Export Diagnostics on a phone (brief 5.17): "Diagnostics from Living Room TV" */
    val diagnosticsTitle: String = "",
    /** "Recent logs from Plasma, with logins, passwords and server addresses taken out." */
    val diagnosticsText: String = "",
    /** "Download" */
    val diagnosticsDownload: String = "",
    /** "To report a problem, open an issue on GitHub and attach the file." */
    val diagnosticsReportText: String = "",
    /** "Report a Problem on GitHub" */
    val diagnosticsReport: String = "",
    /** Back Up Settings on a phone (brief 5.17, #108): "Backup of Living Room TV" */
    val backupTitle: String = "",
    /** "Plasma's settings, sources, favorites and profiles, in one file. To put them back, use Restore from Backup." */
    val backupText: String = "",
    /** "Download" */
    val backupDownload: String = "",
    /** Restore from Backup on a phone: "Restore Living Room TV" */
    val restoreTitle: String = "",
    /** "Choose a backup of Plasma to send to the TV. The TV shows what it replaces before anything changes." */
    val restoreText: String = "",
    /** "Backup file" */
    val restoreFile: String = "",
    /** "Passphrase" */
    val restorePassphrase: String = "",
    /** "Only if the backup has logins. You can type it on the TV instead." */
    val restorePassphraseHint: String = "",
    /** "Send to Living Room TV" */
    val restoreSend: String = "",
    /** "Choose a backup file." */
    val restoreNoFile: String = "",
    /** "Sent to Living Room TV" */
    val restoreSentTitle: String = "",
    /** "Check what the backup replaces on the TV, then choose Restore." */
    val restoreSentText: String = "",
    /** Add a Source › Advanced on the phone (#122): "Advanced", folded until opened. */
    val advanced: String = "",
    /** Advanced's labels, by [PhoneField.key]: "STB model", "Time zone"… */
    val fieldLabels: Map<String, String> = emptyMap(),
    /** Each choice's options, by [PhoneField.key], the one that keeps the TV's own first. */
    val choices: Map<String, List<PhoneChoice>> = emptyMap(),
)

/** The pages a phone sees, in Plasma's own colours and type (brief 4.33's tokens). */
internal object PhonePage {
    /** Asks for the code on the TV, as the QR code would have given it, for the page at [action]. */
    fun code(texts: PhonePageTexts, error: String? = null, action: String = PhonePurpose.AddSource.path): String = page(texts, texts.codeTitle) {
        append("<h1>").text(texts.codeTitle).append("</h1>")
        append("<p>").text(texts.codeText).append("</p>")
        error(error)
        append("<form method=\"get\" action=\"").attr(action).append("\">")
        append("<label for=\"code\">").text(texts.codeLabel).append("</label>")
        append(
            "<input id=\"code\" name=\"code\" required maxlength=\"12\" autocomplete=\"one-time-code\" " +
                "autocapitalize=\"characters\" autocorrect=\"off\" spellcheck=\"false\" class=\"code\">",
        )
        append("<button>").text(texts.next).append("</button>")
        append("</form>")
    }

    /** The source's fields for [kind], with tabs for the other kinds. Nothing typed is ever filled back in. */
    fun form(texts: PhonePageTexts, kind: SourceKind, code: String, csrf: String, error: String? = null): String = page(texts, texts.title) {
        append("<h1>").text(texts.title).append("</h1>")
        append("<nav>")
        for (tab in SourceKind.entries) {
            append("<a href=\"/pair?code=").attr(code).append("&amp;type=").attr(tab.key).append('"')
            if (tab == kind) append(" aria-current=\"page\"")
            append('>').text(texts.tabOf(tab)).append("</a>")
        }
        append("</nav>")
        error(error)
        append("<form method=\"post\" action=\"/pair\" autocomplete=\"off\">")
        append("<input type=\"hidden\" name=\"code\" value=\"").attr(code).append("\">")
        append("<input type=\"hidden\" name=\"csrf\" value=\"").attr(csrf).append("\">")
        append("<input type=\"hidden\" name=\"type\" value=\"").attr(kind.key).append("\">")
        for (field in kind.fields) field(texts, field)
        // What the TV has under Add a Source › Advanced, folded as it is there (#122).
        if (kind.advanced.isNotEmpty() && texts.advanced.isNotEmpty()) {
            append("<details><summary>").text(texts.advanced).append("</summary>")
            for (field in kind.advanced) if (field.choice) choice(texts, field) else field(texts, field)
            append("</details>")
        }
        append("<button>").text(texts.send).append("</button>")
        append("</form>")
    }

    /**
     * The TV's wireless debugging pairing code and port (brief 4.24), typed while the TV's dialog
     * stays open. Nothing typed is filled back in.
     */
    fun debugForm(texts: PhonePageTexts, code: String, csrf: String, error: String? = null): String = page(texts, texts.debugTitle) {
        append("<h1>").text(texts.debugTitle).append("</h1>")
        append("<p>").text(texts.debugText).append("</p>")
        error(error)
        append("<form method=\"post\" action=\"").attr(PhonePurpose.DebugPairing.path).append("\" autocomplete=\"off\">")
        append("<input type=\"hidden\" name=\"code\" value=\"").attr(code).append("\">")
        append("<input type=\"hidden\" name=\"csrf\" value=\"").attr(csrf).append("\">")
        append("<label for=\"pairing\">").text(texts.fieldDebugCode).append("</label>")
        append("<input id=\"pairing\" name=\"pairing\" type=\"text\" inputmode=\"numeric\" pattern=\"[0-9]{6}\" maxlength=\"6\" autocomplete=\"one-time-code\" required class=\"code\">")
        append("<label for=\"port\">").text(texts.fieldDebugPort).append("</label>")
        append("<input id=\"port\" name=\"port\" type=\"text\" inputmode=\"numeric\" pattern=\"[0-9]{1,5}\" maxlength=\"5\" required>")
        append("<div class=\"hint\">").text(texts.debugPortHint).append("</div>")
        append("<button>").text(texts.debugSend).append("</button>")
        append("</form>")
    }

    /**
     * Export Diagnostics on a phone (brief 5.17 and 6.11): the bundle to download from [filePath],
     * and where to report a problem with it, at [reportUrl].
     */
    fun diagnostics(texts: PhonePageTexts, code: String, csrf: String, filePath: String, reportUrl: String?): String = page(texts, texts.diagnosticsTitle) {
        append("<h1>").text(texts.diagnosticsTitle).append("</h1>")
        append("<p>").text(texts.diagnosticsText).append("</p>")
        append("<form method=\"get\" action=\"").attr(filePath).append("\">")
        append("<input type=\"hidden\" name=\"code\" value=\"").attr(code).append("\">")
        append("<input type=\"hidden\" name=\"csrf\" value=\"").attr(csrf).append("\">")
        append("<button>").text(texts.diagnosticsDownload).append("</button>")
        append("</form>")
        if (reportUrl != null) {
            append("<p class=\"report\">").text(texts.diagnosticsReportText).append("</p>")
            append("<a class=\"link\" href=\"").attr(reportUrl).append("\" rel=\"noreferrer\">").text(texts.diagnosticsReport).append("</a>")
        }
    }

    /** Back Up Settings on a phone (#108): the backup to download from [filePath]. */
    fun backup(texts: PhonePageTexts, code: String, csrf: String, filePath: String): String = page(texts, texts.backupTitle) {
        append("<h1>").text(texts.backupTitle).append("</h1>")
        append("<p>").text(texts.backupText).append("</p>")
        append("<form method=\"get\" action=\"").attr(filePath).append("\">")
        append("<input type=\"hidden\" name=\"code\" value=\"").attr(code).append("\">")
        append("<input type=\"hidden\" name=\"csrf\" value=\"").attr(csrf).append("\">")
        append("<button>").text(texts.backupDownload).append("</button>")
        append("</form>")
    }

    /**
     * Restore from Backup on a phone (#108): a backup file to send, and its passphrase if it has
     * logins, which can be typed on the TV instead. Nothing chosen is filled back in.
     */
    fun restore(texts: PhonePageTexts, code: String, csrf: String, error: String? = null): String = page(texts, texts.restoreTitle) {
        append("<h1>").text(texts.restoreTitle).append("</h1>")
        append("<p>").text(texts.restoreText).append("</p>")
        error(error)
        append("<form method=\"post\" action=\"").attr(PhonePurpose.Restore.path).append("\" enctype=\"multipart/form-data\" autocomplete=\"off\">")
        append("<input type=\"hidden\" name=\"code\" value=\"").attr(code).append("\">")
        append("<input type=\"hidden\" name=\"csrf\" value=\"").attr(csrf).append("\">")
        append("<label for=\"backup\">").text(texts.restoreFile).append("</label>")
        append("<input id=\"backup\" name=\"backup\" type=\"file\" accept=\".json,application/json\" required>")
        append("<label for=\"passphrase\">").text(texts.restorePassphrase).append("</label>")
        append("<input id=\"passphrase\" name=\"passphrase\" type=\"password\" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\">")
        append("<div class=\"hint\">").text(texts.restorePassphraseHint).append("</div>")
        append("<button>").text(texts.restoreSend).append("</button>")
        append("</form>")
    }

    /** The backup is on the TV, which asks before it replaces anything. */
    fun uploaded(texts: PhonePageTexts): String = page(texts, texts.restoreSentTitle) {
        append("<div class=\"done\" aria-hidden=\"true\">&#10003;</div>")
        append("<h1>").text(texts.restoreSentTitle).append("</h1>")
        append("<p>").text(texts.restoreSentText).append("</p>")
    }

    /** The code is on its way to the TV. */
    fun debugSent(texts: PhonePageTexts): String = page(texts, texts.debugSentTitle) {
        append("<div class=\"done\" aria-hidden=\"true\">&#10003;</div>")
        append("<h1>").text(texts.debugSentTitle).append("</h1>")
        append("<p>").text(texts.debugSentText).append("</p>")
    }

    /** The phone's part is done. */
    fun sent(texts: PhonePageTexts): String = page(texts, texts.sentTitle) {
        append("<div class=\"done\" aria-hidden=\"true\">&#10003;</div>")
        append("<h1>").text(texts.sentTitle).append("</h1>")
        append("<p>").text(texts.sentText).append("</p>")
    }

    fun notFound(texts: PhonePageTexts): String = page(texts, texts.title) {
        append("<h1>").text(texts.title).append("</h1>")
        append("<p>").text(texts.notFound).append("</p>")
    }

    private fun StringBuilder.field(texts: PhonePageTexts, field: PhoneField) {
        val label = when (field) {
            PhoneField.Name -> texts.fieldName
            PhoneField.Portal -> texts.fieldPortal
            PhoneField.Mac -> texts.fieldMac
            PhoneField.Server -> texts.fieldServer
            PhoneField.Username -> texts.fieldUsername
            PhoneField.Password -> texts.fieldPassword
            PhoneField.Playlist -> texts.fieldPlaylist
            PhoneField.Guide -> texts.fieldGuide
            else -> texts.fieldLabels[field.key] ?: field.key
        }
        append("<label for=\"").attr(field.key).append("\">").text(label).append("</label>")
        append("<input id=\"").attr(field.key).append("\" name=\"").attr(field.key).append('"')
        append(if (field.secret) " type=\"password\"" else " type=\"text\"")
        if (field.address) append(" inputmode=\"url\"")
        if (field != PhoneField.Name) append(" autocapitalize=\"none\" autocorrect=\"off\" spellcheck=\"false\"")
        if (field.required) append(" required")
        append('>')
        if (field == PhoneField.Mac) append("<div class=\"hint\">").text(texts.macHint).append("</div>")
    }

    /** A choice of Advanced's (#122), its first option keeping what the TV has. */
    private fun StringBuilder.choice(texts: PhonePageTexts, field: PhoneField) {
        append("<label for=\"").attr(field.key).append("\">").text(texts.fieldLabels[field.key] ?: field.key).append("</label>")
        append("<select id=\"").attr(field.key).append("\" name=\"").attr(field.key).append("\">")
        for (option in texts.choices[field.key].orEmpty()) {
            append("<option value=\"").attr(option.value).append("\">").text(option.label).append("</option>")
        }
        append("</select>")
    }

    private fun StringBuilder.error(error: String?) {
        if (error != null) append("<div class=\"error\" role=\"alert\">").text(error).append("</div>")
    }

    private fun PhonePageTexts.tabOf(kind: SourceKind): String = when (kind) {
        SourceKind.Stalker -> stalker
        SourceKind.Xtream -> xtream
        SourceKind.M3u -> m3u
    }

    private fun page(texts: PhonePageTexts, title: String, content: StringBuilder.() -> Unit): String = buildString {
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<meta name=\"referrer\" content=\"no-referrer\">")
        append("<title>").text(title).append("</title>")
        append("<style>").append(STYLE).append("</style></head><body><main>")
        append("<header><span class=\"mark\">plasma</span><span class=\"tv\">").text(texts.tvName).append("</span></header>")
        content()
        append("<footer>").text(texts.note).append("</footer>")
        append("</main></body></html>")
    }

    /** Text between tags, escaped. */
    private fun StringBuilder.text(value: String): StringBuilder = escape(value)

    /** An attribute's value, escaped. */
    private fun StringBuilder.attr(value: String): StringBuilder = escape(value)

    private fun StringBuilder.escape(value: String): StringBuilder {
        for (char in value) {
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
        return this
    }

    /** `bg`, `surface1`, the text greys and white for the primary action, as on the TV. */
    private val STYLE = """
        :root{color-scheme:dark}
        *{box-sizing:border-box}
        body{margin:0;background:#0B0B0D;color:#F5F5F7;font:16px/1.45 "Instrument Sans",system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-text-size-adjust:100%}
        main{max-width:440px;margin:0 auto;padding:20px 20px 36px}
        header{display:flex;justify-content:space-between;align-items:center;margin-bottom:28px}
        .mark{font-size:21px;font-weight:600;letter-spacing:-.02em}
        .tv{color:#A1A1A8;font-size:14px}
        .tv::before{content:"\25CF";color:#30D158;margin-right:6px}
        h1{font-size:26px;font-weight:600;letter-spacing:-.01em;margin:0 0 8px}
        p{color:#A1A1A8;margin:0 0 20px}
        nav{display:flex;gap:4px;background:#161618;border-radius:14px;padding:4px;margin:16px 0 8px}
        nav a{flex:1;display:flex;align-items:center;justify-content:center;min-height:44px;padding:6px 4px;border-radius:10px;color:#A1A1A8;text-decoration:none;font-size:14px;font-weight:500;text-align:center}
        nav a[aria-current]{background:rgba(255,255,255,.12);color:#F5F5F7;font-weight:600}
        label{display:block;color:#A1A1A8;font-size:14px;margin:16px 0 6px}
        input{display:block;width:100%;height:50px;padding:0 14px;border:1px solid rgba(255,255,255,.12);border-radius:12px;background:#161618;color:#F5F5F7;font:inherit;font-size:17px}
        input:focus,select:focus{outline:none;border-color:rgba(255,255,255,.55)}
        select{display:block;width:100%;height:50px;padding:0 14px;border:1px solid rgba(255,255,255,.12);border-radius:12px;background:#161618;color:#F5F5F7;font:inherit;font-size:17px}
        details{margin-top:24px;border-top:1px solid rgba(255,255,255,.12);padding-top:4px}
        summary{min-height:44px;display:flex;align-items:center;color:#A1A1A8;font-size:15px;font-weight:600;cursor:pointer}
        input.code{font-size:24px;font-weight:600;letter-spacing:.2em;text-transform:uppercase}
        input[type=file]{height:auto;padding:13px 14px;font-size:15px;color:#A1A1A8}
        .hint{color:#6E6E75;font-size:13px;margin-top:6px}
        button{display:block;width:100%;height:52px;margin-top:28px;border:0;border-radius:14px;background:#F5F5F7;color:#0B0B0D;font:inherit;font-size:17px;font-weight:600}
        .error{margin:16px 0 0;padding:12px 14px;border-radius:12px;background:rgba(255,69,58,.14);color:#FF6961;font-size:15px}
        .done{width:56px;height:56px;margin:12px 0 18px;border-radius:50%;background:rgba(48,209,88,.16);color:#30D158;font-size:28px;line-height:56px;text-align:center}
        .report{margin:36px 0 0}
        .link{display:block;height:52px;margin-top:4px;border:1px solid rgba(255,255,255,.24);border-radius:14px;color:#F5F5F7;font-size:17px;font-weight:600;line-height:50px;text-align:center;text-decoration:none}
        footer{margin-top:32px;color:#6E6E75;font-size:13px;text-align:center}
    """.trimIndent().replace("\n", "")
}
