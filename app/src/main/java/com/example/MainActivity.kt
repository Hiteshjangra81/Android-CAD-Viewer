package com.example

import android.os.Bundle
import android.widget.Toast
import android.net.Uri
import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.os.Build
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.cad.*
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = ViewModelProvider(this)[CadViewerViewModel::class.java]
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = CyberOnyx
                ) { innerPadding ->
                    CadViewerScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

fun saveBitmapToGallery(context: android.content.Context, bitmap: Bitmap): Uri? {
    val filename = "CAD_Render_${System.currentTimeMillis()}.png"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CADViewer")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    
    if (uri != null) {
        try {
            resolver.openOutputStream(uri).use { stream ->
                if (stream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return null
        }
    }
    return uri
}

fun shareImageUri(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CAD GPU Render"))
}

fun renderToBitmap(viewModel: CadViewerViewModel, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0B0F19")
        style = android.graphics.Paint.Style.FILL
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    
    val gridPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        alpha = 10
        style = android.graphics.Paint.Style.FILL
    }
    val spacing = 24f * 3f
    var yGrid = spacing / 2
    while (yGrid < height) {
        var xGrid = spacing / 2
        while (xGrid < width) {
            canvas.drawCircle(xGrid, yGrid, 3f, gridPaint)
            xGrid += spacing
        }
        yGrid += spacing
    }
    
    val assembly = viewModel.assembly.value
    val halfW = width / 2f
    val halfH = height / 2f
    val viewCenter = Offset(halfW, halfH)
    
    val scaleMultiplier = viewModel.zoom.value * 2.5f
    val yaw = viewModel.cameraYaw.value
    val pitch = viewModel.cameraPitch.value
    val roll = viewModel.cameraRoll.value
    val panXVal = viewModel.panX.value
    val panYVal = viewModel.panY.value
    
    val sortedFaces = mutableListOf<RenderFaceRecord>()
    
    assembly.bodies.forEach { body ->
        if (viewModel.bodyVisibility[body.id] == false) return@forEach
        
        val vertexWorldPositions = mutableListOf<Vector3D>()
        val bodyProjectedPositions = mutableMapOf<Int, Offset>()
        
        body.vertices.forEachIndexed { vIdx, v ->
            val explodedOffset = if (viewModel.explosionScale.value > 0f) {
                v + (body.explosionDirection * viewModel.explosionScale.value * 65f)
            } else {
                v
            }
            
            val rotated = explodedOffset.rotate(yaw, pitch, roll)
            val worldX = rotated.x * scaleMultiplier + panXVal
            val worldY = rotated.y * scaleMultiplier + panYVal
            val worldZ = rotated.z * scaleMultiplier + 450f
            
            val worldPos = Vector3D(worldX, worldY, worldZ)
            vertexWorldPositions.add(worldPos)
            
            val perspectiveRatio = 450f / worldZ
            val screenX = viewCenter.x + (worldX * perspectiveRatio)
            val screenY = viewCenter.y + (worldY * perspectiveRatio)
            val screenPos = Offset(screenX, screenY)
            
            bodyProjectedPositions[vIdx] = screenPos
        }
        
        body.faces.forEach { face ->
            if (face.vertexIndices.isEmpty()) return@forEach
            
            if (viewModel.sliceAxis.value != SliceAxis.NONE) {
                var sumCoord = 0f
                face.vertexIndices.forEach { vIdx ->
                    val localV = body.vertices[vIdx]
                    sumCoord += when (viewModel.sliceAxis.value) {
                        SliceAxis.X -> localV.x
                        SliceAxis.Y -> localV.y
                        SliceAxis.Z -> localV.z
                        else -> 0f
                    }
                }
                val avgCoord = sumCoord / face.vertexIndices.size
                if (avgCoord > viewModel.slicePlaneValue.value) {
                    return@forEach
                }
            }
            
            val projectedFaceCoords = face.vertexIndices.map { idx ->
                bodyProjectedPositions[idx] ?: Offset.Zero
            }
            
            val worldFaceCoords = face.vertexIndices.map { idx ->
                vertexWorldPositions[idx]
            }
            
            var sumZ = 0f
            worldFaceCoords.forEach { sumZ += it.z }
            val avgFaceZ = sumZ / worldFaceCoords.size
            
            var skipFace = false
            if (viewModel.renderMode.value == RenderMode.SOLID || viewModel.renderMode.value == RenderMode.RAY_TRACING || viewModel.renderMode.value == RenderMode.FLAT) {
                if (projectedFaceCoords.size >= 3) {
                    val p0 = projectedFaceCoords[0]
                    val p1 = projectedFaceCoords[1]
                    val p2 = projectedFaceCoords[2]
                    val cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
                    if (cross < 0f) {
                        skipFace = true
                    }
                }
            }
            
            if (!skipFace) {
                sortedFaces.add(
                    RenderFaceRecord(
                        body = body,
                        face = face,
                        avgDepth = avgFaceZ,
                        projectedCoords = projectedFaceCoords,
                        worldCoords = worldFaceCoords
                    )
                )
            }
        }
    }
    
    sortedFaces.sortByDescending { it.avgDepth }
    
    val facePaint = android.graphics.Paint().apply {
         style = android.graphics.Paint.Style.FILL
         isAntiAlias = true
    }
    val linePaint = android.graphics.Paint().apply {
         style = android.graphics.Paint.Style.STROKE
         strokeWidth = 2f
         isAntiAlias = true
    }
    
    val activeScene = viewModel.currentLightScene.value
    val mainLightDir = activeScene.mainLightDirection
    val fillLightDir = activeScene.fillLightDirection
    val ambientRatio = activeScene.ambientLevel * viewModel.ambientIntensity.value
    val mainIntensity = viewModel.mainLightIntensity.value
    val fillIntensity = 0.2f * viewModel.mainLightIntensity.value
    val activeMainColor = activeScene.mainLightColor
    val activeFillColor = activeScene.fillLightColor
    
    if (viewModel.renderMode.value == RenderMode.RAY_TRACING) {
        val shadowYOffset = viewCenter.y + 130f * scaleMultiplier
        sortedFaces.forEach { faceRecord ->
            val shadowPath = android.graphics.Path()
            val pts = faceRecord.projectedCoords
            if (pts.isNotEmpty()) {
                val pt0 = pts[0]
                shadowPath.moveTo(pt0.x + (pt0.x - viewCenter.x) * 0.05f, shadowYOffset + (pt0.y - shadowYOffset) * 0.02f)
                for (i in 1 until pts.size) {
                    val pti = pts[i]
                    shadowPath.lineTo(pti.x + (pti.x - viewCenter.x) * 0.05f, shadowYOffset + (pti.y - shadowYOffset) * 0.02f)
                }
                shadowPath.close()
                
                val shadowPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    alpha = 110
                    style = android.graphics.Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawPath(shadowPath, shadowPaint)
            }
        }
    }
    
    sortedFaces.forEach { faceRecord ->
        val body = faceRecord.body
        val face = faceRecord.face
        val pts = faceRecord.projectedCoords
        val wPts = faceRecord.worldCoords
        
        if (pts.isEmpty()) return@forEach
        
        val drawPath = android.graphics.Path()
        drawPath.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            drawPath.lineTo(pts[i].x, pts[i].y)
        }
        drawPath.close()
        
        val p0 = wPts[0]
        val p1 = wPts[1]
        val p2 = wPts[2]
        val normal = (p1 - p0).cross(p2 - p0).normalize()
        
        val diffuseMain = normal.dot(mainLightDir).coerceAtLeast(0f)
        val diffuseFill = normal.dot(fillLightDir).coerceAtLeast(0f)
        
        when (viewModel.renderMode.value) {
            RenderMode.FLAT -> {
                facePaint.color = android.graphics.Color.rgb(
                    (face.color.red * 255).toInt(),
                    (face.color.green * 255).toInt(),
                    (face.color.blue * 255).toInt()
                )
                canvas.drawPath(drawPath, facePaint)
            }
            RenderMode.SOLID, RenderMode.RAY_TRACING -> {
                val rMix = (face.color.red * ambientRatio + 
                            face.color.red * activeMainColor.red * diffuseMain * mainIntensity + 
                            face.color.red * activeFillColor.red * diffuseFill * fillIntensity).coerceIn(0f, 1f)
                val gMix = (face.color.green * ambientRatio + 
                            face.color.green * activeMainColor.green * diffuseMain * mainIntensity + 
                            face.color.green * activeFillColor.green * diffuseFill * fillIntensity).coerceIn(0f, 1f)
                val bMix = (face.color.blue * ambientRatio + 
                            face.color.blue * activeMainColor.blue * diffuseMain * mainIntensity + 
                            face.color.blue * activeFillColor.blue * diffuseFill * fillIntensity).coerceIn(0f, 1f)
                
                var faceColor = Color(rMix, gMix, bMix)
                
                if (viewModel.renderMode.value == RenderMode.RAY_TRACING) {
                    var centerDist = 0f
                    wPts.forEach { centerDist += it.length() }
                    val avgCenter = centerDist / wPts.size
                    val aoFactor = (avgCenter / (450f * scaleMultiplier)).coerceIn(0.5f, 1.0f)
                    faceColor = faceColor.copy(
                        red = faceColor.red * aoFactor,
                        green = faceColor.green * aoFactor,
                        blue = faceColor.blue * aoFactor
                    )
                }
                
                facePaint.color = android.graphics.Color.rgb(
                    (faceColor.red * 255).toInt(),
                    (faceColor.green * 255).toInt(),
                    (faceColor.blue * 255).toInt()
                )
                canvas.drawPath(drawPath, facePaint)
                
                if (viewModel.showMeshEdges.value) {
                    linePaint.color = if (viewModel.renderMode.value == RenderMode.RAY_TRACING) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.rgb(
                            (face.color.red * 255).toInt(),
                            (face.color.green * 255).toInt(),
                            (face.color.blue * 255).toInt()
                        )
                    }
                    linePaint.alpha = if (viewModel.renderMode.value == RenderMode.RAY_TRACING) 38 else 76
                    canvas.drawPath(drawPath, linePaint)
                }
            }
            
            RenderMode.WIREFRAME -> {
                linePaint.color = android.graphics.Color.rgb(
                    (face.color.red * 255).toInt(),
                    (face.color.green * 255).toInt(),
                    (face.color.blue * 255).toInt()
                )
                linePaint.alpha = 200
                canvas.drawPath(drawPath, linePaint)
            }
            
            RenderMode.HOLOGRAM -> {
                facePaint.color = android.graphics.Color.rgb(0, 229, 255)
                facePaint.alpha = 15
                canvas.drawPath(drawPath, facePaint)
                
                linePaint.color = android.graphics.Color.rgb(0, 229, 255)
                linePaint.alpha = 115
                canvas.drawPath(drawPath, linePaint)
            }
        }
    }
    
    return bitmap
}

