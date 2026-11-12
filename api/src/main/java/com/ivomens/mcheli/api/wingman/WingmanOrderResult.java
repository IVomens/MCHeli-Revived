package com.ivomens.mcheli.api.wingman;

/**
 * What became of an order given through {@link Wingmen}. Only {@link #DONE} changed anything.
 *
 * <p>Immutable; any thread. Since API 1.1. A minor version may add constants, so a {@code switch} over this
 * enum needs a {@code default} branch.
 */
public enum WingmanOrderResult {
    /** The order was given. */
    DONE,
    /** No such aircraft is loaded in the commander's level. */
    NOT_FOUND,
    /** The aircraft is no wingman, and the order only goes to wingmen. */
    NOT_A_WINGMAN,
    /**
     * The commander may not order it: it is another player's wingman, someone else is aboard, or it is locked to
     * its owner. Owners and operators may order a wingman, and so may whoever flies the aircraft it follows.
     */
    NOT_ALLOWED,
    /** The order needs the commander to fly an aircraft (to lead a formation), and they do not. */
    NOT_IN_AIRCRAFT,
    /** The commander's formation has as many wingmen as the server allows. */
    FORMATION_FULL,
    /** The order makes no sense: the aircraft is no MCHeli aircraft, is the commander's own, or the request is empty. */
    INVALID
}
