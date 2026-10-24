// SPDX-License-Identifier: GPL-3.0-only
// From Flyfish233/spake2-java 1.1.1 (https://github.com/Flyfish233/spake2-java, commit 809ad7a), unchanged.
/*
 * Copyright (C) 2021 Muntashir Al-Islam
 * Modified 2025 Flyfish233
 *
 * Licensed according to the LICENSE file in this repository.
 */

package com.flyfish233.crypto.spake2;

/**
 * Role identifiers for the two SPAKE2 participants.
 */
public enum Spake2Role {
    /** Initiator role traditionally named "Alice". */
    Alice,
    /** Responder role traditionally named "Bob". */
    Bob,
}
