{
    // Bounded stream fixtures process MiBs of bytes on JS/Wasm; Mocha's 2s default
    // is not a product performance budget. Keep every size/cancellation assertion.
    config.client = config.client || {};
    config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 10000 });
    const path = require("path");
    const fs = require("fs");
    // karma-webpack otherwise leaves large _karma_webpack_* folders outside Gradle outputs.
    // __dirname is the generated test package inside this build's JS or Wasm output.
    config.webpack = config.webpack || {};
    config.webpack.output = Object.assign({}, config.webpack.output, { path: path.join(__dirname, "karma-webpack") });
    // Isolated Gradle build outputs can live outside the repository during concurrent development.
    let root = process.env.BOARDERLESS_TEST_REPO_ROOT || process.cwd();
    while (!fs.existsSync(path.join(root, "settings.gradle.kts"))) {
        const parent = path.dirname(root);
        if (parent === root) throw new Error("Cannot locate BoarderLess browser fixtures");
        root = parent;
    }
    config.files.unshift(
        { pattern: path.join(root, "webApp/src/webMain/resources/video-playback.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/media-import.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/image-export.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/image-export-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/scheme-bundle.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/scheme-bundle-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/local-originals.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/local-originals-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/durable-records.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/durable-records-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/draft-import.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/draft-import-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/media-import-fixture.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/draft-download-fixture.js"), included: true, served: true, watched: false }
    );
    // Compose resource URLs are relative to the page; Karma does not serve them automatically.
    const composeResources = path.join(root, "shared/src/commonMain/composeResources");
    config.files.push({ pattern: path.join(composeResources, "**/*"), included: false, served: true, watched: false });
    config.proxies = Object.assign({}, config.proxies, {
        "/composeResources/boarderless.shared.generated.resources/": "/absolute" + composeResources.replace(/\\/g, "/") + "/"
    });
}
