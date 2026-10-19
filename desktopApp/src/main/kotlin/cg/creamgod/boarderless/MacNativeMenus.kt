package cg.creamgod.boarderless

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicReference

/**
 * The parts of the macOS menu bar that Swing cannot express, done through the Objective-C runtime.
 *
 * - The Window menu is a native NSMenu registered as `NSApp.windowsMenu`. Its items use the standard
 *   AppKit actions, so macOS treats it like any Mac app's Window menu and adds its own items: Fill,
 *   Center, Move & Resize (including moving to another display), Full Screen Tile and the window
 *   list, in the languages the app bundle declares.
 * - The app menu that AWT creates is English only; its items are retitled in the app's language.
 *
 * AWT rebuilds the main menu (for example when the window is activated), which drops the Window
 * menu or puts menus after it, so [update] is meant to be called repeatedly.
 */
internal object MacNativeMenus {
    data class Titles(
        val window: String,
        val minimize: String,
        val zoom: String,
        val bringAllToFront: String,
        val about: String,
        val services: String,
        val hide: String,
        val hideOthers: String,
        val showAll: String,
        val quit: String,
    )

    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val msgSend by lazy { objc.getFunction("objc_msgSend") }
    private var windowMenuItem: Pointer? = null
    private var windowMenuTitles: Titles? = null

    internal interface UpdateCallback : Callback {
        fun invoke(
            receiver: Pointer,
            action: Pointer,
            argument: Pointer?,
        )
    }

    private val pendingTask = AtomicReference<(() -> Unit)?>(null)

    // Strong references outlive every Objective-C callback and its dispatch object.
    private val callback =
        object : UpdateCallback {
            override fun invoke(
                receiver: Pointer,
                action: Pointer,
                argument: Pointer?,
            ) {
                runCatching { pendingTask.getAndSet(null)?.invoke() }
            }
        }
    private val dispatcher by lazy {
        val className = "BoarderLessNativeMenuDispatcher"
        val type =
            objcClass(className) ?: run {
                val allocated =
                    requireNotNull(objc.getFunction("objc_allocateClassPair").invokePointer(arrayOf(objcClass("NSObject"), className, 0L)))
                check(
                    objc
                        .getFunction(
                            "class_addMethod",
                        ).invokeInt(
                            arrayOf(allocated, selector("boarderlessUpdateMenus:"), CallbackReference.getFunctionPointer(callback), "v@:@"),
                        ) and
                        0xff !=
                        0,
                )
                objc.getFunction("objc_registerClassPair").invokeVoid(arrayOf(allocated))
                allocated
            }
        requireNotNull(send(send(type, "alloc"), "init"))
    }
    private val updates =
        LatestNativeMenuUpdate<Titles>(post = { task ->
            pendingTask.set(task)
            onMainThread(dispatcher, "boarderlessUpdateMenus:", null)
        }, apply = { titles -> updateOnMainThread(titles) })

    /** Never enters AppKit menu code from the AWT event thread. Accessibility may be waiting
     * for AWT from AppKit, so synchronously reading/constructing menus creates a native wait cycle.
     */
    fun update(titles: Titles) {
        updates.offer(titles)
    }

    private fun updateOnMainThread(titles: Titles) {
        runCatching {
            val app = send(objcClass("NSApplication"), "sharedApplication") ?: return
            val mainMenu = send(app, "mainMenu") ?: return
            ensureWindowMenu(app, mainMenu, titles)
            localizeAppMenu(mainMenu, titles)
        }
    }

