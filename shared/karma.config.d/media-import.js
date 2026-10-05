{
    // Bounded stream fixtures process MiBs of bytes on JS/Wasm; Mocha's 2s default
    // is not a product performance budget. Keep every size/cancellation assertion.
    config.client = config.client || {};
    config.client.mocha = Object.assign({}, config.client.mocha, { timeout: 10000 });
    const path = require("path");
    const fs = require("fs");
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
