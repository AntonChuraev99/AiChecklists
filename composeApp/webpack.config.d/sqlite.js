// Required COOP/COEP headers for OPFS (Origin Private File System) access.
// WebWorkerSQLiteDriver uses OPFS for persistence, which requires SharedArrayBuffer,
// which in turn requires cross-origin isolation via these headers.
//
// CORP (Cross-Origin-Resource-Policy) is also required because COEP=require-corp
// rejects any resource without an explicit CORP header. Compose's wasm/asset bundles
// otherwise fail to load with "blocked by CORP" or wasm streaming truncation errors.
;(function(config) {
    if (config.devServer) {
        config.devServer.headers = config.devServer.headers || [];
        config.devServer.headers.push(
            { key: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
            { key: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
            { key: 'Cross-Origin-Resource-Policy', value: 'cross-origin' }
        );
    }
})(config);
