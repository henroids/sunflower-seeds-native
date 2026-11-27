package dev.nxnative.android;

/**
 * The game's audio focus (GameActivity's AudioFocusRequest), without Android types so the JVM test can run it (AudioFocusJvmTest).
 * A transient loss (a call, a navigation prompt, a notification sound) is followed by a gain from Android once the other app is done. A permanent loss (another app
 * started playing music or a video, which a frontend or the home screen may do while the game is minimized) never is: Android drops the request, so the game kept its
 * sound suspended (Native.audioInterrupt) after returning, with everything else running. A focus that is lost for good, or was refused, is asked for again on the return.
 * PEANUTS(sunflower): a permanent loss while the game is on screen keeps its sound: on a dual-screen device that is a video or music on the other screen (the
 * 2nd screen left to Android), and the player hears both, as when they share one screen; the focus is still asked for again on the next return
 */
final class AudioFocus {
    // AudioManager's values
    static final int GAIN = 1, LOSS = -1, LOSS_TRANSIENT = -2, LOSS_TRANSIENT_CAN_DUCK = -3;

    private boolean dropped;   // lost for good or refused: no gain will come without a new request
    private boolean shown = true;   // the game on screen (GameActivity's onStart / onStop)

    /** The game came on screen or left it */
    void shown(boolean on) { shown = on; }

    /** The listener's change: whether the game's sound is interrupted now */
    boolean changed(int f) {
        if (f == GAIN) { dropped = false; return false; }
        if (f == LOSS) { dropped = true; return !shown; }   // on screen: another app on the other screen, both play
        return f == LOSS_TRANSIENT || f == LOSS_TRANSIENT_CAN_DUCK;
    }
    /** The answer to a request: granted ends the interruption (no listener call comes for it), refused leaves it to the next return */
    void requested(boolean granted) { dropped = !granted; }
    /** On returning to the foreground: whether to request the focus again */
    boolean askAgain() { return dropped; }
}
