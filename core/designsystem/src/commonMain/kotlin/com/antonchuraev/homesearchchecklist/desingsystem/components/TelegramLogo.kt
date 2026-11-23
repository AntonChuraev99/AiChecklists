package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Telegram brand logo — the official `#2AABEE` circle with the white paper plane — as a code-built
 * [ImageVector]. Brand-coloured on purpose: it is never tinted by the theme and looks the same in
 * light and dark.
 *
 * Why code and not `ic_telegram.xml` via `painterResource`: Compose Resources parses XML vectors at
 * runtime and on wasmJs that parser renders multi-path drawables blank (see `GooglePlayLogo`).
 * Path data is copied verbatim from `composeResources/drawable/ic_telegram.xml` — keep them in sync.
 */
val TelegramLogoVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "TelegramLogo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = addPathNodes("M12,0A12,12 0,1 0,12 24A12,12 0,1 0,12 0z"),
            fill = SolidColor(TelegramBlue),
        )
        addPath(
            pathData = addPathNodes(
                "M16.906,7.224c0.1,-0.002 0.321,0.023 0.465,0.14a0.506,0.506 0,0 1,0.171 0.325" +
                    "c0.016,0.093 0.036,0.306 0.02,0.472 -0.18,1.898 -0.962,6.502 -1.36,8.627" +
                    " -0.168,0.9 -0.499,1.201 -0.82,1.23 -0.696,0.065 -1.225,-0.46 -1.9,-0.902" +
                    " -1.056,-0.693 -1.653,-1.124 -2.678,-1.8 -1.185,-0.78 -0.417,-1.21 0.258,-1.91" +
                    " 0.177,-0.184 3.247,-2.977 3.307,-3.23 0.007,-0.032 0.014,-0.15 -0.056,-0.212" +
                    "s-0.174,-0.041 -0.249,-0.024c-0.106,0.024 -1.793,1.14 -5.061,3.345" +
                    " -0.48,0.33 -0.913,0.49 -1.302,0.48 -0.428,-0.008 -1.252,-0.241 -1.865,-0.44" +
                    " -0.752,-0.245 -1.349,-0.374 -1.297,-0.789 0.027,-0.216 0.325,-0.437 0.893,-0.663" +
                    " 3.498,-1.524 5.83,-2.529 6.998,-3.014 3.332,-1.386 4.025,-1.627 4.476,-1.635z",
            ),
            fill = SolidColor(Color.White),
        )
    }.build()
}

/** Official Telegram blue — brand colour, deliberately not a theme token. */
private val TelegramBlue = Color(0xFF2AABEE)

/**
 * The [TelegramLogoVector] at [size], untinted. Decorative by default: every call site sits next to
 * text that already says "Telegram", so a description would make TalkBack read the name twice.
 */
@Composable
fun TelegramLogo(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Image(
        imageVector = TelegramLogoVector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
