package net.montoyo.wd.utilities.browser;

import com.cinemamod.mcef.MCEF;
import net.montoyo.wd.entity.ScreenBlockEntity;
import net.montoyo.wd.utilities.browser.handlers.js.queries.ElementCenterQuery;
import net.montoyo.wd.utilities.browser.handlers.js.JSQueryHandler;
import net.montoyo.wd.utilities.data.BlockSide;
import org.cef.browser.CefBrowser;

import java.util.HashMap;
import java.util.Map;

public interface WDBrowser {
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
        try {
            if (browser instanceof com.cinemamod.mcef.MCEFBrowser mcefBrowser) {
                MCEF.releaseBrowserToPool(mcefBrowser, owner);
            }
        } catch (NoSuchMethodError e) {
            // Pooling not available, just close the browser
            if (browser instanceof com.cinemamod.mcef.MCEFBrowser mcefBrowser) {
                mcefBrowser.close(true);
            }
        }
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
