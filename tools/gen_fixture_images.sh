#!/bin/sh
# Regenerates FixtureImages.kt's map after adding sample art to core/fixtures/src/main/res/drawable-nodpi (fx_*).
cd "$(dirname "$0")/.." && ls core/fixtures/src/main/res/drawable-nodpi | sort | sed -E 's/^fx_([^.]*)\..*/        "\1" to R.drawable.fx_\1,/'