    private fun ensureWindowMenu(
        app: Pointer,
        mainMenu: Pointer,
        titles: Titles,
    ) {
        val previous = windowMenuItem
        val index = previous?.let { msgSend.invokeLong(arrayOf(mainMenu, selector("indexOfItem:"), it)) } ?: -1L
        // Window is the last menu; AWT may add its menus after this one was installed.
        if (index == itemCount(mainMenu) - 1 && titles == windowMenuTitles) return
        if (index >= 0) sendVoid(mainMenu, "removeItem:", previous)

        val menu = send(send(objcClass("NSMenu"), "alloc"), "initWithTitle:", nsString(titles.window))
        sendVoid(menu, "addItem:", menuItem(titles.minimize, "performMiniaturize:", "m"))
        sendVoid(menu, "addItem:", menuItem(titles.zoom, "performZoom:", ""))
        sendVoid(menu, "addItem:", send(objcClass("NSMenuItem"), "separatorItem"))
        sendVoid(menu, "addItem:", menuItem(titles.bringAllToFront, "arrangeInFront:", ""))
        val item = menuItem(titles.window, null, "")
        sendVoid(item, "setSubmenu:", menu)
        sendVoid(mainMenu, "addItem:", item)
        sendVoid(app, "setWindowsMenu:", menu)
        windowMenuItem = item
        windowMenuTitles = titles
    }

    /** Items are found by their AppKit action, so this also works once they have been retitled. */
    private fun localizeAppMenu(
        mainMenu: Pointer,
        titles: Titles,
    ) {
        if (itemCount(mainMenu) == 0L) return
        val appMenu = send(itemAt(mainMenu, 0), "submenu") ?: return
        for (index in 0 until itemCount(appMenu)) {
            val item = itemAt(appMenu, index) ?: continue
            if (bool(item, "isSeparatorItem") || bool(item, "isAlternate")) continue
            val action =
                msgSend
                    .invokePointer(arrayOf(item, selector("action")))
                    ?.let { objc.getFunction("sel_getName").invokeString(arrayOf(it), false) }
            val title =
                when {
                    index == 0L -> titles.about
                    action == "hide:" -> titles.hide
                    action == "hideOtherApplications:" -> titles.hideOthers
                    action == "unhideAllApplications:" -> titles.showAll
                    send(item, "submenu") != null -> titles.services
                    string(send(item, "keyEquivalent")) == "q" -> titles.quit
                    else -> null
                } ?: continue
            if (string(send(item, "title")) != title) sendVoid(item, "setTitle:", nsString(title))
        }
    }

    private fun menuItem(
        title: String,
        action: String?,
        keyEquivalent: String,
    ): Pointer? =
        send(
            send(objcClass("NSMenuItem"), "alloc"),
            "initWithTitle:action:keyEquivalent:",
            nsString(title),
            action?.let(::selector),
            nsString(keyEquivalent),
        )

    private fun itemCount(menu: Pointer): Long = msgSend.invokeLong(arrayOf(menu, selector("numberOfItems")))

    private fun itemAt(
        menu: Pointer,
        index: Long,
    ): Pointer? = msgSend.invokePointer(arrayOf(menu, selector("itemAtIndex:"), index))

    private fun bool(
        receiver: Pointer,
        selector: String,
    ): Boolean = msgSend.invokeInt(arrayOf(receiver, selector(selector))) and 0xff != 0

    /**
     * AppKit state must change on the main thread. Not waiting: the main thread may be waiting on the
     * AWT event thread, which runs this.
     */
    private fun onMainThread(
        receiver: Pointer,
        selector: String,
        argument: Pointer?,
    ) {
        msgSend.invokeVoid(
            arrayOf(receiver, selector("performSelectorOnMainThread:withObject:waitUntilDone:"), selector(selector), argument, false),
        )
    }

    private fun objcClass(name: String): Pointer? = objc.getFunction("objc_getClass").invokePointer(arrayOf(name))

    private fun selector(name: String): Pointer = objc.getFunction("sel_registerName").invokePointer(arrayOf(name))

    private fun nsString(value: String): Pointer? = send(objcClass("NSString"), "stringWithUTF8String:", value)

    private fun string(nsString: Pointer?): String? = nsString?.let { msgSend.invokeString(arrayOf(it, selector("UTF8String")), false) }

    private fun sendVoid(
        receiver: Pointer?,
        selector: String,
        vararg args: Any?,
    ) {
        if (receiver != null) msgSend.invokeVoid(arrayOf(receiver, selector(selector), *args))
    }

    private fun send(
        receiver: Pointer?,
        selector: String,
        vararg args: Any?,
    ): Pointer? {
        if (receiver == null) return null
        return msgSend.invokePointer(arrayOf(receiver, selector(selector), *args))
    }
}
