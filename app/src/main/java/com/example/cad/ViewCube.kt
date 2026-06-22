package com.example.cad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.PI

// Face data structure
data class ViewCubeFace(
    val name: String,
    val vertexIndices: List<Int>,
    val center: Vector3D,
    val targetYaw: Float,
    val targetPitch: Float
)

@Composable
fun ViewCube(
    viewModel: CadViewerViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val yaw by viewModel.cameraYaw
    val pitch by viewModel.cameraPitch
    val roll by viewModel.cameraRoll

    // Cube size parameter
    val s = 28f // Local half-size of the cube

    // 8 local vertices of the 3D Cube
    val localVertices = remember(s) {
        listOf(
            Vector3D(-s, -s, -s), // 0
            Vector3D(s, -s, -s),  // 1
            Vector3D(s, s, -s),   // 2
            Vector3D(-s, s, -s),  // 3
            Vector3D(-s, -s, s),  // 4
            Vector3D(s, -s, s),   // 5
            Vector3D(s, s, s),    // 6
            Vector3D(-s, s, s)    // 7
        )
    }

    // 6 faces mapped to indices and target viewport rotation angles
    val faces = remember {
        listOf(
            ViewCubeFace("FRONT", listOf(4, 5, 6, 7), Vector3D(0f, 0f, s), 0f, 0f),
            ViewCubeFace("BACK", listOf(1, 0, 3, 2), Vector3D(0f, 0f, -s), PI.toFloat(), 0f),
            ViewCubeFace("RIGHT", listOf(5, 1, 2, 6), Vector3D(s, 0f, 0f), (PI / 2).toFloat(), 0f),
            ViewCubeFace("LEFT", listOf(0, 4, 7, 3), Vector3D(-s, 0f, 0f), (-PI / 2).toFloat(), 0f),
            ViewCubeFace("TOP", listOf(0, 1, 5, 4), Vector3D(0f, -s, 0f), 0f, (-PI / 2).toFloat()),
            ViewCubeFace("BOTTOM", listOf(3, 7, 6, 2), Vector3D(0f, s, 0f), 0f, (PI / 2).toFloat())
        )
    }

    // Interactive custom view helper layout
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberOverlay),
        border = BorderStroke(1.dp, CyberGrid),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .width(180.dp)
            .testTag("fusion_view_cube_card")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VIEW AXIS",
                    color = CyberNeonCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                
                // HOME BUTTON (Top Right of the cube area)
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            animateToAngle(viewModel, 0.7f, -0.5f)
                        }
                    },
                    modifier = Modifier.size(24.dp).testTag("view_cube_home_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Reset viewport",
                        tint = CyberNeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Keep track of the center point and rotated screen space coords of visible faces
            var canvasCenter by remember { mutableStateOf(Offset(75f, 75f)) }
            val faceScreenMapping = remember { mutableStateMapOf<String, Offset>() }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.cameraYaw.value = (viewModel.cameraYaw.value + dragAmount.x * 0.007f)
                            viewModel.cameraPitch.value = (viewModel.cameraPitch.value + dragAmount.y * 0.007f).coerceIn(-1.5f, 1.5f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Find the visible face whose projected center is closest to this tap offset
                            var bestFace: ViewCubeFace? = null
                            var bestDist = Float.MAX_VALUE

                            // Rotated vertices of our local cube
                            val rotatedVerts = localVertices.map { it.rotate(viewModel.cameraYaw.value, viewModel.cameraPitch.value, viewModel.cameraRoll.value) }
                            
                            // Only check faces facing front (the front-facing ones have smaller rotated Z value in our camera view system)
                            val sortedVisible = faces.sortedBy { face ->
                                face.vertexIndices.sumOf { rotatedVerts[it].z.toDouble() }
                            }.take(3) // First 3 are the front-facing (facing camera/smallest rotated z depth)

                            sortedVisible.forEach { face ->
                                val screenPos = faceScreenMapping[face.name]
                                if (screenPos != null) {
                                    val dist = (screenPos - offset).getDistance()
                                    if (dist < bestDist && dist < 45f) { // Within 45px tap radius
                                        bestDist = dist
                                        bestFace = face
                                    }
                                }
                            }

                            bestFace?.let { face ->
                                coroutineScope.launch {
                                    animateToAngle(viewModel, face.targetYaw, face.targetPitch)
                                }
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val center = Offset(width / 2f, height / 2f)
                    canvasCenter = center

                    // Local project scale
                    val scale = 1.1f

                    // 1. Project all local cube vertices using the active yaw/pitch/roll
                    val rotatedVertices = localVertices.map { v ->
                        val rot = v.rotate(yaw, pitch, roll)
                        Offset(
                            center.x + rot.x * scale,
                            center.y + rot.y * scale
                        ) to rot.z
                    }

                    // 2. Sort faces using Painter's Algorithm:
                    // Faces with larger avg Z are rendered first (back of the cube).
                    // Faces with smaller avg Z are rendered last (front of the cube).
                    val sortedFacesWithZ = faces.map { face ->
                        val avgZ = face.vertexIndices.sumOf { rotatedVertices[it].second.toDouble() } / 4.0
                        face to avgZ
                    }.sortedByDescending { it.second } // Largest Z first (rear)

                    // 3. Draw each face
                    sortedFacesWithZ.forEachIndexed { sIdx, (face, zDepth) ->
                        // The last 3 faces in sorting (smallest Z depth) are the front-facing (foreground) ones since they are closer
                        val isForegroundFace = sIdx >= 3 
                        
                        val path = Path().apply {
                            val p0 = rotatedVertices[face.vertexIndices[0]].first
                            moveTo(p0.x, p0.y)
                            for (i in 1 until face.vertexIndices.size) {
                                val p = rotatedVertices[face.vertexIndices[i]].first
                                lineTo(p.x, p.y)
                            }
                            close()
                        }

                        // Colors
                        val faceBgColor = if (isForegroundFace) {
                            CyberSteel.copy(alpha = 0.85f)
                        } else {
                            CyberOnyx.copy(alpha = 0.35f)
                        }
                        
                        val strokeColor = if (isForegroundFace) {
                            CyberNeonCyan.copy(alpha = 0.9f)
                        } else {
                            CyberGrid.copy(alpha = 0.25f)
                        }

                        // Draw background fill
                        drawPath(
                            path = path,
                            color = faceBgColor
                        )

                        // Draw wireframe borders
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = if (isForegroundFace) 2.5f else 1f)
                        )

                        // 4. Calculate center of this face in screen coordinates to draw the label
                        var sumX = 0f
                        var sumY = 0f
                        face.vertexIndices.forEach { idx ->
                            sumX += rotatedVertices[idx].first.x
                            sumY += rotatedVertices[idx].first.y
                        }
                        val faceCenterScreen = Offset(sumX / 4f, sumY / 4f)
                        
                        // Map the screen coordinate of the label for tap detection
                        faceScreenMapping[face.name] = faceCenterScreen

                        // Render text labels on foreground visible faces
                        if (isForegroundFace) {
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 21f
                                    typeface = android.graphics.Typeface.MONOSPACE
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                }
                                val textY = faceCenterScreen.y - ((paint.descent() + paint.ascent()) / 2)
                                drawText(
                                    face.name,
                                    faceCenterScreen.x,
                                    textY,
                                    paint
                                )
                            }
                        }
                    }

                    // Draw companion static outer compass ring for scifi look
                    drawCircle(
                        color = CyberGrid.copy(alpha = 0.15f),
                        radius = s * 2.1f,
                        center = center,
                        style = Stroke(width = 1f)
                    )
                }
            }

            Text(
                text = "Drag Cube to Orbit\nTap Face to Align View",
                color = CyberGrey,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

private suspend fun animateToAngle(viewModel: CadViewerViewModel, targetYaw: Float, targetPitch: Float) {
    val startYaw = viewModel.cameraYaw.value
    val startPitch = viewModel.cameraPitch.value
    
    // Normalize targetYaw to the circular shortest path relative to startYaw
    var adjustedTargetYaw = targetYaw
    while (adjustedTargetYaw - startYaw > PI) adjustedTargetYaw -= (2 * PI).toFloat()
    while (adjustedTargetYaw - startYaw < -PI) adjustedTargetYaw += (2 * PI).toFloat()
    
    val duration = 300 // ms
    val steps = 15
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        // Cubic ease-out
        val ease = 1f - (1f - t) * (1f - t) * (1f - t)
        viewModel.cameraYaw.value = startYaw + (adjustedTargetYaw - startYaw) * ease
        viewModel.cameraPitch.value = startPitch + (targetPitch - startPitch) * ease
        kotlinx.coroutines.delay((duration / steps).toLong())
    }
}
