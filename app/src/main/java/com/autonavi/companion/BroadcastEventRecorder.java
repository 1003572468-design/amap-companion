package com.autonavi.companion;

import android.content.Intent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;

final class BroadcastEventRecorder {
    private static final int MAX_EVENTS = 200;
    private static final ArrayDeque<BroadcastEvent> EVENTS = new ArrayDeque<>();

    private BroadcastEventRecorder() {
    }

    static void record(Intent intent) {
        if (intent == null || isReplayIntent(intent)) {
            return;
        }
        BroadcastEvent event = BroadcastEvent.fromIntent(intent);
        if (event == null) {
            return;
        }
        synchronized (EVENTS) {
            EVENTS.addLast(event);
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeFirst();
            }
        }
    }

    static boolean isReplayIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        return AppPrefs.ACTION_DIAGNOSTIC_REPLAY.equals(intent.getAction())
                || intent.getBooleanExtra(AppPrefs.EXTRA_DIAGNOSTIC_REPLAY, false);
    }

    static ArrayList<BroadcastEvent> recentEvents() {
        synchronized (EVENTS) {
            ArrayList<BroadcastEvent> out = new ArrayList<>(EVENTS);
            Collections.reverse(out);
            return out;
        }
    }

    static ArrayList<BroadcastEvent> eventsOldestFirst() {
        synchronized (EVENTS) {
            return new ArrayList<>(EVENTS);
        }
    }

    static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    static int size() {
        synchronized (EVENTS) {
            return EVENTS.size();
        }
    }
}
