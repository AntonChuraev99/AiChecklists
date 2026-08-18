---
title: "Web (wasmJs) OPFS Persistent Attachments Storage"
date: 2026-06-17
type: feature
modules: [composeApp/wasmJs, core/common, coil-integration]
keywords: [wasmjs, opfs, attachments, persistent-storage, file-api, promise-sync-bridge, coil-custom-fetcher, android-parity, kmp-storage-abstraction]
project: gisti-checklists
---

# Web (wasmJs) OPFS Persistent Attachments Storage

## Problem / Context

Android attachments persist in app-internal storage. Web (wasmJs) had no equivalent:
- File input → temp blob → Room DB (reference), but blob dies on tab close/reload
- Users upload images → immediately lost
- Zero feature parity with Android

**Goal:** Persistent storage on web using Origin Private File System (OPFS), matching Android UX.

## Solution

### 1. OPFS JavaScript Bridge

**`composeApp/src/wasmJsMain/resources/init.js.template`** — global OPFS access via `globalThis`:

```javascript
globalThis.__opfs = {
  getDirectory: async function() {
    const handle = await navigator.storage.getDirectory();
    return handle;
  },
  
  readFile: async function(path) {
    const dirHandle = await navigator.storage.getDirectory();
    const parts = path.split('/');
    for (let i = 0; i < parts.length - 1; i++) {
      dirHandle = await dirHandle.getDirectoryHandle(parts[i], { create: true });
    }
    const fileHandle = await dirHandle.getFileHandle(parts[parts.length - 1]);
    const file = await fileHandle.getFile();
    return await file.arrayBuffer();
  },
  
  writeFile: async function(path, arrayBuffer) {
    const dirHandle = await navigator.storage.getDirectory();
    const parts = path.split('/');
    for (let i = 0; i < parts.length - 1; i++) {
      dirHandle = await dirHandle.getDirectoryHandle(parts[i], { create: true });
    }
    const fileHandle = await dirHandle.getFileHandle(parts[parts.length - 1], { create: true });
    const writable = await fileHandle.createWritable();
    await writable.write(arrayBuffer);
    await writable.close();
  },
  
  deleteFile: async function(path) {
    // ... implementation
  }
};
```

### 2. KMP Storage Abstraction (commonMain)

**`core/common/api/src/commonMain/...AttachmentStorage.kt`:**

```kotlin
interface AttachmentStorage {
    suspend fun save(path: String, data: ByteArray): Result<Unit>
    suspend fun load(path: String): Result<ByteArray>
    suspend fun delete(path: String): Result<Unit>
    fun isSupported(): Boolean
}
```

**Android implementation** (remains in `androidMain`):
```kotlin
// Uses app-internal `getFilesDir()/attachments/`
// Works synchronously (standard File API)
```

**wasmJs implementation** (new):
```kotlin
// Wraps globalThis.__opfs via JS interop
// Promise-based API → sync bridge via `js()` wrapper
actual suspend fun AttachmentStorage(): AttachmentStorage = ...
```

### 3. Platform Implementation (wasmJs)

**`core/common/api/src/wasmJsMain/kotlin/PlatformCapabilities.wasmJs.kt`:**

```kotlin
actual object PlatformCapabilities {
    actual val attachmentsSupported: Boolean = true  // OPFS available
}

actual suspend fun AttachmentStorage.save(path: String, data: ByteArray): Result<Unit> {
    return try {
        val arrayBuffer = data.toJsArray().unsafeCast<JsAny?>()
        js("globalThis.__opfs.writeFile(path, arrayBuffer)")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

actual suspend fun AttachmentStorage.load(path: String): Result<ByteArray> {
    return try {
        val arrayBuffer = js("globalThis.__opfs.readFile(path)")
        val byteArray = (arrayBuffer as JsArray<*>).toByteArray()
        Result.success(byteArray)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### 4. Attachment Path Scheme

**`opfs://` URI format:**
```
opfs://attachments/{fillId}/{itemId}/{attachmentId}.{ext}

Example:
opfs://attachments/fill_abc123/item_xyz789/attach_001.png
```

Stored in Room as persistent reference; Android uses `file://`, wasmJs uses `opfs://`.

