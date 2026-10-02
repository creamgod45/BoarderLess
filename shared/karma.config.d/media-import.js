{
    const path = require("path");
    const fs = require("fs");
    let root = process.cwd();
    while (!fs.existsSync(path.join(root, "settings.gradle.kts"))) {
        const parent = path.dirname(root);
        if (parent === root) throw new Error("Cannot locate BoarderLess browser fixtures");
        root = parent;
    }
    config.files.unshift(
        { pattern: path.join(root, "webApp/src/webMain/resources/video-playback.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "webApp/src/webMain/resources/media-import.js"), included: true, served: true, watched: false },
        { pattern: path.join(root, "shared/src/webTest/resources/media-import-fixture.js"), included: true, served: true, watched: false }
    );
}
