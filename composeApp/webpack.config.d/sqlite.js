// SAH Pool VFS (migrated in ba2b585d) does NOT require SharedArrayBuffer,
// so COOP/COEP headers are no longer needed. Removing them unblocks
// Firebase Auth signInWithPopup() — COOP: same-origin prevents the SDK
// from polling popup.closed, causing the auth handler to hang.
//
// The "Ignoring inability to install OPFS sqlite3_vfs" warning in console
// is expected and harmless — SAH Pool VFS is the active driver.
