package com.kelpie.virtualdisplayprobe

/**
 * Main-thread-only single-flight gate for one offline migration run.
 * Repeated WebView onPageFinished callbacks or stale delayed tasks cannot
 * start multiple migrations or count a frame sequence more than once.
 */
internal class ProbeLifecycleGate {
    private var loadClaimed = false
    private var migrationClaimed = false
    private var nextStep = 1

    fun claimLoad(): Boolean {
        if (loadClaimed) return false
        loadClaimed = true
        return true
    }

    fun claimMigration(): Boolean {
        if (!loadClaimed || migrationClaimed) return false
        migrationClaimed = true
        return true
    }

    fun claimStep(sequence: Int): Boolean {
        if (!migrationClaimed || sequence != nextStep || sequence !in 1..21) return false
        nextStep++
        return true
    }
}
