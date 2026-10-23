package io.freemytv;

/**
 * The screensaver on Android 11 and 12. There the system's DreamActivity runs in the app's default process and only works
 * with the DreamService in that same process, which can't also hold a home activity (#10). So Android 11/12 get this
 * copy in the main process; Android 13+ keeps Screensaver in :home (values-v33/bools.xml).
 */
public class ScreensaverAlone extends Screensaver {
}
