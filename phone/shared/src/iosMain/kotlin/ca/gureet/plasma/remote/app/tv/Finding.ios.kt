// SPDX-License-Identifier: Apache-2.0
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package ca.gureet.plasma.remote.app.tv

import ca.gureet.plasma.remote.protocol.RemoteAdvert
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * The paired TVs, kept in the Keychain (brief 5.22: tokens in the iOS Keychain), on this device
 * only and readable once it's been unlocked after a restart.
 */
class KeychainVault(private val account: String = "paired-tvs") : Vault {
    private fun query(): CFMutableDictionaryRef? = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)?.also {
        CFDictionaryAddValue(it, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(it, kSecAttrService, CFBridgingRetain(SERVICE))
        CFDictionaryAddValue(it, kSecAttrAccount, CFBridgingRetain(account))
    }

    override fun read(): ByteArray? = memScoped {
        val query = query() ?: return null
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null
        (CFBridgingRelease(result.value) as? NSData)?.toBytes()
    }

    override fun write(bytes: ByteArray?) {
        query()?.let {
            SecItemDelete(it)
            CFRelease(it)
        }
        if (bytes == null) return
        val query = query() ?: return
        CFDictionaryAddValue(query, kSecValueData, CFBridgingRetain(bytes.toNSData()))
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(query, null)
        CFRelease(query)
    }

    private companion object {
        const val SERVICE = "ca.gureet.plasma.remote"
    }
}

/**
 * TVs on the Wi-Fi through Bonjour's NSNetService, which gives each service's host name, port and
 * TXT entries in one step; Network's NWBrowser gives a service without its address, which only a
 * connection then finds. Deprecated since iOS 15 and still working. Its callbacks come on the
 * run loop of the thread that started it: collect on the main thread.
 */
class BonjourTvFinder : TvFinder {
    override fun tvs(): Flow<List<FoundTv>> = callbackFlow {
        val found = LinkedHashMap<String, FoundTv>()
        val resolving = mutableListOf<NSNetService>()
        send(emptyList())

        val serviceDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
            override fun netServiceDidResolveAddress(sender: NSNetService) {
                val txt = sender.TXTRecordData()?.let { NSNetService.dictionaryFromTXTRecordData(it) } ?: return
                val entries = txt.entries.associate { (key, value) ->
                    key.toString() to (value as? NSData)?.let { NSString.create(it, NSUTF8StringEncoding)?.toString() }
                }
                val advert = RemoteAdvert.fromTxt(entries) ?: return
                val host = sender.hostName?.removeSuffix(".") ?: return
                found[sender.name] = FoundTv(advert, host)
                trySend(found.values.toList())
            }
        }
        val browserDelegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
            @ObjCSignatureOverride
            override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
                resolving += didFindService
                didFindService.delegate = serviceDelegate
                didFindService.resolveWithTimeout(5.0)
            }

            @ObjCSignatureOverride
            override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
                resolving -= didRemoveService
                if (found.remove(didRemoveService.name) != null) trySend(found.values.toList())
            }
        }
        val browser = NSNetServiceBrowser()
        browser.delegate = browserDelegate
        browser.searchForServicesOfType("${RemoteAdvert.SERVICE_TYPE}.", inDomain = "local.")
        awaitClose {
            browser.stop()
            resolving.forEach { it.stop() }
        }
    }
}

private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

private fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