@Composable
fun CadViewerScreen(
    viewModel: CadViewerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val assembly by viewModel.assembly.collectAsState()

    var importIntoExisting by remember { mutableStateOf(false) } // Default to false (new workspace/clearing previous)
    var selectedHexColorForImport by remember { mutableStateOf("#FF00E5FF") } // Default color

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val parsedHex = if (selectedHexColorForImport.startsWith("#")) selectedHexColorForImport else "#FF00E5FF"
            val parsedColor = try {
                Color(android.graphics.Color.parseColor(parsedHex))
            } catch (e: Exception) {
                Color(0xFF00E5FF)
            }
            viewModel.importCadFileFromUri(context, uri, parsedColor, importIntoExisting)
        }
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var currentDrawerTab by remember { mutableStateOf(0) } // 0: Parts list, 1: Controls, 2: Info/Telemetry
    var isLeftPanelExpanded by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberOnyx)
    ) {
        // 1. Futuristic Space Background Grid (Radial Dot Matrix)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val spacing = 24.dp.toPx()
                    var y = spacing / 2
                    while (y < size.height) {
                        var x = spacing / 2
                        while (x < size.width) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.04f),
                                radius = 1.dp.toPx(),
                                center = Offset(x, y)
                            )
                            x += spacing
                        }
                        y += spacing
                    }
                }
        )

        // 2. Primary 3D Rendering Canvas Viewport
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("3d_canvas_container")
        ) {
            if (viewModel.isVrMode.value) {
                // VR Stereoscopic Split Screen Layout
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Cad3DCanvas(
                            viewModel = viewModel,
                            assembly = assembly,
                            isLeftEye = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // Vertical divider line
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(CyberGrid)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Cad3DCanvas(
                            viewModel = viewModel,
                            assembly = assembly,
                            isLeftEye = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // Expanded Single Screen Layout
                Cad3DCanvas(
                    viewModel = viewModel,
                    assembly = assembly,
                    isLeftEye = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Cybernetic Glowing Floating HUD Overlay Top-Right
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberOverlay),
                border = BorderStroke(1.dp, CyberGrid),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.width(190.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CORE TELEMETRY",
                            color = CyberNeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (viewModel.isGyroEnabled.value) CyberGreen else CyberLaserOrange)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Assembly: ${assembly.name}",
                        color = CyberWhite,
                        fontSize = 10.sp,
                        maxLines = 1,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Bodies Active: ${assembly.bodies.count { viewModel.bodyVisibility[it.id] != false }}/${assembly.bodies.size}",
                        color = CyberGrey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Shading: ${viewModel.renderMode.value.name}",
                        color = CyberGrey,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            com.example.cad.ViewCube(viewModel = viewModel)
        }

        // 4. Immersive Left Cyber Panel (Collapsible Drawer with tabs)
        AnimatedVisibility(
            visible = isLeftPanelExpanded,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(top = 16.dp, bottom = 100.dp, start = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberOverlay),
                border = BorderStroke(1.dp, CyberGrid),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(310.dp)
                    .fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Drawer Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSteel)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "NAVIGATOR 3D",
                                    color = CyberNeonCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Active CAD Layers",
                                    color = CyberGrey,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            IconButton(
                                onClick = { isLeftPanelExpanded = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Collapse menu",
                                    tint = CyberNeonCyan
                                )
                            }
                        }
                    }

                    // Tabs Selector
                    TabRow(
                        selectedTabIndex = currentDrawerTab,
                        containerColor = Color.Transparent,
                        contentColor = CyberNeonCyan,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[currentDrawerTab]),
                                color = CyberNeonCyan
                            )
                        }
                    ) {
                        Tab(
                            selected = currentDrawerTab == 0,
                            onClick = { currentDrawerTab = 0 },
                            text = { Text("PARTS", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                        Tab(
                            selected = currentDrawerTab == 1,
                            onClick = { currentDrawerTab = 1 },
                            text = { Text("SCENE", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                        Tab(
                            selected = currentDrawerTab == 2,
                            onClick = { currentDrawerTab = 2 },
                            text = { Text("IMPORT", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp)
                    ) {
                        when (currentDrawerTab) {
                            0 -> {
                                // Body Layer Toggle List
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "[${assembly.bodies.size}] Solid Bodies List",
                                            color = CyberWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        TextButton(
                                            onClick = { viewModel.showAllBodies() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = CyberNeonCyan)
                                        ) {
                                            Text("SHOW ALL", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        assembly.bodies.forEach { body ->
                                            val isVisible = viewModel.bodyVisibility[body.id] ?: true

                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isVisible) CyberSteel else CyberSteel.copy(alpha = 0.4f)
                                                ),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isVisible) CyberGrid.copy(alpha = 0.4f) else Color.Transparent
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                                ) {
                                                    // Miniature color legend
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(RoundedCornerShape(5.dp))
                                                            .background(body.baseColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = body.name,
                                                            color = if (isVisible) CyberWhite else CyberGrey,
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "F: ${body.faces.size} | V: ${body.vertices.size}",
                                                            color = CyberGrey.copy(alpha = 0.7f),
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    // Hide/Show Toggle Action
                                                    IconButton(
                                                        onClick = { viewModel.toggleVisibility(body.id) },
                                                        modifier = Modifier.size(32.dp).testTag("visibility_btn_${body.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isVisible) Icons.Filled.Check else Icons.Filled.Clear,
                                                            contentDescription = "Toggle hidden body",
                                                            tint = if (isVisible) CyberNeonCyan else CyberGrey,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    // Isolate Focus Action (Starred)
                                                    IconButton(
                                                        onClick = { viewModel.isolateBody(body.id) },
                                                        modifier = Modifier.size(32.dp).testTag("focus_btn_${body.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Star,
                                                            contentDescription = "Isolate CAD Layer",
                                                            tint = CyberAmber,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // Camera & Render Mode Operations
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = "Viewport Rendition Mode",
                                        color = CyberWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Render Mode Selector Grid
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RenderModeButton(
                                            label = "SOLID",
                                            isActive = viewModel.renderMode.value == RenderMode.SOLID,
                                            onClick = { viewModel.renderMode.value = RenderMode.SOLID },
                                            modifier = Modifier.weight(1f)
                                        )
                                        RenderModeButton(
                                            label = "FLAT",
                                            isActive = viewModel.renderMode.value == RenderMode.FLAT,
                                            onClick = { viewModel.renderMode.value = RenderMode.FLAT },
                                            modifier = Modifier.weight(1f)
                                        )
                                        RenderModeButton(
                                            label = "RAY-TRACE",
                                            isActive = viewModel.renderMode.value == RenderMode.RAY_TRACING,
                                            onClick = { viewModel.renderMode.value = RenderMode.RAY_TRACING },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RenderModeButton(
                                            label = "WIRE",
                                            isActive = viewModel.renderMode.value == RenderMode.WIREFRAME,
                                            onClick = { viewModel.renderMode.value = RenderMode.WIREFRAME },
                                            modifier = Modifier.weight(1f)
                                        )
                                        RenderModeButton(
                                            label = "HOLO",
                                            isActive = viewModel.renderMode.value == RenderMode.HOLOGRAM,
                                            onClick = { viewModel.renderMode.value = RenderMode.HOLOGRAM },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Divider(color = CyberGrid.copy(alpha = 0.2f))

                                    // Stereoscopic VR Controller Switch
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                tint = CyberNeonCyan,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "VR Dual-Lens Split",
                                                color = CyberWhite,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.isVrMode.value,
                                            onCheckedChange = {
                                                viewModel.isVrMode.value = it
                                                if (it) {
                                                    viewModel.enableGyroscope(true)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = CyberNeonCyan,
                                                checkedTrackColor = CyberSteel,
                                                uncheckedThumbColor = CyberGrey,
                                                uncheckedTrackColor = Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                                                .testTag("vr_mode_switch")
                                        )
                                    }

                                    // Gyroscope Control Switch
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                tint = CyberGreen,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Head Tracker (Gyro)",
                                                color = CyberWhite,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Switch(
                                            checked = viewModel.isGyroEnabled.value,
                                            onCheckedChange = { viewModel.enableGyroscope(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = CyberGreen,
                                                checkedTrackColor = CyberSteel,
                                                uncheckedThumbColor = CyberGrey,
                                                uncheckedTrackColor = Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                                                .testTag("gyro_switch")
                                        )
                                    }

                                    Divider(color = CyberGrid.copy(alpha = 0.2f))

                                    Text(
                                        text = "Environment Lighting Scenes",
                                        color = CyberWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { viewModel.currentLightScene.value = LightScene.SPACE_LAB },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.currentLightScene.value == LightScene.SPACE_LAB) CyberNeonCyan else CyberSteel
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text("DARK", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (viewModel.currentLightScene.value == LightScene.SPACE_LAB) CyberOnyx else CyberWhite)
                                        }
                                        Button(
                                            onClick = { viewModel.currentLightScene.value = LightScene.CYBERPUNK },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.currentLightScene.value == LightScene.CYBERPUNK) CyberNeonCyan else CyberSteel
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text("GREY", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (viewModel.currentLightScene.value == LightScene.CYBERPUNK) CyberOnyx else CyberWhite)
                                        }
                                        Button(
                                            onClick = { viewModel.currentLightScene.value = LightScene.GOLDEN_HOUR },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.currentLightScene.value == LightScene.GOLDEN_HOUR) CyberNeonCyan else CyberSteel
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text("DAYLIGHT", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (viewModel.currentLightScene.value == LightScene.GOLDEN_HOUR) CyberOnyx else CyberWhite)
                                        }
                                        Button(
                                            onClick = { viewModel.currentLightScene.value = LightScene.CLINICAL_STUDIO },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (viewModel.currentLightScene.value == LightScene.CLINICAL_STUDIO) CyberNeonCyan else CyberSteel
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp),
                                            modifier = Modifier.weight(1f).height(32.dp)
                                        ) {
                                            Text("STUDIO", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (viewModel.currentLightScene.value == LightScene.CLINICAL_STUDIO) CyberOnyx else CyberWhite)
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Primary Light Lumens:", color = CyberGrey, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(String.format("%.1f x", viewModel.mainLightIntensity.value), color = CyberNeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Slider(
                                            value = viewModel.mainLightIntensity.value,
                                            onValueChange = { viewModel.mainLightIntensity.value = it },
                                            valueRange = 0.1f..2.0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = CyberNeonCyan,
                                                activeTrackColor = CyberNeonCyan,
                                                inactiveTrackColor = CyberGrid
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Ambient Shader Level:", color = CyberGrey, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(String.format("%.2f", viewModel.ambientIntensity.value), color = CyberNeonCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Slider(
                                            value = viewModel.ambientIntensity.value,
                                            onValueChange = { viewModel.ambientIntensity.value = it },
                                            valueRange = 0.05f..1.0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = CyberNeonCyan,
                                                activeTrackColor = CyberNeonCyan,
                                                inactiveTrackColor = CyberGrid
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    // Switch to toggle showing facet mesh wire outlines
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Mesh Wire Outlines:", color = CyberGrey, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                            Text(if (viewModel.showMeshEdges.value) "ENABLED" else "DISABLED (REALISTIC)", color = if (viewModel.showMeshEdges.value) CyberNeonCyan else CyberLaserOrange, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        androidx.compose.material3.Switch(
                                            checked = viewModel.showMeshEdges.value,
                                            onCheckedChange = { viewModel.showMeshEdges.value = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = CyberOnyx,
                                                checkedTrackColor = CyberNeonCyan,
                                                uncheckedThumbColor = CyberGrey,
                                                uncheckedTrackColor = CyberGrid
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }

                                    val localScope = rememberCoroutineScope()
                                    Button(
                                        onClick = {
                                            localScope.launch {
                                                viewModel.isGeneratingRender.value = true
                                                kotlinx.coroutines.delay(1600)
                                                try {
                                                    val bmp = renderToBitmap(viewModel, 1024, 1024)
                                                    saveBitmapToGallery(context, bmp)
                                                    viewModel.capturedImage.value = bmp
                                                    Toast.makeText(context, "Render snapshot saved in Pictures/CADViewer!", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    viewModel.isGeneratingRender.value = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberLaserOrange),
                                        border = BorderStroke(1.dp, CyberNeonCyan),
                                        modifier = Modifier.fillMaxWidth().testTag("gpu_render_action_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Capture",
                                            tint = CyberOnyx,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "GPU RENDER & SNAPSHOT",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberOnyx
                                        )
                                    }

                                    Divider(color = CyberGrid.copy(alpha = 0.2f))

                                    // Reset viewport position
                                    Button(
                                        onClick = { viewModel.resetViewport() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSteel),
                                        border = BorderStroke(1.dp, CyberGrid),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth().testTag("reset_view_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = CyberNeonCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "RESET VIEWPORT CAMERA",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberNeonCyan
                                        )
                                    }
                                }
                            }

                            2 -> {
                                // Predefined model files switcher and local simulations
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Interstellar Assemblies",
                                        color = CyberWhite,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )

                                    PredefinedModelItem(
                                        title = "Cosmic Fusion Reactor",
                                        desc = "Magnetic cores & plasma spheres",
                                        onClick = { viewModel.loadAssembly("reactor") },
                                        isActive = assembly.name == "Cosmic Fusion Reactor"
                                    )

                                    PredefinedModelItem(
                                        title = "T-800 Arm Actuator",
                                        desc = "Hydraulic pistons & servo joints",
                                        onClick = { viewModel.loadAssembly("mech_arm") },
                                        isActive = assembly.name == "T-800 Arm Actuator"
                                    )

                                    PredefinedModelItem(
                                        title = "Aero-Turbine Compressor",
                                        desc = "Radially segmented fans & ducts",
                                        onClick = { viewModel.loadAssembly("aero_turbine") },
                                        isActive = assembly.name == "Aero-Turbine Compressor"
                                    )

                                    PredefinedModelItem(
                                        title = "Hypersonic Engine Impeller",
                                        desc = "High-density curved blades (1500+ tris)",
                                        onClick = { viewModel.loadAssembly("impeller") },
                                        isActive = assembly.name == "Hypersonic Engine Impeller"
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    Button(
                                        onClick = { showImportDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberSteel),
                                        border = BorderStroke(1.dp, CyberNeonCyan),
                                        modifier = Modifier.fillMaxWidth().testTag("add_part_gate_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = CyberNeonCyan
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "IMPORT MULTIPART FILE",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberNeonCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expand Handle Button when Left Panel is Hidden
        if (!isLeftPanelExpanded) {
            IconButton(
                onClick = { isLeftPanelExpanded = true },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(8.dp)
                    .background(CyberOverlay, shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .border(1.dp, CyberGrid, shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .size(40.dp)
                    .testTag("expand_drawer_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Expand menu",
                    tint = CyberNeonCyan
                )
            }
        }

        // 5. Immersive Bottom Controller Panel (Glow Sliders & Measurement readout)
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberOverlay),
            border = BorderStroke(1.dp, CyberGrid),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top controls row: Explosion, Slice, Laser measurement triggers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Exploded View Slider Controls
                    Column(modifier = Modifier.width(180.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "EXPLODED ASSEMBLY",
                                color = CyberNeonCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(viewModel.explosionScale.value * 100).toInt()}%",
                                color = CyberGrey,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Slider(
                            value = viewModel.explosionScale.value,
                            onValueChange = { viewModel.explosionScale.value = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberNeonCyan,
                                activeTrackColor = CyberNeonCyan,
                                inactiveTrackColor = CyberSteel
                            ),
                            modifier = Modifier
                                .height(24.dp)
                                .testTag("explosion_slider")
                        )
                    }

                    // Slicing Cross-Section Selector
                    Column(modifier = Modifier.width(180.dp)) {
                        Text(
                            text = "CROSS-SECTION SLICER",
                            color = CyberNeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SliceAxisButton("NONE", viewModel.sliceAxis.value == SliceAxis.NONE) {
                                viewModel.sliceAxis.value = SliceAxis.NONE
                            }
                            SliceAxisButton("X", viewModel.sliceAxis.value == SliceAxis.X) {
                                viewModel.sliceAxis.value = SliceAxis.X
                            }
                            SliceAxisButton("Y", viewModel.sliceAxis.value == SliceAxis.Y) {
                                viewModel.sliceAxis.value = SliceAxis.Y
                            }
                            SliceAxisButton("Z", viewModel.sliceAxis.value == SliceAxis.Z) {
                                viewModel.sliceAxis.value = SliceAxis.Z
                            }
                        }
                    }

                    // Measurement Tool Trigger
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "MEASUREMENT",
                            color = CyberAmber,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (viewModel.isMeasurementModeActive.value) {
                                Button(
                                    onClick = { viewModel.clearMeasurement() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSteel),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("CLEAR", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CyberGrey)
                                }
                            }
                            IconButton(
                                onClick = {
                                    viewModel.isMeasurementModeActive.value = !viewModel.isMeasurementModeActive.value
                                    if (!viewModel.isMeasurementModeActive.value) {
                                        viewModel.clearMeasurement()
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (viewModel.isMeasurementModeActive.value) CyberAmber else CyberSteel,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (viewModel.isMeasurementModeActive.value) CyberAmber else CyberGrid,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .testTag("measurement_mode_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "Measure distances",
                                    tint = if (viewModel.isMeasurementModeActive.value) CyberOnyx else CyberWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Slicing Depth Slider (shows only when a slice axis is active)
                if (viewModel.sliceAxis.value != SliceAxis.NONE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSteel, shape = RoundedCornerShape(6.dp))
                            .border(1.dp, CyberGrid.copy(alpha = 0.3f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = CyberNeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Slicing Bound Offset:",
                            color = CyberWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = viewModel.slicePlaneValue.value,
                            onValueChange = { viewModel.slicePlaneValue.value = it },
                            valueRange = -150f..150f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberNeonCyan,
                                activeTrackColor = CyberNeonCyan,
                                inactiveTrackColor = CyberGrid.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .testTag("slice_offset_slider")
                        )
                        Text(
                            text = "${viewModel.slicePlaneValue.value.toInt()} mm",
                            color = CyberNeonCyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(50.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Laser Measurement Display readout
                if (viewModel.isMeasurementModeActive.value) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberSteel, shape = RoundedCornerShape(6.dp))
                            .border(1.dp, CyberAmber.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                val pt1 = viewModel.firstSelectedVertex.value
                                val pt2 = viewModel.secondSelectedVertex.value
                                Text(
                                    text = if (pt1 == null) {
                                        "Measurement Node: Tapping on model vertices"
                                    } else if (pt2 == null) {
                                        "Node 1: [${pt1.partName}] #${pt1.vertexIndex} -> Select Node 2"
                                    } else {
                                        "Joint: [${pt1.partName}]#${pt1.vertexIndex} to [${pt2.partName}]#${pt2.vertexIndex}"
                                    },
                                    color = CyberWhite,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (pt1 != null) {
                                        "Local Coordinates: (${pt1.localCoords.x.toInt()}, ${pt1.localCoords.y.toInt()}, ${pt1.localCoords.z.toInt()}) mm"
                                    } else {
                                        "Awaiting laser vertex target locks..."
                                    },
                                    color = CyberGrey,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Distance readout
                        val dist = viewModel.getMeasuredDistance()
                        if (dist != null) {
                            Text(
                                text = String.format("%.2f mm", dist),
                                color = CyberAmber,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "Awaiting...",
                                color = CyberGrey,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 6. Comprehensive Multipart OBJ Importer dialog
        if (showImportDialog) {
            var inputPartName by remember { mutableStateOf("Interstellar core segment") }
            var selectedFileFormat by remember { mutableStateOf("cylindrical_pipe") } // "cylindrical_pipe", "spherical_bubble", "pastable_obj"
            var inputObjText by remember { mutableStateOf(getObjTemplateText()) }

            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = CyberSteel,
                shape = RoundedCornerShape(12.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = CyberNeonCyan,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "CYBER IMPORT TERMINAL",
                            color = CyberNeonCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Import or upload CAD geometry definitions seamlessly into your visual assemblies.",
                            color = CyberGrey,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        DropdownStyleInput(
                            label = "Component Spec Name:",
                            value = inputPartName,
                            onValueChange = { inputPartName = it },
                            placeholder = "Name (e.g. Laser core injector)"
                        )

                        // Mode Selector (Ready-made parametric files vs Paste Raw OBJ data)
                        Text(
                            "CAD File Interface Format:",
                            color = CyberNeonCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ImportFormatButton("CYLINDER", selectedFileFormat == "cylindrical_pipe") {
                                selectedFileFormat = "cylindrical_pipe"
                            }
                            ImportFormatButton("SPHERE", selectedFileFormat == "spherical_bubble") {
                                selectedFileFormat = "spherical_bubble"
                            }
                            ImportFormatButton("PASTE OBJ", selectedFileFormat == "pastable_obj") {
                                selectedFileFormat = "pastable_obj"
                            }
                        }

                        Button(
                            onClick = {
                                pickerLauncher.launch(arrayOf("*/*"))
                                showImportDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGrid),
                            border = BorderStroke(1.dp, CyberNeonCyan),
                            modifier = Modifier.fillMaxWidth().height(36.dp).testTag("select_device_file_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Folder", tint = CyberNeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "PICK CAD FILE FROM DEVICE (OBJ, STL, STEP, ...)",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = CyberNeonCyan
                            )
                        }

                        // If "Paste Raw OBJ" mode is active, display a multiline text area
                        if (selectedFileFormat == "pastable_obj") {
                            Text(
                                "Raw Wavefront .obj Text Payload:",
                                color = CyberGrey,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            OutlinedTextField(
                                value = inputObjText,
                                onValueChange = { inputObjText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .testTag("obj_text_area"),
                                textStyle = TextStyle(
                                    color = CyberWhite,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberNeonCyan,
                                    unfocusedBorderColor = CyberGrid,
                                    focusedContainerColor = CyberOnyx,
                                    unfocusedContainerColor = CyberOnyx
                                )
                            )
                        }

                        // Target Destination: Import to Existing Assembly vs Create New Scene
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Target Destination:",
                                color = CyberWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { importIntoExisting = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (importIntoExisting) CyberGrid else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("EXISTING", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CyberWhite)
                                }
                                Button(
                                    onClick = { importIntoExisting = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!importIntoExisting) CyberGrid else Color.Transparent
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("NEW FILE", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = CyberWhite)
                                }
                            }
                        }

                        // Color selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Visual Base Hue:",
                                color = CyberWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ColorDot("#FF00E5FF", selectedHexColorForImport == "#FF00E5FF") { selectedHexColorForImport = "#FF00E5FF" }
                                ColorDot("#FFFF5252", selectedHexColorForImport == "#FFFF5252") { selectedHexColorForImport = "#FFFF5252" }
                                ColorDot("#FFFFC400", selectedHexColorForImport == "#FFFFC400") { selectedHexColorForImport = "#FFFFC400" }
                                ColorDot("#FF00E676", selectedHexColorForImport == "#FF00E676") { selectedHexColorForImport = "#FF00E676" }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsedHex = if (selectedHexColorForImport.startsWith("#")) selectedHexColorForImport else "#FF00E5FF"
                            val parsedColor = try {
                                Color(android.graphics.Color.parseColor(parsedHex))
                            } catch (e: Exception) {
                                CyberNeonCyan
                            }

                            if (selectedFileFormat == "pastable_obj") {
                                viewModel.parseAndImportObjString(inputPartName, inputObjText, parsedColor, importIntoExisting)
                            } else {
                                if (importIntoExisting) {
                                    viewModel.importPartIntoAssembly(inputPartName, selectedFileFormat, parsedColor)
                                } else {
                                    viewModel.createNewAssemblyWithPart(inputPartName, selectedFileFormat, parsedColor)
                                }
                            }
                            Toast.makeText(context, "Component import complete!", Toast.LENGTH_SHORT).show()
                            showImportDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberNeonCyan),
                        modifier = Modifier.testTag("apply_import_btn")
                    ) {
                        Text("ENGAGE IMPORT", color = CyberOnyx, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("CANCEL", color = CyberGrey, fontFamily = FontFamily.Monospace)
                    }
                }
            )
        }

        if (viewModel.isGeneratingRender.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = CyberNeonCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "COMPILING HIGH-FIDELITY GPU SHADERS...",
                        color = CyberNeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Executing 144 double-precision ray casting iterations\nComputing Ambient Occlusion and shadow maps...",
                        color = CyberGrey,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (viewModel.capturedImage.value != null) {
            val capturedBmp = viewModel.capturedImage.value!!
            AlertDialog(
                onDismissRequest = { viewModel.capturedImage.value = null },
                containerColor = CyberOnyx,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(1.dp, CyberNeonCyan, RoundedCornerShape(16.dp)),
                title = {
                    Text(
                        "GPU CAD OFFLINE SNAPSHOT",
                        color = CyberNeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            bitmap = capturedBmp.asImageBitmap(),
                            contentDescription = "Rendered CAD Picture",
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, CyberGrid)
                        )
                        Text(
                            "Resolution: 1024x1024 (Anti-Aliased)\nLocation: Pictures/CADViewer\nGPU Shading Pass completed successfully.",
                            color = CyberGrey,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val savedUri = saveBitmapToGallery(context, capturedBmp)
                            if (savedUri != null) {
                                shareImageUri(context, savedUri)
                            } else {
                                Toast.makeText(context, "Failed to prep sharing Uri.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberNeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = CyberOnyx, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE RENDER", color = CyberOnyx, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.capturedImage.value = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE PREVIEW", color = CyberGrey, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            )
        }
    }
}

// --- Dynamic 3D Projection Canvas Component ---

@Composable
fun Cad3DCanvas(
    viewModel: CadViewerViewModel,
    assembly: CadAssembly,
    isLeftEye: Boolean?, // null: single view, true: left side, false: right side
    modifier: Modifier = Modifier
) {
    var isInteracting by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier
            .testTag("renderer_canvas")
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }
                        if (isInteracting != pressed) {
                            isInteracting = pressed
                        }
                    }
                }
            }
            .pointerInput(viewModel.isMeasurementModeActive.value) {
                if (viewModel.isMeasurementModeActive.value) {
                    detectTapGestures { tapOffset ->
                        viewModel.onCanvasTapped(tapOffset)
                    }
                } else {
                    detectTransformGestures { centroid, pan, zoomAmount, rotation ->
                        viewModel.cameraYaw.value = (viewModel.cameraYaw.value + pan.x * 0.005f)
                        viewModel.cameraPitch.value = (viewModel.cameraPitch.value + pan.y * 0.005f).coerceIn(-1.5f, 1.5f)
                        viewModel.zoom.value = (viewModel.zoom.value * zoomAmount).coerceIn(0.3f, 4.0f)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        val halfW = width / 2f
        val halfH = height / 2f

        val viewCenter = when (isLeftEye) {
            true -> Offset(width / 4f, height / 2f)
            false -> Offset(3 * width / 4f, height / 2f)
            null -> Offset(width / 2f, height / 2f)
        }

        val scaleMultiplier = viewModel.zoom.value * if (isLeftEye != null) 0.65f else 1.0f

        val eyeShift = if (isLeftEye != null) {
            val factor = if (isLeftEye) -1f else 1f
            factor * viewModel.vrEyeSeparation.value
        } else {
            0f
        }

        val yaw = viewModel.cameraYaw.value
        val pitch = viewModel.cameraPitch.value
        val roll = viewModel.cameraRoll.value
        val panXVal = viewModel.panX.value + eyeShift
        val panYVal = viewModel.panY.value

        val sortedFaces = mutableListOf<RenderFaceRecord>()
        val bodyProjectionMap = mutableMapOf<String, Map<Int, Offset>>()

        val totalAssemblyFaces = assembly.bodies.sumOf { it.faces.size }
        val lodStep = if (isInteracting) {
            when {
                totalAssemblyFaces > 300000 -> 8
                totalAssemblyFaces > 150000 -> 4
                totalAssemblyFaces > 75000 -> 2
                else -> 1
            }
        } else {
            1
        }

        assembly.bodies.forEach { body ->
            if (viewModel.bodyVisibility[body.id] == false) return@forEach

            val vertexWorldPositions = mutableListOf<Vector3D>()
            val bodyProjectedPositions = mutableMapOf<Int, Offset>()

            val facesToRender = if (lodStep > 1) {
                body.faces.filterIndexed { index, _ -> index % lodStep == 0 }
            } else {
                body.faces
            }

            val vertexUsed = BooleanArray(body.vertices.size)
            facesToRender.forEach { face ->
                face.vertexIndices.forEach { vIdx ->
                    if (vIdx in vertexUsed.indices) {
                        vertexUsed[vIdx] = true
                    }
                }
            }

            body.vertices.forEachIndexed { vIdx, v ->
                if (!vertexUsed[vIdx]) {
                    bodyProjectedPositions[vIdx] = Offset.Zero
                    vertexWorldPositions.add(Vector3D(0f, 0f, 0f))
                    return@forEachIndexed
                }

                val explodedOffset = if (viewModel.explosionScale.value > 0f) {
                    v + (body.explosionDirection * viewModel.explosionScale.value * 65f)
                } else {
                    v
                }

                val rotated = explodedOffset.rotate(yaw, pitch, roll)

                val worldX = rotated.x * scaleMultiplier + panXVal
                val worldY = rotated.y * scaleMultiplier + panYVal
                val worldZ = rotated.z * scaleMultiplier + 450f

                val worldPos = Vector3D(worldX, worldY, worldZ)
                vertexWorldPositions.add(worldPos)

                val perspectiveRatio = 450f / worldZ
                val screenX = viewCenter.x + (worldX * perspectiveRatio)
                val screenY = viewCenter.y + (worldY * perspectiveRatio)
                val screenPos = Offset(screenX, screenY)

                bodyProjectedPositions[vIdx] = screenPos
            }

            bodyProjectionMap[body.id] = bodyProjectedPositions

            facesToRender.forEach { face ->
                if (face.vertexIndices.isEmpty()) return@forEach

                if (viewModel.sliceAxis.value != SliceAxis.NONE) {
                    var sumCoord = 0f
                    face.vertexIndices.forEach { vIdx ->
                        val localV = body.vertices[vIdx]
                        sumCoord += when (viewModel.sliceAxis.value) {
                            SliceAxis.X -> localV.x
                            SliceAxis.Y -> localV.y
                            SliceAxis.Z -> localV.z
                            else -> 0f
                        }
                    }
                    val avgCoord = sumCoord / face.vertexIndices.size
                    if (avgCoord > viewModel.slicePlaneValue.value) {
                        return@forEach
                    }
                }

                val projectedFaceCoords = face.vertexIndices.map { idx ->
                    bodyProjectedPositions[idx] ?: Offset.Zero
                }

                val worldFaceCoords = face.vertexIndices.map { idx ->
                    if (idx in vertexWorldPositions.indices) vertexWorldPositions[idx] else Vector3D(0f, 0f, 0f)
                }

                var sumZ = 0f
                worldFaceCoords.forEach { sumZ += it.z }
                val avgFaceZ = sumZ / worldFaceCoords.size

                var skipFace = false
                if (viewModel.renderMode.value == RenderMode.SOLID || viewModel.renderMode.value == RenderMode.RAY_TRACING || viewModel.renderMode.value == RenderMode.FLAT) {
                    if (projectedFaceCoords.size >= 3) {
                        val p0 = projectedFaceCoords[0]
                        val p1 = projectedFaceCoords[1]
                        val p2 = projectedFaceCoords[2]
                        val cross = (p1.x - p0.x) * (p2.y - p0.y) - (p1.y - p0.y) * (p2.x - p0.x)
                        if (cross < 0f) {
                            skipFace = true
                        }
                    }
                }

                if (!skipFace) {
                    sortedFaces.add(
                        RenderFaceRecord(
                            body = body,
                            face = face,
                            avgDepth = avgFaceZ,
                            projectedCoords = projectedFaceCoords,
                            worldCoords = worldFaceCoords
                        )
                    )
                }
            }
        }

        if (isLeftEye == null || isLeftEye == true) {
            viewModel.projectedCoordinateCache.clear()
            viewModel.projectedCoordinateCache.putAll(bodyProjectionMap)
        }

        sortedFaces.sortByDescending { it.avgDepth }

        // DRAW COGNITIONS:

        // 1. Raytrace Ground Drop Shadow pass
        if (viewModel.renderMode.value == RenderMode.RAY_TRACING) {
            val shadowYOffset = viewCenter.y + 130f * scaleMultiplier
            sortedFaces.forEach { faceRecord ->
                val shadowCoords = faceRecord.projectedCoords.map { pt ->
                    Offset(pt.x + (pt.x - viewCenter.x) * 0.05f, shadowYOffset + (pt.y - shadowYOffset) * 0.02f)
                }

                val path = Path().apply {
                    if (shadowCoords.isNotEmpty()) {
                        moveTo(shadowCoords[0].x, shadowCoords[0].y)
                        for (i in 1 until shadowCoords.size) {
                            lineTo(shadowCoords[i].x, shadowCoords[i].y)
                        }
                        close()
                    }
                }
                drawPath(
                    path = path,
                    color = CyberOnyx.copy(alpha = 0.5f)
                )
            }

            drawCircle(
                color = CyberWhite.copy(alpha = 0.02f),
                radius = 180f * scaleMultiplier,
                center = Offset(viewCenter.x, shadowYOffset),
                style = Stroke(width = 2f)
            )
        }

        // 2. Draw sorted faces
        sortedFaces.forEach { faceRecord ->
            val body = faceRecord.body
            val face = faceRecord.face
            val pts = faceRecord.projectedCoords
            val wPts = faceRecord.worldCoords

            if (pts.isEmpty()) return@forEach

            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x, pts[i].y)
                }
                close()
            }

            val p0 = wPts[0]
            val p1 = wPts[1]
            val p2 = wPts[2]
            val normal = (p1 - p0).cross(p2 - p0).normalize()

            val activeScene = viewModel.currentLightScene.value
            val mainLightDir = activeScene.mainLightDirection
            val fillLightDir = activeScene.fillLightDirection

            val diffuseMain = normal.dot(mainLightDir).coerceAtLeast(0f)
            val diffuseFill = normal.dot(fillLightDir).coerceAtLeast(0f)

            when (viewModel.renderMode.value) {
                RenderMode.FLAT -> {
                    drawPath(path = path, color = face.color)

                    if (viewModel.showMeshEdges.value) {
                        drawPath(
                            path = path,
                            color = face.color.copy(alpha = 0.3f),
                            style = Stroke(width = 1f)
                        )
                    }
                }
                RenderMode.SOLID, RenderMode.RAY_TRACING -> {
                    val ambientRatio = activeScene.ambientLevel * viewModel.ambientIntensity.value
                    val mainIntensity = viewModel.mainLightIntensity.value
                    val fillIntensity = 0.2f * viewModel.mainLightIntensity.value
                    val activeMainColor = activeScene.mainLightColor
                    val activeFillColor = activeScene.fillLightColor

                    val rMix = (face.color.red * ambientRatio + 
                                face.color.red * activeMainColor.red * diffuseMain * mainIntensity + 
                                face.color.red * activeFillColor.red * diffuseFill * fillIntensity).coerceIn(0f, 1f)
                    val gMix = (face.color.green * ambientRatio + 
                                face.color.green * activeMainColor.green * diffuseMain * mainIntensity + 
                                face.color.green * activeFillColor.green * diffuseFill * fillIntensity).coerceIn(0f, 1f)
                    val bMix = (face.color.blue * ambientRatio + 
                                face.color.blue * activeMainColor.blue * diffuseMain * mainIntensity + 
                                face.color.blue * activeFillColor.blue * diffuseFill * fillIntensity).coerceIn(0f, 1f)

                    var faceColor = Color(rMix, gMix, bMix)

                    if (viewModel.renderMode.value == RenderMode.RAY_TRACING) {
                        var centerDist = 0f
                        wPts.forEach { centerDist += it.length() }
                        val avgCenter = centerDist / wPts.size
                        val aoFactor = (avgCenter / 450f).coerceIn(0.6f, 1.0f)
                        faceColor = faceColor.copy(
                            red = faceColor.red * aoFactor,
                            green = faceColor.green * aoFactor,
                            blue = faceColor.blue * aoFactor
                        )
                    }

                    drawPath(path = path, color = faceColor)

                    if (viewModel.showMeshEdges.value) {
                        drawPath(
                            path = path,
                            color = if (viewModel.renderMode.value == RenderMode.RAY_TRACING) CyberWhite.copy(alpha = 0.15f) else face.color.copy(alpha = 0.3f),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                RenderMode.WIREFRAME -> {
                    drawPath(
                        path = path,
                        color = face.color.copy(alpha = 0.75f),
                        style = Stroke(width = 2f)
                    )
                }

                RenderMode.HOLOGRAM -> {
                    drawPath(
                        path = path,
                        color = CyberNeonCyan.copy(alpha = 0.08f)
                    )
                    drawPath(
                        path = path,
                        color = CyberNeonCyan.copy(alpha = 0.45f),
                        style = Stroke(width = 1f)
                    )
                }
            }
        }

        // 3. Draw active Laser measurement lines
        if (viewModel.isMeasurementModeActive.value) {
            val pt1 = viewModel.firstSelectedVertex.value
            val pt2 = viewModel.secondSelectedVertex.value

            if (pt1 != null) {
                drawCircle(
                    color = CyberAmber,
                    radius = 8.dp.toPx(),
                    center = pt1.screenPos,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = CyberAmber.copy(alpha = 0.2f),
                    radius = 16.dp.toPx(),
                    center = pt1.screenPos
                )
            }
            if (pt2 != null) {
                drawCircle(
                    color = CyberAmber,
                    radius = 8.dp.toPx(),
                    center = pt2.screenPos,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = CyberAmber.copy(alpha = 0.2f),
                    radius = 16.dp.toPx(),
                    center = pt2.screenPos
                )

                drawLine(
                    color = CyberAmber,
                    start = pt1!!.screenPos,
                    end = pt2.screenPos,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

data class RenderFaceRecord(
    val body: SolidBody,
    val face: Face3D,
    val avgDepth: Float,
    val projectedCoords: List<Offset>,
    val worldCoords: List<Vector3D>
)

@Composable
fun RenderModeButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) CyberNeonCyan else CyberSteel
        ),
        border = BorderStroke(1.dp, if (isActive) CyberNeonCyan else CyberGrid),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        modifier = modifier
            .height(34.dp)
            .testTag("render_${label.lowercase()}_btn")
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isActive) CyberOnyx else CyberWhite
        )
    }
}

@Composable
fun PredefinedModelItem(
    title: String,
    desc: String,
    onClick: () -> Unit,
    isActive: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) CyberSteel else CyberSteel.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) CyberNeonCyan else CyberGrid.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("preset_${title.replace(" ", "_").lowercase()}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                color = if (isActive) CyberNeonCyan else CyberWhite,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                color = CyberGrey,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ImportFormatButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) CyberGrid else CyberOverlay
        ),
        border = BorderStroke(1.dp, if (isSelected) CyberNeonCyan else CyberGrid),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.height(28.dp).testTag("format_${label.lowercase()}")
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = CyberWhite
        )
    }
}

@Composable
fun ColorDot(
    hex: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(android.graphics.Color.parseColor(hex)))
            .border(
                2.dp,
                if (isSelected) CyberWhite else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .testTag("color_dot_$hex")
    )
}

@Composable
fun SliceAxisButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(height = 24.dp, width = 42.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) CyberNeonCyan else CyberSteel)
            .border(1.dp, if (isActive) CyberNeonCyan else CyberGrid, shape = RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .testTag("slice_btn_${label.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isActive) CyberOnyx else CyberWhite
        )
    }
}

@Composable
fun DropdownStyleInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            text = label,
            color = CyberGrey,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CyberGrey) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("input_part_name"),
            textStyle = TextStyle(color = CyberWhite, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberNeonCyan,
                unfocusedBorderColor = CyberGrid,
                focusedContainerColor = CyberOnyx,
                unfocusedContainerColor = CyberOnyx
            )
        )
    }
}

fun getObjTemplateText(): String {
    return """
        # Custom Interstellar Core Component
        # Preloaded Cyber OBJ Mesh Format
        v -1.5 -1.5 2.0
        v 1.5 -1.5 2.0
        v 1.5 1.5 2.0
        v -1.5 1.5 2.0
        v -1.5 -1.5 -2.0
        v 1.5 -1.5 -2.0
        v 1.5 1.5 -2.0
        v -1.5 1.5 -2.0
        f 1 2 3 4
        f 6 5 8 7
        f 5 1 4 8
        f 2 6 7 3
        f 5 6 2 1
        f 4 3 7 8
    """.trimIndent()
}
