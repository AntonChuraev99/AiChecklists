package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * Persists the navigation A/B arm ("control" | "v2") assigned to this install.
 *
 * Why persist at all: the arm gates the whole navigation shell on every launch, and most launches
 * never await a Remote Config activation. Reading RC live would render one arm on the first launch
 * after an update and the other on the next — a shell that changes by itself, and one install
 * counted in both arms. The first *assigned* arm is stored here and read back before RC is ever
 * consulted again.
 *
 * Global (NOT keyed per-uid, mirroring [AiExperimentPrefsRepository]): the assignment belongs to
 * the install, not to a signed-in identity, and must survive sign-out. Never throws; the empty
 * string is the "absent" sentinel and is mapped back to null by [getNavArm].
 */
interface NavExperimentPrefsRepository {
    /** Persisted arm wire value ("control" | "v2"), or null if no arm has been assigned yet. */
    suspend fun getNavArm(): String?

    /**
     * Stores the arm. Idempotent.
     *
     * Callers MUST only pass an arm Remote Config actually assigned — writing the
     * "RC not fetched yet" fallback here would pin the install to control permanently.
     *
     * Passing the EMPTY string clears the assignment (it is the absent sentinel, mapped back to null
     * by [getNavArm]), which is how the debug screen removes a forced arm. Production code must never
     * do this: it would un-stick an install that already reported its arm to analytics.
     */
    suspend fun setNavArm(arm: String)
}
