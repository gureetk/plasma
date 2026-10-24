// SPDX-License-Identifier: GPL-3.0-or-later
package ca.gureet.plasma

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** After a boot, Google TV Home coming back where Plasma had replaced it (brief 5.13). */
class HomeCameBackTest {
    @Test fun askedWhenHomeNoLongerOpensPlasma() {
        assertTrue(homeCameBack(replaced = true, homeOpensPlasma = false, askedBoot = null, boot = 12))
        assertTrue("A new boot asks again", homeCameBack(replaced = true, homeOpensPlasma = false, askedBoot = 11, boot = 12))
    }

    @Test fun notAskedOtherwise() {
        assertFalse("Never replaced", homeCameBack(replaced = false, homeOpensPlasma = false, askedBoot = null, boot = 12))
        assertFalse("Still Plasma", homeCameBack(replaced = true, homeOpensPlasma = true, askedBoot = null, boot = 12))
        assertFalse("Asked this boot", homeCameBack(replaced = true, homeOpensPlasma = false, askedBoot = 12, boot = 12))
    }

    @Test fun withoutABootCountEachStartAsks() {
        assertTrue(homeCameBack(replaced = true, homeOpensPlasma = false, askedBoot = 12, boot = null))
    }
}
