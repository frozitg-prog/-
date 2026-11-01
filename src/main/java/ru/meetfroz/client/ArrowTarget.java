package ru.meetfroz.client;

public final class ArrowTarget {
    private static int index = 0;
    private ArrowTarget() {}
    public static GPSMarker current() {
        var list = MeetFroz.manager.snapshot();
        if (list.isEmpty()) return null;
        index = Math.floorMod(index, list.size());
        return list.get(index);
    }
    public static void selectNext() { index++; current(); }
    public static void clear() { index = 0; }
}
