// SPDX-License-Identifier: Apache-2.0
package ca.gureet.plasma.remote.app.tv

// The desktop target only draws the screens for tests, from the demo TV: it never reaches a TV.

actual suspend fun openTvSocket(endpoint: TvEndpoint, pinned: String?, seen: (String) -> Unit): TvSocket =
    throw UnsupportedOperationException("The desktop target has no TV")

actual suspend fun fetchFromTv(endpoint: TvEndpoint, pinned: String, path: String): ByteArray? = null
