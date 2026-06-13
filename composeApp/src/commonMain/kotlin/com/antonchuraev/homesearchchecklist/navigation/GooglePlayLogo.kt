package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Google Play logo as a code-built [ImageVector] (4 brand-colored paths).
 *
 * Why not the `ic_google_play.xml` drawable via `painterResource`: Compose
 * Resources parses XML vector drawables into an ImageVector at RUNTIME, and on
 * wasmJs/Skiko that parser renders this multi-path drawable blank — the promo
 * badge showed an empty box on web while Android (native AVD) was fine. Material
 * `Icons.*` render on web because they are pre-built ImageVectors, NOT XML-parsed.
 * Building the logo the same way (in code) bypasses the XML parser, so it renders
 * identically on Android, iOS and web.
 *
 * Path data + colors are copied verbatim from
 * core/designsystem/.../composeResources/drawable/ic_google_play.xml.
 */
internal val GooglePlayLogo: ImageVector by lazy {
    ImageVector.Builder(
        name = "GooglePlayLogo",
        defaultWidth = 22.dp,
        defaultHeight = 24.dp,
        viewportWidth = 466f,
        viewportHeight = 511.98f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M199.9,237.8l-198.5,232.37c7.22,24.57 30.16,41.81 55.8,41.81 " +
                    "11.16,0 20.93,-2.79 29.3,-8.37l0,0 244.16,-139.46 -130.76,-126.35z",
            ),
            fill = SolidColor(Color(0xFFEA4335)),
        )
        addPath(
            pathData = addPathNodes(
                "M433.91,205.1l0,0 -104.65,-60 -111.61,110.22 113.01,108.83 " +
                    "104.64,-58.6c18.14,-9.77 30.7,-29.3 30.7,-50.23 -1.4,-20.93 " +
                    "-13.95,-40.46 -32.09,-50.22z",
            ),
            fill = SolidColor(Color(0xFFFBBC04)),
        )
        addPath(
            pathData = addPathNodes(
                "M199.42,273.45l129.85,-128.35 -241.37,-136.73c-8.37,-5.58 " +
                    "-19.54,-8.37 -30.7,-8.37 -26.5,0 -50.22,18.14 -55.8,41.86 " +
                    "0,0 0,0 0,0l198.02,231.59z",
            ),
            fill = SolidColor(Color(0xFF34A853)),
        )
        addPath(
            pathData = addPathNodes(
                "M1.39,41.86c-1.39,4.18 -1.39,9.77 -1.39,15.34l0,397.64c0,5.57 " +
                    "0,9.76 1.4,15.34l216.27,-214.86 -216.28,-213.46z",
            ),
            fill = SolidColor(Color(0xFF4285F4)),
        )
    }.build()
}
