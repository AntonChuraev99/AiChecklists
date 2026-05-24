;(function(config) {
    var port = typeof process !== 'undefined' && process.env.WASM_DEV_PORT;
    if (port && config.devServer) {
        config.devServer.port = parseInt(port, 10);
    }
})(config);
