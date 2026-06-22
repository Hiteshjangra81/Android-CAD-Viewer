package com.example.cad

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class RenderMode {
    WIREFRAME,
    SOLID,
    FLAT,
    HOLOGRAM,
    RAY_TRACING
}

enum class SliceAxis {
    NONE,
    X,
    Y,
    Z
}

enum class LightScene(
    val displayName: String,
    val mainLightColor: Color,
    val fillLightColor: Color,
    val mainLightDirection: Vector3D,
    val fillLightDirection: Vector3D,
    val ambientLevel: Float
) {
    SPACE_LAB(
        "Engineering Dark",
        Color(0xFFFFFFFF), // Pure White
        Color(0xFFB0BEC5), // Slate Silver Fill
        Vector3D(1.0f, -1.2f, -1.0f).normalize(),
        Vector3D(-1.0f, 0.8f, 1.0f).normalize(),
        0.35f
    ),
    CYBERPUNK(
        "Industrial Grey",
        Color(0xFFFFFFFF), // Pure White
        Color(0xFF90A4AE), // Cool Steel Fill
        Vector3D(0.5f, -1.0f, -0.8f).normalize(),
        Vector3D(-0.5f, 0.5f, 0.8f).normalize(),
        0.28f
    ),
    GOLDEN_HOUR(
        "Clear Daylight",
        Color(0xFFFFFFFF), // Pure White
        Color(0xFFCFD8DC), // Clear Sky Fill
        Vector3D(1.5f, -0.4f, -0.5f).normalize(),
        Vector3D(-1.5f, 1.2f, 0.5f).normalize(),
        0.45f
    ),
    CLINICAL_STUDIO(
        "Cleanroom Studio",
        Color(0xFFFFFFFF), // Pure White
        Color(0xFFECEFF1), // Balanced Light Fill
        Vector3D(0.0f, -2.0f, -1.0f).normalize(),
        Vector3D(1.0f, 1.0f, 1.0f).normalize(),
        0.55f
    )
}

data class SelectedVertexInfo(
    val partName: String,
    val vertexIndex: Int,
    val localCoords: Vector3D,
    val screenPos: Offset
)

class CadViewerViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    // --- State Management ---
    private val _assembly = MutableStateFlow<CadAssembly>(ModelFactory.createFusionReactor())
    val assembly = _assembly.asStateFlow()

    // Active hidden state per body id
    val bodyVisibility = mutableStateMapOf<String, Boolean>()

    // Camera parameters
    val cameraYaw = mutableStateOf(0.7f) // Radians
    val cameraPitch = mutableStateOf(-0.5f) // Radians
    val cameraRoll = mutableStateOf(0f)
    val panX = mutableStateOf(0f)
    val panY = mutableStateOf(0f)
    val zoom = mutableStateOf(1.0f) // Scale multiplier

    // Exploded View Scale
    val explosionScale = mutableStateOf(0f) // 0.0 to 1.0

    // Slicing Mode
    val sliceAxis = mutableStateOf(SliceAxis.NONE)
    val slicePlaneValue = mutableStateOf(0f) // -150f to 150f

    // Shading Render Mode
    val renderMode = mutableStateOf(RenderMode.SOLID)
    val showMeshEdges = mutableStateOf(false)

    // VR Stereoscopic Mode
    val isVrMode = mutableStateOf(false)
    val vrEyeSeparation = mutableStateOf(40f) // Pixels in eye offset

    // VR Gyroscope Controller state (Real Sensors)
    val isGyroEnabled = mutableStateOf(false)

    // Measurement Mode
    val isMeasurementModeActive = mutableStateOf(false)
    val firstSelectedVertex = mutableStateOf<SelectedVertexInfo?>(null)
    val secondSelectedVertex = mutableStateOf<SelectedVertexInfo?>(null)

    // Cached projected vertex coordinates (vertex index in the assembly body to Offset)
    val projectedCoordinateCache = mutableMapOf<String, Map<Int, Offset>>()

    // Environment Lighting and Offline CAD rendering variables
    val currentLightScene = mutableStateOf(LightScene.SPACE_LAB)
    val mainLightIntensity = mutableStateOf(1.0f)
    val ambientIntensity = mutableStateOf(0.35f)
    val isGeneratingRender = mutableStateOf(false)
    val capturedImage = mutableStateOf<Bitmap?>(null)

    // Sensor Management
    private val sensorManager: SensorManager? by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private var rotationSensor: Sensor? = null

    init {
        // Prepare initial visibility map
        refreshVisibilityMap()
        rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private fun refreshVisibilityMap() {
        bodyVisibility.clear()
        _assembly.value.bodies.forEach { body ->
            bodyVisibility[body.id] = body.isVisible
        }
    }

    // --- Model Modification & File Imports ---

    fun loadAssembly(type: String) {
        viewModelScope.launch {
            val newAssembly = when (type) {
                "reactor" -> ModelFactory.createFusionReactor()
                "mech_arm" -> ModelFactory.createMechArm()
                "aero_turbine" -> ModelFactory.createAeroCompressor()
                "impeller" -> ModelFactory.createHypersonicImpeller()
                else -> ModelFactory.createFusionReactor()
            }
            _assembly.value = newAssembly
            refreshVisibilityMap()
            resetViewport()
            clearMeasurement()
        }
    }

    fun importPartIntoAssembly(partName: String, geometryType: String, baseColor: Color) {
        viewModelScope.launch {
            val currentBodies = _assembly.value.bodies.toMutableList()
            val newBody = when (geometryType) {
                "cylinder" -> ModelFactory.createCylinder(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    radius = 45f,
                    length = 70f,
                    segments = 12,
                    color = baseColor,
                    center = Vector3D(0f, 0f, 0f),
                    explosionDir = Vector3D(0f, 0f, 1f)
                )
                "sphere" -> ModelFactory.createSphere(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    radius = 40f,
                    rings = 6,
                    sectors = 10,
                    color = baseColor,
                    center = Vector3D(0f, 0f, 0f),
                    explosionDir = Vector3D(0f, 0f, 1f)
                )
                else -> ModelFactory.createBox(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    width = 40f,
                    height = 40f,
                    depth = 40f,
                    color = baseColor,
                    center = Vector3D(0f, 0f, 0f),
                    explosionDir = Vector3D(0f, 0f, 1f)
                )
            }
            currentBodies.add(newBody)
            _assembly.value = _assembly.value.copy(bodies = currentBodies)
            bodyVisibility[newBody.id] = true
        }
    }

    fun createNewAssemblyWithPart(partName: String, geometryType: String, baseColor: Color) {
        viewModelScope.launch {
            val newBody = when (geometryType) {
                "cylinder" -> ModelFactory.createCylinder(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    radius = 45f,
                    length = 70f,
                    segments = 12,
                    color = baseColor,
                    explosionDir = Vector3D(0f, 1f, 0f)
                )
                "sphere" -> ModelFactory.createSphere(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    radius = 45f,
                    rings = 7,
                    sectors = 12,
                    color = baseColor,
                    explosionDir = Vector3D(0f, 1f, 0f)
                )
                else -> ModelFactory.createBox(
                    id = "imported_${System.currentTimeMillis()}",
                    name = partName,
                    width = 50f,
                    height = 50f,
                    depth = 50f,
                    color = baseColor,
                    explosionDir = Vector3D(0f, 1f, 0f)
                )
            }
            _assembly.value = CadAssembly(name = "$partName Assembly", listOf(newBody))
            refreshVisibilityMap()
            resetViewport()
            clearMeasurement()
        }
    }

    fun parseAndImportObjString(fileName: String, objText: String, color: Color, importIntoExisting: Boolean) {
        viewModelScope.launch {
            val parsedBody = ModelFactory.parseObjString(fileName, objText, color)
            val updatedBodies = if (importIntoExisting) {
                _assembly.value.bodies.toMutableList().apply { add(parsedBody) }
            } else {
                listOf(parsedBody)
            }
            val assemblyName = if (importIntoExisting) _assembly.value.name else "$fileName Assembly"
            _assembly.value = CadAssembly(assemblyName, updatedBodies)
            refreshVisibilityMap()
            resetViewport()
            clearMeasurement()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "unnamed.obj"
    }

    fun importCadFileFromUri(context: Context, uri: Uri, customColor: Color = Color(0xFF00FF99), importIntoExisting: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val fileName = getFileName(context, uri)
                var fileSize: Long = 0
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                                if (sizeIndex != -1) {
                                    fileSize = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                    } catch (ignored: Exception) {}
                }

                val importedBodies = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open data stream.")
                    val lowercaseName = fileName.lowercase()
                    when {
                        lowercaseName.endsWith(".obj") -> {
                            listOf(ModelFactory.parseObjStream(fileName, inputStream, fileSize, customColor))
                        }
                        lowercaseName.endsWith(".stl") -> {
                            listOf(ModelFactory.parseStlStream(fileName, inputStream, fileSize, customColor))
                        }
                        lowercaseName.endsWith(".stp") || lowercaseName.endsWith(".step") -> {
                            ModelFactory.parseStepStream(fileName, inputStream, fileSize, customColor)
                        }
                        else -> {
                            ModelFactory.parseStepStream(fileName, inputStream, fileSize, customColor)
                        }
                    }
                }

                val updatedBodies = if (importIntoExisting) {
                    _assembly.value.bodies.toMutableList().apply { addAll(importedBodies) }
                } else {
                    importedBodies
                }

                val assemblyName = if (importIntoExisting) _assembly.value.name else "$fileName Assembly"
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _assembly.value = CadAssembly(assemblyName, updatedBodies)
                    refreshVisibilityMap()
                    resetViewport()
                    clearMeasurement()

                    val totalTriangles = importedBodies.sumOf { it.faces.size }
                    Toast.makeText(context, "Imported '$fileName' (${importedBodies.size} bodies, $totalTriangles triangles) successfully!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "CAD Import Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- Interaction Operations ---

    fun toggleVisibility(bodyId: String) {
        val current = bodyVisibility[bodyId] ?: true
        bodyVisibility[bodyId] = !current
    }

    fun isolateBody(bodyId: String) {
        bodyVisibility.keys.forEach { id ->
            bodyVisibility[id] = (id == bodyId)
        }
    }

    fun showAllBodies() {
        bodyVisibility.keys.forEach { id ->
            bodyVisibility[id] = true
        }
    }

    fun resetViewport() {
        cameraYaw.value = 0.7f
        cameraPitch.value = -0.5f
        cameraRoll.value = 0f
        panX.value = 0f
        panY.value = 0f
        zoom.value = 1.0f
        explosionScale.value = 0f
        sliceAxis.value = SliceAxis.NONE
        isMeasurementModeActive.value = false
        clearMeasurement()
    }

    // --- Distance Measurement ---

    fun onCanvasTapped(tapPos: Offset) {
        if (!isMeasurementModeActive.value) return

        // Find nearest vertex from our cache
        var nearestVertex: SelectedVertexInfo? = null
        var minDistance = 24 * 24 // within 24 pixels threshold

        projectedCoordinateCache.forEach { (bodyId, vertexProjections) ->
            if (bodyVisibility[bodyId] != false) {
                val body = _assembly.value.bodies.find { it.id == bodyId } ?: return@forEach
                vertexProjections.forEach { (vertexIdx, screenOffset) ->
                    val dx = tapPos.x - screenOffset.x
                    val dy = tapPos.y - screenOffset.y
                    val distSq = dx * dx + dy * dy
                    if (distSq < minDistance) {
                        minDistance = distSq.toInt()
                        nearestVertex = SelectedVertexInfo(
                            partName = body.name,
                            vertexIndex = vertexIdx,
                            localCoords = body.vertices[vertexIdx],
                            screenPos = screenOffset
                        )
                    }
                }
            }
        }

        nearestVertex?.let { selected ->
            if (firstSelectedVertex.value == null) {
                firstSelectedVertex.value = selected
            } else if (secondSelectedVertex.value == null) {
                secondSelectedVertex.value = selected
            } else {
                // Cycle selections
                firstSelectedVertex.value = selected
                secondSelectedVertex.value = null
            }
        }
    }

    fun clearMeasurement() {
        firstSelectedVertex.value = null
        secondSelectedVertex.value = null
    }

    fun getMeasuredDistance(): Float? {
        val v1 = firstSelectedVertex.value?.localCoords ?: return null
        val v2 = secondSelectedVertex.value?.localCoords ?: return null
        return v1.distanceTo(v2)
    }

    // --- Gyroscope / VR Sensing ---

    fun enableGyroscope(enable: Boolean) {
        if (enable) {
            rotationSensor?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                isGyroEnabled.value = true
            }
        } else {
            sensorManager?.unregisterListener(this)
            isGyroEnabled.value = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isGyroEnabled.value) return
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientationValues = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientationValues)

            // Map gyroscope orientation directly to our camera offsets
            cameraYaw.value = orientationValues[0]      // Azimuth (yaw)
            cameraPitch.value = orientationValues[1]    // Pitch
            cameraRoll.value = orientationValues[2]     // Roll
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
    }
}
