package com.kelpie.browser.device

/**
 * Android gives each user/profile a distinct Linux UID range. The TCP/IP port
 * namespace is shared, however, so Samsung Dual App clones must not compete
 * for the owner's listener. The primary profile retains the historical port.
 */
internal object AndroidProfilePort {
    private const val UID_RANGE_PER_USER = 100_000

    fun userId(uid: Int): Int = (uid / UID_RANGE_PER_USER).coerceAtLeast(0)

    fun forProfile(
        basePort: Int,
        uid: Int,
    ): Int {
        require(basePort in 1024..65279) { "Invalid local service port" }
        val user = userId(uid)
        return basePort + (user % 256)
    }
}