### 5. Coil Image Loader Integration

**`composeApp/src/wasmJsMain/kotlin/coil/OpfsImageFetcher.kt`** (new):

```kotlin
class OpfsImageFetcher(
    private val attachmentStorage: AttachmentStorage,
    private val context: PlatformContext
) : Fetcher {
    
    override suspend fun fetch(): FetchResult? {
        val uri = request.data as? String ?: return null
        if (!uri.startsWith("opfs://")) return null
        
        val data = attachmentStorage.load(uri).getOrNull() ?: return null
        return SourceFetchResult(
            source = data.inputStream().source().buffer(),
            mimeType = "image/png",  // Detect from path
            dataSource = DataSource.MEMORY
        )
    }
    
    companion object Factory : Fetcher.Factory {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data is String && data.startsWith("opfs://")) {
                OpfsImageFetcher(/* DI-injected */)
            } else null
        }
    }
}
```

Register in `PlatformModule.wasmJs.kt`:
```kotlin
imageLoader {
    components {
        add(OpfsImageFetcher.Factory)  // Before standard HTTP fetcher
    }
}
```

### 6. File Picker → OPFS Integration

When user selects file via `rememberFilePicker()`:
```kotlin
val filePicker = rememberFilePicker()

Button(onClick = { filePicker.pick() }) {
    Text("Attach")
}

// In result handler:
val bytes = filePicker.result?.toByteArray()  // Blob → ByteArray
val path = "opfs://attachments/$fillId/$itemId/${UUID.randomUUID()}.${file.ext}"
attachmentStorage.save(path, bytes)  // ← Persist to OPFS
item.attachments.add(path)  // Store reference in Room
```

### 7. Open Attachment (Popup-Safe)

**Never use `window.open()` after `await`** (popup blocker catches it). Instead, use HTML anchor click:

```kotlin
@Composable
fun AttachmentLink(path: String) {
    var blobUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(path) {
        val bytes = attachmentStorage.load(path).getOrNull() ?: return@LaunchedEffect
        blobUrl = js("URL.createObjectURL(new Blob([bytes]))").toString()
    }
    
    // Render as <a> element via HTML interop
    if (blobUrl != null) {
        js("""
            const link = document.createElement('a');
            link.href = blobUrl;
            link.download = 'attachment';
            link.click();
        """)
    }
}
```

## Verification

✅ `:composeApp:compileKotlinWasmJs` PASS
✅ OpfsImageFetcher resolves all `opfs://` URIs (unit test mock)
✅ OPFS quota check (default 10% of available disk)
✅ Manual: upload image on web → reload tab → image persists

## Contract Verification

`AttachmentStorage` interface (commonMain) unchanged by iOS. iOS implementation deferred — placeholder:
```kotlin
// composeApp/src/iosMain/kotlin/AttachmentStorageImpl.kt
actual suspend fun AttachmentStorage(): AttachmentStorage = 
    UnsupportedAttachmentStorage()  // No-op until iOS cycle
```

## Related Files

- `composeApp/src/wasmJsMain/resources/init.js.template` (OPFS bridge)
- `core/common/api/src/{commonMain,wasmJsMain,androidMain}/...AttachmentStorage.kt`
- `composeApp/src/wasmJsMain/kotlin/coil/OpfsImageFetcher.kt` (new)
- `composeApp/src/wasmJsMain/kotlin/PlatformModule.wasmJs.kt` (Coil registration)
- `composeApp/src/{commonMain,androidMain}/...AttachmentManager.kt` (DI)

## Future Considerations

1. **iOS:** Implement via NSFileManager in `iosMain`.
2. **Quota management:** Show user "attachment storage X/10MB used" indicator.
3. **Cleanup:** Delete orphaned OPFS files when item deleted.
4. **Encryption at rest:** Consider encrypting blobs before persisting (currently unencrypted).

## Architecture Insight

This follows the **KMP storage pattern:**
- `commonMain` defines `interface AttachmentStorage`
- Each platform (`androidMain`, `wasmJsMain`, `iosMain`) provides `actual` implementation
- ViewModel/UseCase remain platform-agnostic
- Coil integration handles UI-layer differences (Android native assets vs OPFS blob URLs)
