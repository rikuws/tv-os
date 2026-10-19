package fi.koti.launcher;

/**
 * Koti, tvOS-style: white type, dark glass, artwork as the only colour. Values follow
 * Apple's dark-mode system grays so cards and glass read like the Apple TV app.
 */
final class Palette {
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = 0xFFF5F5F7;          // primary labels
    static final int TEXT_2 = 0x99FFFFFF;        // secondary (60%)
    static final int TEXT_3 = 0x66FFFFFF;        // tertiary (40%)
    static final int ON_FOCUS = 0xFF1C1C1E;      // text/icons on a white focused capsule

    // Glass (no blur on this TV): tinted dark fill + a bright top edge
    static final int GLASS = 0xC21C1C1F;
    static final int GLASS_STRONG = 0xF51C1C1F;  // panels that hold lists
    static final int GLASS_SOLID = 0xFF1C1C1F;   // full-screen pages: opaque, no ghosting
    static final int GLASS_EDGE_TOP = 0x40FFFFFF;
    static final int GLASS_EDGE_BOTTOM = 0x0DFFFFFF;

    static final int SCRIM = 0xFF000000;
    static final int PLATE = 0xFF2C2C2E;         // tile behind icons without a banner
    static final int PROGRESS_TRACK = 0x4DFFFFFF;

    // Fallback ground when there is no art and no photo
    static final int FALLBACK_TOP = 0xFF0B0B0F;
    static final int FALLBACK_BOTTOM = 0xFF1C1C1E;

    private Palette() {}

    static int alpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
    }
}
