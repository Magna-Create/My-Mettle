package dev.kian.mymettle.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer as composeGraphicsLayer

/** File-local import bridge for selector prototypes that use the package-level modifier name. */
internal fun Modifier.graphicsLayer(block: GraphicsLayerScope.() -> Unit): Modifier =
    composeGraphicsLayer(block)
