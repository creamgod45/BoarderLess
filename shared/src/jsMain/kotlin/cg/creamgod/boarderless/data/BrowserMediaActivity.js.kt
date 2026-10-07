package cg.creamgod.boarderless.data

internal actual fun browserBindMediaActivity(
    ownerId: String,
    onChange: (Boolean) -> Unit,
) {
    val bind =
        js(
            """(function(id, changed) {
        const owners = globalThis.boarderlessMediaActivityOwners || (globalThis.boarderlessMediaActivityOwners = new Map());
        if (owners.has(id)) throw new Error('Activity owner already bound');
        let hiddenByPage = false, frozen = false;
        const refresh = () => changed(!document.hidden && !hiddenByPage && !frozen);
        const hide = () => { hiddenByPage = true; refresh(); };
        const show = () => { hiddenByPage = false; refresh(); };
        const freeze = () => { frozen = true; refresh(); };
        const resume = () => { frozen = false; refresh(); };
        document.addEventListener('visibilitychange', refresh);
        document.addEventListener('freeze', freeze);
        document.addEventListener('resume', resume);
        window.addEventListener('pagehide', hide);
        window.addEventListener('pageshow', show);
        owners.set(id, () => {
            document.removeEventListener('visibilitychange', refresh);
            document.removeEventListener('freeze', freeze);
            document.removeEventListener('resume', resume);
            window.removeEventListener('pagehide', hide);
            window.removeEventListener('pageshow', show);
        });
        refresh();
    })""",
        )
    bind(ownerId, onChange)
}

internal actual fun browserUnbindMediaActivity(ownerId: String) {
    val unbind =
        js(
            """(function(id) {
        const owners = globalThis.boarderlessMediaActivityOwners;
        const dispose = owners && owners.get(id);
        if (dispose) { owners.delete(id); dispose(); }
    })""",
        )
    unbind(ownerId)
}
