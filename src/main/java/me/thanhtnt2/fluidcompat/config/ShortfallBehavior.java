package me.thanhtnt2.fluidcompat.config;

/** What a pump does when the surrounding fluid cannot pay for a whole cycle. */
public enum ShortfallBehavior {
    /** Produce a proportionally smaller amount. */
    SCALE_DOWN,
    /** Produce nothing for that cycle. */
    STOP
}
