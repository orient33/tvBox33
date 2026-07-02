package com.github.tvbox.osc.server;

/**
 * Stub for Guard spider's ProxyOrigin URL discovery.
 * The spider calls {@link #getAddress(boolean)} to construct proxy:// URLs
 * that route through the local NanoHTTPD server.
 */
public class ControlManager {

    private static final ControlManager INSTANCE = new ControlManager();

    public static ControlManager get() {
        return INSTANCE;
    }

    public String getAddress(boolean local) {
        if (local) {
            return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
        }
        return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
    }
}
