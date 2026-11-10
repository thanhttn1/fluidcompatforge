/**
 * The public Fluid Compat API: ask whether a fluid is finite, take fluid from the world on behalf of a machine,
 * and add drain backends.
 *
 * <p><b>Stability (since 2.0.0).</b> Everything in this package and in {@code api.event} and {@code api.compat} is
 * the supported surface for addons. Within the 2.x line it only grows: classes, methods and event fields are added,
 * never removed, renamed or given a different meaning, and the unit stays 1 level = 1/8 bucket = 125 mB.
 * Anything outside these packages ({@code core}, {@code compat.*}, {@code config}, {@code client}) is internal and
 * may change in any release.
 */
package me.thanhtnt2.fluidcompat.api;
