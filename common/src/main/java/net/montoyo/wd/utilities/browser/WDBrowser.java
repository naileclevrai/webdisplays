package net.montoyo.wd.utilities.browser;

import com.cinemamod.mcef.MCEF;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.utilities.browser.handlers.js.queries.ElementCenterQuery;
import net.montoyo.wd.utilities.browser.handlers.js.JSQueryHandler;
import net.montoyo.wd.utilities.data.BlockSide;
import org.cef.browser.CefBrowser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public interface WDBrowser {
    Set<CefBrowser> ACTIVE_BROWSERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Create a browser.
     * @param url The URL to display
     * @param transparent Whether the browser should be transparent
     * @return A new browser instance
     */
    static CefBrowser createBrowser(String url, boolean transparent) {
        WDClientBrowser browser = new WDClientBrowser(MCEF.getClient(), url, transparent);
        browser.setCloseAllowed();
        browser.createImmediately();
        registerQueries(browser);
        ACTIVE_BROWSERS.add(browser);
        return browser;
    }

    /**
     * Create a browser using the pool system (fallback to standard creation if pooling not available).
     * @param url The URL to display
     * @param transparent Whether the browser should be transparent
     * @param shared Whether this browser can be shared with other screens (ignored in fallback)
     * @param owner The object requesting the browser (for tracking, ignored in fallback)
     * @return A browser instance
     */
    static CefBrowser createBrowserFromPool(String url, boolean transparent, boolean shared, Object owner) {
        // MCEF's pooling system returns MCEFBrowser instances, not WDClientBrowser
        // Since we need WDClientBrowser for query handlers, we can't use pooling
        // TODO: Modify MCEF to support custom browser types in the pool
        return createBrowser(url, transparent);
    }

    /**
     * Release a browser back to the pool (or close it if pooling not available).
     * @param browser The browser to release
     * @param owner The object releasing the browser
     */
    static void releaseBrowser(CefBrowser browser, Object owner) {
        if (browser == null)
            return;

        // Pooling is not used for WDClientBrowser, so always close to avoid leaks.
        if (browser instanceof com.cinemamod.mcef.MCEFBrowser mcefBrowser) {
            ACTIVE_BROWSERS.remove(browser);
            mcefBrowser.close(true);
            return;
        }

        try {
            ACTIVE_BROWSERS.remove(browser);
            browser.close(true);
        } catch (Throwable ignored) {
        }
    }

    static void closeAllBrowsers() {
        for (CefBrowser browser : ACTIVE_BROWSERS) {
            try {
                if (browser instanceof com.cinemamod.mcef.MCEFBrowser mcefBrowser) {
                    mcefBrowser.close(true);
                } else {
                    browser.close(true);
                }
            } catch (Throwable ignored) {
            }
        }
        ACTIVE_BROWSERS.clear();
    }

    static void registerQueries(WDBrowser browser) {
        Map<String, JSQueryHandler> handlerMap = browser.queryHandlers();

        JSQueryHandler handler;

        handler = browser.focusedElement();
        handlerMap.put(handler.getName(), handler);
        handler = browser.pointerLockElement();
        handlerMap.put(handler.getName(), handler);
    }

    HashMap<String, JSQueryHandler> queryHandlers();
    ElementCenterQuery focusedElement();
    ElementCenterQuery pointerLockElement();

    void setBe(ScreenBlockEntity blockEntity, BlockSide side);
    ScreenBlockEntity getBe();
    BlockSide getSide();
}
