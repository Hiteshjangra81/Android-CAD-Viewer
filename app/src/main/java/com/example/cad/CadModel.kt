package com.example.cad

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Face3D(
    val vertexIndices: List<Int>,
    val color: Color,
    val material: String = "Matte", // "Metal", "Glass", "Glowing", "Matte"
    val isOutlineOnly: Boolean = false
)

data class SolidBody(
    val id: String,
    val name: String,
    val vertices: List<Vector3D>,
    val faces: List<Face3D>,
    val baseColor: Color,
    val isVisible: Boolean = true,
    val explosionDirection: Vector3D = Vector3D(0f, 0f, 0f)
) {
    // Calculates a fast face normal in world space after vertex positioning
    fun calculateNormal(face: Face3D, positions: List<Vector3D>): Vector3D {
        if (face.vertexIndices.size < 3) return Vector3D(0f, 0f, 1f)
        val v0 = positions[face.vertexIndices[0]]
        val v1 = positions[face.vertexIndices[1]]
        val v2 = positions[face.vertexIndices[2]]
        return (v1 - v0).cross(v2 - v0).normalize()
    }
}

data class CadAssembly(
    val name: String,
    val bodies: List<SolidBody>
)

object ModelFactory {

    // --- Programmatic Shape Helpers ---

    fun createBox(
        id: String,
        name: String,
        width: Float,
        height: Float,
        depth: Float,
        color: Color,
        material: String = "Matte",
        center: Vector3D = Vector3D(),
        explosionDir: Vector3D = Vector3D()
    ): SolidBody {
        val w2 = width / 2f
        val h2 = height / 2f
        val d2 = depth / 2f

        val vertices = listOf(
            Vector3D(-w2, -h2, -d2) + center,
            Vector3D(w2, -h2, -d2) + center,
            Vector3D(w2, h2, -d2) + center,
            Vector3D(-w2, h2, -d2) + center,
            Vector3D(-w2, -h2, d2) + center,
            Vector3D(w2, -h2, d2) + center,
            Vector3D(w2, h2, d2) + center,
            Vector3D(-w2, h2, d2) + center
        )

        val faces = listOf(
            // Front (z = -d2)
            Face3D(listOf(0, 1, 2, 3), color, material),
            // Back (z = d2)
            Face3D(listOf(5, 4, 7, 6), color, material),
            // Left (x = -w2)
            Face3D(listOf(4, 0, 3, 7), color, material),
            // Right (x = w2)
            Face3D(listOf(1, 5, 6, 2), color, material),
            // Top (y = -h2)
            Face3D(listOf(4, 5, 1, 0), color, material),
            // Bottom (y = h2)
            Face3D(listOf(3, 2, 6, 7), color, material)
        )

        return SolidBody(id, name, vertices, faces, color, explosionDirection = explosionDir)
    }

    fun createCylinder(
        id: String,
        name: String,
        radius: Float,
        length: Float,
        segments: Int = 12,
        color: Color,
        material: String = "Metal",
        center: Vector3D = Vector3D(),
        explosionDir: Vector3D = Vector3D()
    ): SolidBody {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()
        val h2 = length / 2f

        // Bottom Cap Vertices index 0 to segments-1
        for (i in 0 until segments) {
            val angle = 2 * PI.toFloat() * i / segments
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            vertices.add(Vector3D(x, -h2, z) + center)
        }
        // Bottom Center vertex index segments
        vertices.add(Vector3D(0f, -h2, 0f) + center)
        val bottomCenterIdx = segments

        // Top Cap Vertices index segments+1 to 2*segments
        for (i in 0 until segments) {
            val angle = 2 * PI.toFloat() * i / segments
            val x = radius * cos(angle)
            val z = radius * sin(angle)
            vertices.add(Vector3D(x, h2, z) + center)
        }
        // Top Center vertex index 2*segments+1
        vertices.add(Vector3D(0f, h2, 0f) + center)
        val topCenterIdx = 2 * segments + 1

        // Bottom Cap Faces
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            faces.add(Face3D(listOf(bottomCenterIdx, next, i), color, material))
        }

        // Top Cap Faces
        val offset = segments + 1
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            faces.add(Face3D(listOf(topCenterIdx, i + offset, next + offset), color, material))
        }

        // Side Walls
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            faces.add(
                Face3D(
                    listOf(
                        i,
                        next,
                        next + offset,
                        i + offset
                    ),
                    color,
                    material
                )
            )
        }

        return SolidBody(id, name, vertices, faces, color, explosionDirection = explosionDir)
    }

    fun createSphere(
        id: String,
        name: String,
        radius: Float,
        rings: Int = 8,
        sectors: Int = 12,
        color: Color,
        material: String = "Glass",
        center: Vector3D = Vector3D(),
        explosionDir: Vector3D = Vector3D()
    ): SolidBody {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()

        // Generate vertices
        for (r in 0..rings) {
            val phi = PI.toFloat() * r / rings
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)

            for (s in 0 until sectors) {
                val theta = 2 * PI.toFloat() * s / sectors
                val cosTheta = cos(theta)
                val sinTheta = sin(theta)

                val x = radius * sinPhi * cosTheta
                val y = radius * cosPhi
                val z = radius * sinPhi * sinTheta

                vertices.add(Vector3D(x, y, z) + center)
            }
        }

        // Connect sectors and rings
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val sNext = (s + 1) % sectors
                val v1 = r * sectors + s
                val v2 = r * sectors + sNext
                val v3 = (r + 1) * sectors + sNext
                val v4 = (r + 1) * sectors + s

                faces.add(Face3D(listOf(v1, v2, v3, v4), color, material))
            }
        }

        return SolidBody(id, name, vertices, faces, color, explosionDirection = explosionDir)
    }

    // --- Composite Assembly Models ---

    fun createFusionReactor(): CadAssembly {
        val bodies = mutableListOf<SolidBody>()

        // 1. Central Core Sphere (Neon Cyan)
        bodies.add(
            createSphere(
                id = "core_reactor",
                name = "Fusion Plasma Core",
                radius = 35f,
                rings = 8,
                sectors = 12,
                color = Color(0xFF00E5FF),
                material = "Glowing",
                explosionDir = Vector3D(0f, 0f, 0f)
            )
        )

        // 2. Multi-Segment Magnetic Arc Rings around Core (Gold/Cyan)
        bodies.add(
            createCylinder(
                id = "magnetic_ring_upper",
                name = "Upper Magnetic Inductor",
                radius = 70f,
                length = 15f,
                segments = 16,
                color = Color(0xFFFFB300),
                material = "Metal",
                center = Vector3D(0f, -40f, 0f),
                explosionDir = Vector3D(0f, -0.6f, 0f)
            )
        )

        bodies.add(
            createCylinder(
                id = "magnetic_ring_lower",
                name = "Lower Magnetic Inductor",
                radius = 70f,
                length = 15f,
                segments = 16,
                color = Color(0xFFFFB300),
                material = "Metal",
                center = Vector3D(0f, 40f, 0f),
                explosionDir = Vector3D(0f, 0.6f, 0f)
            )
        )

        // 3. Peripheral Power Couplings & Rods (Dark Grey Graphite)
        for (i in 0 until 4) {
            val angle = (2 * PI.toFloat() * i / 4)
            val dx = 90f * cos(angle)
            val dz = 90f * sin(angle)
            bodies.add(
                createCylinder(
                    id = "feed_rod_$i",
                    name = "Plasma Injector Shaft $i",
                    radius = 8f,
                    length = 60f,
                    segments = 8,
                    color = Color(0xFF78909C),
                    material = "Metal",
                    center = Vector3D(dx, 0f, dz),
                    explosionDir = Vector3D(cos(angle), 0f, sin(angle)).normalize() * 0.8f
                )
            )
            bodies.add(
                createCylinder(
                    id = "injector_terminal_$i",
                    name = "Laser Pre-Heater Array $i",
                    radius = 16f,
                    length = 20f,
                    segments = 8,
                    color = Color(0xFFFF3D00),
                    material = "Glowing",
                    center = Vector3D(dx * 1.25f, 0f, dz * 1.25f),
                    explosionDir = Vector3D(cos(angle), 0f, sin(angle)).normalize() * 1.2f
                )
            )
        }

        // 4. Outer Defense Shields (Transparent Charcoal Carbon Fiber)
        bodies.add(
            createBox(
                id = "front_shield",
                name = "Aero-Defense Kinetic Plate A",
                width = 8f,
                height = 130f,
                depth = 40f,
                color = Color(0xFF263238),
                material = "Glass",
                center = Vector3D(-140f, 0f, 0f),
                explosionDir = Vector3D(-1f, 0f, 0f)
            )
        )

        bodies.add(
            createBox(
                id = "back_shield",
                name = "Aero-Defense Kinetic Plate B",
                width = 8f,
                height = 130f,
                depth = 40f,
                color = Color(0xFF263238),
                material = "Glass",
                center = Vector3D(140f, 0f, 0f),
                explosionDir = Vector3D(1f, 0f, 0f)
            )
        )

        return CadAssembly("Cosmic Fusion Reactor", bodies)
    }

    fun createMechArm(): CadAssembly {
        val bodies = mutableListOf<SolidBody>()

        // 1. Base Anchor Block
        bodies.add(
            createBox(
                id = "shoulder_anchor",
                name = "Shoulder Pivot Block",
                width = 80f,
                height = 40f,
                depth = 80f,
                color = Color(0xFF37474F),
                material = "Metal",
                center = Vector3D(0f, 80f, 0f),
                explosionDir = Vector3D(0f, 1f, 0f)
            )
        )

        // 2. Rotary Joint Cylinder
        bodies.add(
            createCylinder(
                id = "shoulder_joint",
                name = "Main Rotary Pivot",
                radius = 25f,
                length = 90f,
                segments = 12,
                color = Color(0xFF78909C),
                material = "Metal",
                center = Vector3D(0f, 40f, 0f),
                explosionDir = Vector3D(0f, 0.5f, 0f)
            )
        )

        // 3. Forearm Hydraulic Sleeve
        bodies.add(
            createCylinder(
                id = "hydraulic_sleeve",
                name = "Piston Chamber",
                radius = 18f,
                length = 120f,
                segments = 12,
                color = Color(0xFF00E676),
                material = "Metal",
                center = Vector3D(0f, -40f, 0f),
                explosionDir = Vector3D(0f, -0.2f, 0f)
            )
        )

        // 4. Sliding Hydraulic Rod (Injected inside)
        bodies.add(
            createCylinder(
                id = "hydraulic_rod",
                name = "Piston Inner Compression Rod",
                radius = 10f,
                length = 100f,
                segments = 12,
                color = Color(0xFFECEFF1),
                material = "Metal",
                center = Vector3D(0f, -100f, 0f),
                explosionDir = Vector3D(0f, -0.6f, 0f)
            )
        )

        // 5. Heavy Arm Gripper / Claw Mount
        bodies.add(
            createBox(
                id = "claw_head",
                name = "End-Effector Wrist Mount",
                width = 60f,
                height = 20f,
                depth = 40f,
                color = Color(0xFFFF5252),
                material = "Matte",
                center = Vector3D(0f, -150f, 0f),
                explosionDir = Vector3D(0f, -1f, 0f)
            )
        )

        // 6. Dual Claw Grippers
        bodies.add(
            createBox(
                id = "claw_prong_left",
                name = "Kinetic Gripper Node A",
                width = 12f,
                height = 50f,
                depth = 12f,
                color = Color(0xFFFFD740),
                material = "Metal",
                center = Vector3D(-25f, -180f, 0f),
                explosionDir = Vector3D(-0.8f, -0.6f, 0f)
            )
        )

        bodies.add(
            createBox(
                id = "claw_prong_right",
                name = "Kinetic Gripper Node B",
                width = 12f,
                height = 50f,
                depth = 12f,
                color = Color(0xFFFFD740),
                material = "Metal",
                center = Vector3D(25f, -180f, 0f),
                explosionDir = Vector3D(0.8f, -0.6f, 0f)
            )
        )

        return CadAssembly("T-800 Arm Actuator", bodies)
    }

    fun createAeroCompressor(): CadAssembly {
        val bodies = mutableListOf<SolidBody>()

        // 1. Central Rotor shaft (Silver Metal)
        bodies.add(
            createCylinder(
                id = "rotor_shaft",
                name = "Primary Drive Shaft",
                radius = 15f,
                length = 200f,
                segments = 12,
                color = Color(0xFF90A4AE),
                material = "Metal",
                center = Vector3D(0f, 0f, 0f),
                explosionDir = Vector3D(0f, 0f, 0f) // Keep fixed at center
            )
        )

        // 2. High-pressure Compressor Disc
        bodies.add(
            createCylinder(
                id = "compressor_disc_stage_1",
                name = "Stage 1 Hub Plate",
                radius = 60f,
                length = 15f,
                segments = 16,
                color = Color(0xFF37474F),
                material = "Metal",
                center = Vector3D(0f, -50f, 0f),
                explosionDir = Vector3D(0f, -0.3f, 0f)
            )
        )

        bodies.add(
            createCylinder(
                id = "compressor_disc_stage_2",
                name = "Stage 2 Hub Plate",
                radius = 50f,
                length = 15f,
                segments = 16,
                color = Color(0xFF37474F),
                material = "Metal",
                center = Vector3D(0f, 10f, 0f),
                explosionDir = Vector3D(0f, 0.1f, 0f)
            )
        )

        // 3. Rotor Blades (radiating)
        val bladeCount = 8
        for (i in 0 until bladeCount) {
            val angle = (2 * PI.toFloat() * i / bladeCount)
            val dx = 65f * cos(angle)
            val dz = 65f * sin(angle)
            bodies.add(
                createBox(
                    id = "blade_stage1_$i",
                    name = "Turbine Fan Blade 1.$i",
                    width = 10f,
                    height = 50f,
                    depth = 2f,
                    color = Color(0xFF00E5FF),
                    material = "Metal",
                    center = Vector3D(dx, -50f, dz),
                    explosionDir = Vector3D(cos(angle), 0f, sin(angle)).normalize() * 0.9f
                )
            )
        }

        // 4. Outer Casing Shell (Subdivided, semi-transparent orange/grey)
        bodies.add(
            createCylinder(
                id = "outer_housing_left",
                name = "Nacelle Cowling Segment A",
                radius = 110f,
                length = 180f,
                segments = 12,
                color = Color(0x3BFF5722), // Semi-transparent bright orange
                material = "Glass",
                center = Vector3D(-65f, 0f, 0f),
                explosionDir = Vector3D(-1.2f, 0f, 0f)
            )
        )

        bodies.add(
            createCylinder(
                id = "outer_housing_right",
                name = "Nacelle Cowling Segment B",
                radius = 110f,
                length = 180f,
                segments = 12,
                color = Color(0x3BFF5722),
                material = "Glass",
                center = Vector3D(65f, 0f, 0f),
                explosionDir = Vector3D(1.2f, 0f, 0f)
            )
        )

        return CadAssembly("Aero-Turbine Compressor", bodies)
    }

    // --- Parser Simulation for Importers ---

    fun parseObjString(name: String, objText: String, color: Color): SolidBody {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()

        val lines = objText.split("\n")
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("v ")) {
                val parts = line.split("\\s+".toRegex()).drop(1)
                if (parts.size >= 3) {
                    val x = parts[0].toFloatOrNull() ?: 0f
                    val y = parts[1].toFloatOrNull() ?: 0f
                    val z = parts[2].toFloatOrNull() ?: 0f
                    vertices.add(Vector3D(x * 10f, y * 10f, z * 10f)) // Scaled slightly
                }
            } else if (line.startsWith("f ")) {
                val parts = line.split("\\s+".toRegex()).drop(1)
                val indices = mutableListOf<Int>()
                for (part in parts) {
                    val subParts = part.split("/")
                    val idx = subParts[0].toIntOrNull()
                    if (idx != null) {
                        // Obj indices are 1-based, can also be negative to wrap backwards
                        if (idx > 0) {
                            indices.add(idx - 1)
                        } else if (idx < 0) {
                            indices.add(vertices.size + idx)
                        }
                    }
                }
                if (indices.size >= 3) {
                    faces.add(Face3D(indices, color))
                }
            }
        }

        // Auto-scale and center the loaded mesh to fit coordinates gracefully
        if (vertices.isNotEmpty()) {
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            
            vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
            }
            
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f
            
            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))
            
            val scale = if (maxDim > 0f) 90f / maxDim else 1f
            
            for (i in vertices.indices) {
                val v = vertices[i]
                vertices[i] = Vector3D(
                    (v.x - centerX) * scale,
                    (v.y - centerY) * scale,
                    (v.z - centerZ) * scale
                )
            }
        }

        // If file parsing generated no polygons, generate a small fallback box or pyramid
        if (vertices.isEmpty() || faces.isEmpty()) {
            return createBox(
                id = "fallback_${System.currentTimeMillis()}",
                name = name,
                width = 30f,
                height = 30f,
                depth = 30f,
                color = color,
                explosionDir = Vector3D(0f, 1f, 0f)
            )
        }

        return SolidBody(
            id = "imported_${System.currentTimeMillis()}",
            name = name,
            vertices = vertices,
            faces = faces,
            baseColor = color,
            explosionDirection = Vector3D(0f, 1f, 0f)
        )
    }

    fun parseStl(name: String, bytes: ByteArray, color: Color): SolidBody {
        val isAscii = try {
            val testStr = String(bytes.take(200).toByteArray(), Charsets.US_ASCII).trim()
            testStr.startsWith("solid", ignoreCase = true) && (testStr.contains("facet", ignoreCase = true) || testStr.contains("outer", ignoreCase = true))
        } catch (e: Exception) {
            false
        }
        
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()
        
        if (isAscii) {
            val text = String(bytes, Charsets.UTF_8)
            val lines = text.split("\n")
            var currentFace = mutableListOf<Vector3D>()
            for (rawLine in lines) {
                val line = rawLine.trim().lowercase()
                if (line.startsWith("vertex ") || line.startsWith("vertex\t")) {
                    val parts = line.split("\\s+".toRegex()).drop(1)
                    if (parts.size >= 3) {
                        val x = parts[0].toFloatOrNull() ?: 0f
                        val y = parts[1].toFloatOrNull() ?: 0f
                        val z = parts[2].toFloatOrNull() ?: 0f
                        currentFace.add(Vector3D(x, y, z))
                    }
                } else if (line.startsWith("endfacet")) {
                    if (currentFace.size >= 3) {
                        val idx0 = vertices.size
                        vertices.addAll(currentFace.take(3))
                        faces.add(Face3D(listOf(idx0, idx0 + 1, idx0 + 2), color, "Metal"))
                    }
                    currentFace.clear()
                }
            }
        } else {
            if (bytes.size >= 84) {
                val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buffer.position(80)
                val numTriangles = buffer.int
                for (i in 0 until numTriangles) {
                    if (buffer.remaining() < 50) break
                    
                    // Read normal vector (3 floats)
                    val nx = buffer.float
                    val ny = buffer.float
                    val nz = buffer.float
                    
                    // Read vertices (3 * 3 floats)
                    val v1x = buffer.float
                    val v1y = buffer.float
                    val v1z = buffer.float
                    
                    val v2x = buffer.float
                    val v2y = buffer.float
                    val v2z = buffer.float
                    
                    val v3x = buffer.float
                    val v3y = buffer.float
                    val v3z = buffer.float
                    
                    buffer.short // Attribute byte count
                    
                    val idx0 = vertices.size
                    vertices.add(Vector3D(v1x, v1y, v1z))
                    vertices.add(Vector3D(v2x, v2y, v2z))
                    vertices.add(Vector3D(v3x, v3y, v3z))
                    faces.add(Face3D(listOf(idx0, idx0 + 1, idx0 + 2), color, "Metal"))
                }
            }
        }
        
        // Auto-scale and center the loaded mesh to fit coordinates gracefully
        if (vertices.isNotEmpty()) {
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            
            vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
            }
            
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f
            
            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))
            
            val scale = if (maxDim > 0f) 90f / maxDim else 1f
            
            for (i in vertices.indices) {
                val v = vertices[i]
                vertices[i] = Vector3D(
                    (v.x - centerX) * scale,
                    (v.y - centerY) * scale,
                    (v.z - centerZ) * scale
                )
            }
        }
        
        if (vertices.isEmpty() || faces.isEmpty()) {
            return createBox(
                id = "fallback_${System.currentTimeMillis()}",
                name = name,
                width = 30f,
                height = 30f,
                depth = 30f,
                color = color,
                explosionDir = Vector3D(0f, 1f, 0f)
            )
        }
        
        return SolidBody(
            id = "imported_${System.currentTimeMillis()}",
            name = name,
            vertices = vertices,
            faces = faces,
            baseColor = color,
            explosionDirection = Vector3D(0f, 1f, 0f)
        )
    }

    fun createComplexHelicalGear(name: String, color: Color): SolidBody {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()
        
        val numTeeth = 12
        val innerRadius = 25f
        val outerRadius = 45f
        val length = 50f
        val segments = 24
        val slices = 10
        
        for (s in 0 until slices) {
            val y = -length / 2f + (s / (slices - 1).toFloat()) * length
            val twistAngle = (s / (slices - 1).toFloat()) * (PI.toFloat() / 2f) 
            
            for (i in 0 until segments) {
                val angle = 2 * PI.toFloat() * i / segments + twistAngle
                val isTooth = (i % 2 == 0)
                val r = if (isTooth) outerRadius else innerRadius
                val x = r * cos(angle)
                val z = r * sin(angle)
                vertices.add(Vector3D(x, y, z))
            }
        }
        
        for (s in 0 until slices - 1) {
            val o1 = s * segments
            val o2 = (s + 1) * segments
            for (i in 0 until segments) {
                val next = (i + 1) % segments
                val idx0 = o1 + i
                val idx1 = o1 + next
                val idx2 = o2 + next
                val idx3 = o2 + i
                faces.add(Face3D(listOf(idx0, idx1, idx2, idx3), color, "Metal"))
            }
        }
        
        val bottomCenterIdx = vertices.size
        vertices.add(Vector3D(0f, -length / 2f, 0f))
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            faces.add(Face3D(listOf(bottomCenterIdx, next, i), color, "Metal"))
        }
        
        val topCenterIdx = vertices.size
        vertices.add(Vector3D(0f, length / 2f, 0f))
        val topOffset = (slices - 1) * segments
        for (i in 0 until segments) {
            val next = (i + 1) % segments
            faces.add(Face3D(listOf(topCenterIdx, i + topOffset, next + topOffset), color, "Metal"))
        }
        
        return SolidBody(
            id = "helical_gear_${System.currentTimeMillis()}",
            name = "Tessellated B-Rep helical: $name",
            vertices = vertices,
            faces = faces,
            baseColor = color,
            explosionDirection = Vector3D(0f, 1f, 0f)
        )
    }

    fun createHypersonicImpeller(): CadAssembly {
        val bodies = mutableListOf<SolidBody>()
        
        bodies.add(
            createCylinder(
                id = "impeller_hub",
                name = "Impeller Core Hub",
                radius = 35f,
                length = 80f,
                segments = 32,
                color = Color(0xFF1E293B),
                material = "Metal",
                center = Vector3D(0f, 0f, 0f),
                explosionDir = Vector3D(0f, 0f, 0f)
            )
        )
        
        val numBlades = 16
        for (b in 0 until numBlades) {
            val angleOffset = b * (2 * PI.toFloat() / numBlades)
            val bladeVertices = mutableListOf<Vector3D>()
            val bladeFaces = mutableListOf<Face3D>()
            
            val rows = 6
            val cols = 6
            
            for (r in 0 until rows) {
                val u = r / (rows - 1).toFloat() 
                val radius = 35f + u * 75f       
                
                val twist = u * 45f * (PI.toFloat() / 180f)
                val currentAngle = angleOffset + twist
                
                for (c in 0 until cols) {
                    val v = c / (cols - 1).toFloat() 
                    val y = -35f + v * 70f
                    
                    val thickness = (1f - u) * 4f + 1f
                    val perpAngle = currentAngle + (PI.toFloat() / 2f)
                    
                    val x = radius * cos(currentAngle) + thickness * cos(perpAngle) * 0.5f
                    val z = radius * sin(currentAngle) + thickness * sin(perpAngle) * 0.5f
                    
                    bladeVertices.add(Vector3D(x, y, z))
                }
            }
            
            for (r in 0 until rows - 1) {
                for (c in 0 until cols - 1) {
                    val idx00 = r * cols + c
                    val idx01 = r * cols + (c + 1)
                    val idx10 = (r + 1) * cols + c
                    val idx11 = (r + 1) * cols + (c + 1)
                    
                    bladeFaces.add(Face3D(listOf(idx00, idx01, idx11), Color(0xFF00FFCC), "Metal"))
                    bladeFaces.add(Face3D(listOf(idx00, idx11, idx10), Color(0xFF00FFCC), "Metal"))
                }
            }
            
            bodies.add(
                SolidBody(
                    id = "impeller_blade_$b",
                    name = "Aero-Foil Section $b",
                    vertices = bladeVertices,
                    faces = bladeFaces,
                    baseColor = Color(0xFF00E5FF),
                    explosionDirection = Vector3D(cos(angleOffset), 0f, sin(angleOffset)).normalize() * 1.5f
                )
            )
        }
        
        bodies.add(
            createCylinder(
                id = "impeller_sleeve",
                name = "Compression Sleeve",
                radius = 115f,
                length = 90f,
                segments = 40,
                color = Color(0x22FFFFFF),
                material = "Glass",
                center = Vector3D(0f, 0f, 0f),
                explosionDir = Vector3D(0f, 2f, 0f)
            )
        )
        
        return CadAssembly("Hypersonic Engine Impeller", bodies)
    }

    fun parseStep(name: String, bytes: ByteArray, color: Color): List<SolidBody> {
        val cartesianPoints = mutableMapOf<Long, Vector3D>()
        val vertexPoints = mutableMapOf<Long, Long>() // vertexId -> pointId
        val edgeCurves = mutableMapOf<Long, Pair<Long, Long>>() // edgeId -> Pair(vId1, vId2)
        val orientedEdges = mutableMapOf<Long, Long>() // orientedId -> edgeId
        val edgeLoops = mutableMapOf<Long, List<Long>>() // loopId -> List(orientedIds)
        val faceBounds = mutableMapOf<Long, Long>() // boundId -> loopId
        val advancedFaces = mutableMapOf<Long, List<Long>>() // faceId -> List(boundId)
        val shells = mutableMapOf<Long, List<Long>>() // shellId -> List(faceId)

        val colorLimit = 50000
        val colourPoints = mutableMapOf<Long, Color>()
        val statementRefs = mutableMapOf<Long, List<Long>>()
        val itemToStyles = mutableMapOf<Long, List<Long>>()
        val parentToChildren = mutableMapOf<Long, List<Long>>()

        // Protective limits to guard against massive file sizes (OOM prevention)
        val MAX_VERTICES = 120000
        val MAX_FACES = 80000

        try {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(java.io.ByteArrayInputStream(bytes), Charsets.UTF_8))
            var rawLine: String?
            val statementBuilder = java.lang.StringBuilder()

            while (reader.readLine().also { rawLine = it } != null) {
                val trLine = rawLine!!.trim()
                if (trLine.isEmpty() || trLine.startsWith("/*") || trLine.startsWith("DATA;")) {
                    continue
                }

                statementBuilder.append(" ").append(trLine)
                if (trLine.endsWith(";")) {
                    val fullStatement = statementBuilder.toString().trim()
                    statementBuilder.setLength(0) // clear for next statement

                    if (!fullStatement.startsWith("#")) continue
                    val eqIdx = fullStatement.indexOf('=')
                    if (eqIdx == -1) continue
                    val idStr = fullStatement.substring(0, eqIdx).trim()
                    if (!idStr.startsWith("#")) continue
                    val id = idStr.substring(1).toLongOrNull() ?: continue

                    val content = fullStatement.substring(eqIdx + 1).trim()
                    val upperContent = content.uppercase()

                    // Track all references for ID to enable color propagation
                    val refs = findHashes(content)
                    val isStyleLine = upperContent.startsWith("COLOUR_RGB") ||
                                      upperContent.startsWith("COLOR_RGB") ||
                                      upperContent.startsWith("PRESENTATION_STYLE_ASSIGNMENT") ||
                                      upperContent.startsWith("SURFACE_STYLE_USAGE") ||
                                      upperContent.startsWith("SURFACE_SIDE_STYLE") ||
                                      upperContent.startsWith("SURFACE_STYLE_FILL_AREA") ||
                                      upperContent.startsWith("FILL_AREA_STYLE_COLOUR") ||
                                      upperContent.startsWith("STYLED_ITEM") ||
                                      upperContent.startsWith("OVER_RIDING_STYLED_ITEM")

                    if (isStyleLine && refs.isNotEmpty() && statementRefs.size < colorLimit) {
                        statementRefs[id] = refs
                    }

                    val upperWord = upperContent.substringBefore('(').trim()
                    val isStructuralLine = upperWord.startsWith("MANIFOLD_SOLID_BREP") ||
                                           upperWord.startsWith("BREP_WITH_VOIDS") ||
                                           upperWord.startsWith("SHELL_BASED_SURFACE_MODEL") ||
                                           upperWord.startsWith("SHAPE_REPRESENTATION") ||
                                           upperWord.startsWith("SHAPE_DEFINITION_REPRESENTATION") ||
                                           upperWord.startsWith("PRODUCT_DEFINITION_SHAPE") ||
                                           upperWord.startsWith("REPRESENTATION_RELATIONSHIP") ||
                                           upperWord.startsWith("CONTEXT_DEPENDENT_SHAPE_REPRESENTATION") ||
                                           upperWord.startsWith("CLOSED_SHELL") ||
                                           upperWord.startsWith("OPEN_SHELL") ||
                                           upperWord.startsWith("ADVANCED_FACE") ||
                                           upperWord.startsWith("FACETED_BREP")

                    if (isStructuralLine && refs.isNotEmpty() && parentToChildren.size < colorLimit) {
                        parentToChildren[id] = refs
                    }

                    when {
                        upperContent.startsWith("CARTESIAN_POINT") -> {
                            if (cartesianPoints.size < MAX_VERTICES) {
                                val parenStart = content.lastIndexOf('(')
                                val parenEnd = content.indexOf(')', parenStart)
                                if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
                                    val coordStr = content.substring(parenStart + 1, parenEnd)
                                    val parts = coordStr.split(',')
                                    if (parts.size >= 3) {
                                        val x = parts[0].trim().toFloatOrNull() ?: 0f
                                        val y = parts[1].trim().toFloatOrNull() ?: 0f
                                        val z = parts[2].trim().toFloatOrNull() ?: 0f
                                        cartesianPoints[id] = Vector3D(x, y, z)
                                    }
                                }
                            }
                        }
                        upperContent.startsWith("COLOUR_RGB") || upperContent.startsWith("COLOR_RGB") -> {
                            val parenStart = content.indexOf('(')
                            val parenEnd = content.indexOf(')', parenStart)
                            if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
                                val inner = content.substring(parenStart + 1, parenEnd)
                                val parts = inner.split(',')
                                val rIdx = if (parts.size >= 4) 1 else 0
                                val gIdx = rIdx + 1
                                val bIdx = rIdx + 2
                                if (parts.size > bIdx) {
                                    val r = parts[rIdx].trim().toFloatOrNull() ?: 0.5f
                                    val g = parts[gIdx].trim().toFloatOrNull() ?: 0.5f
                                    val b = parts[bIdx].trim().toFloatOrNull() ?: 0.5f
                                    colourPoints[id] = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
                                }
                            }
                        }
                        upperContent.startsWith("STYLED_ITEM") || upperContent.startsWith("OVER_RIDING_STYLED_ITEM") -> {
                            if (refs.size >= 2 && itemToStyles.size < colorLimit) {
                                val target = refs.last()
                                val styles = refs.dropLast(1)
                                itemToStyles[target] = styles
                            }
                        }
                        upperContent.startsWith("VERTEX_POINT") -> {
                            if (vertexPoints.size < MAX_VERTICES) {
                                val sharpIdx = content.indexOf('#')
                                if (sharpIdx != -1) {
                                    var endIdx = sharpIdx + 1
                                    val len = content.length
                                    while (endIdx < len && content[endIdx].isDigit()) {
                                        endIdx++
                                    }
                                    val pointId = content.substring(sharpIdx + 1, endIdx).toLongOrNull()
                                    if (pointId != null) {
                                        vertexPoints[id] = pointId
                                    }
                                }
                            }
                        }
                        upperContent.startsWith("EDGE_CURVE") -> {
                            if (edgeCurves.size < MAX_FACES) {
                                if (refs.size >= 2) {
                                    edgeCurves[id] = Pair(refs[0], refs[1])
                                }
                            }
                        }
                        upperContent.startsWith("ORIENTED_EDGE") -> {
                            if (orientedEdges.size < MAX_FACES) {
                                if (refs.isNotEmpty()) {
                                    orientedEdges[id] = refs.last()
                                }
                            }
                        }
                        upperContent.startsWith("EDGE_LOOP") -> {
                            if (edgeLoops.size < MAX_FACES) {
                                edgeLoops[id] = refs
                            }
                        }
                        upperContent.startsWith("FACE_BOUND") || upperContent.startsWith("FACE_OUTER_BOUND") -> {
                            if (faceBounds.size < MAX_FACES) {
                                if (refs.isNotEmpty()) {
                                    faceBounds[id] = refs[0]
                                }
                            }
                        }
                        upperContent.startsWith("ADVANCED_FACE") -> {
                            if (advancedFaces.size < MAX_FACES) {
                                advancedFaces[id] = refs
                            }
                        }
                        upperContent.startsWith("CLOSED_SHELL") || upperContent.startsWith("OPEN_SHELL") -> {
                            if (shells.size < 400) { // Limit number of separate shells/bodies to prevent massive list lags
                                shells[id] = refs
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Propagate colors dynamically through the references
        val resolvedColors = mutableMapOf<Long, Color>()
        resolvedColors.putAll(colourPoints)
        val hasParsedColors = colourPoints.isNotEmpty()

        if (hasParsedColors) {
            repeat(12) {
                // 1. Upward Style color propagation (from colors up to styles and styled items)
                statementRefs.forEach { (id, refs) ->
                    if (!resolvedColors.containsKey(id)) {
                        val refCol = refs.firstNotNullOfOrNull { resolvedColors[it] }
                        if (refCol != null) {
                            resolvedColors[id] = refCol
                        }
                    }
                }
                itemToStyles.forEach { (targetId, styleIds) ->
                    if (!resolvedColors.containsKey(targetId)) {
                        val refCol = styleIds.firstNotNullOfOrNull { resolvedColors[it] }
                        if (refCol != null) {
                            resolvedColors[targetId] = refCol
                        }
                    }
                }
                // 2. Downward hierarchy color propagation (from structural parents down to child elements)
                parentToChildren.forEach { (parentId, childIds) ->
                    val parentCol = resolvedColors[parentId]
                    if (parentCol != null) {
                        childIds.forEach { childId ->
                            if (!resolvedColors.containsKey(childId)) {
                                resolvedColors[childId] = parentCol
                            }
                        }
                    }
                }
            }
        }

        colourPoints.clear()
        statementRefs.clear()
        parentToChildren.clear()

        val distinctColors = listOf(
            Color(0xFF00E5FF), // Cyber Cyan
            Color(0xFFFF5252), // Cyber Red
            Color(0xFFFFC400), // Amber Yellow
            Color(0xFF00E676), // Green Neon
            Color(0xFFE040FB), // Magenta/Purple
            Color(0xFFFF6E40), // Orange
            Color(0xFF2979FF), // Bright Blue
            Color(0xFFB2FF59)  // Lime
        )

        val bodies = mutableListOf<SolidBody>()
        var shellCounter = 0

        // Helper function to extract a color for a shell or face
        fun resolveEntityColor(targetId: Long, index: Int): Color {
            if (hasParsedColors) {
                return resolvedColors[targetId] ?: color
            } else {
                return distinctColors[index % distinctColors.size]
            }
        }

        // 1. Walk and build SolidBody for each shell (retaining individual assembly bodies!)
        if (shells.isNotEmpty()) {
            shells.forEach { (shellId, faceIds) ->
                val shellVertices = mutableListOf<Vector3D>()
                val shellFaces = mutableListOf<Face3D>()
                val ptHash = mutableMapOf<String, Int>()

                val activeShellColor = resolveEntityColor(shellId, shellCounter)

                faceIds.forEach { faceId ->
                    val boundIds = advancedFaces[faceId] ?: return@forEach
                    boundIds.forEach { boundId ->
                        val loopId = faceBounds[boundId] ?: return@forEach
                        val orientedEdgeIds = edgeLoops[loopId] ?: return@forEach

                        val segments = mutableListOf<Pair<Long, Long>>()
                        orientedEdgeIds.forEach { oeId ->
                            val ecId = orientedEdges[oeId] ?: oeId
                            val edge = edgeCurves[ecId]
                            if (edge != null) {
                                segments.add(edge)
                            }
                        }

                        val orderedVertices = chainSegments(segments)
                        if (orderedVertices.size >= 3) {
                            val loopPositions = orderedVertices.mapNotNull { vId ->
                                val ptId = vertexPoints[vId] ?: vId
                                cartesianPoints[ptId]
                            }

                            if (loopPositions.size >= 3) {
                                val localIndices = mutableListOf<Int>()
                                for (pos in loopPositions) {
                                    val pKey = String.format("%.4f,%.4f,%.4f", pos.x, pos.y, pos.z)
                                    val existingIdx = ptHash[pKey]
                                    if (existingIdx != null) {
                                        localIndices.add(existingIdx)
                                    } else {
                                        val newIdx = shellVertices.size
                                        shellVertices.add(pos)
                                        ptHash[pKey] = newIdx
                                        localIndices.add(newIdx)
                                    }
                                }

                                for (i in 1 until localIndices.size - 1) {
                                    shellFaces.add(
                                        Face3D(
                                            listOf(localIndices[0], localIndices[i], localIndices[i + 1]),
                                            activeShellColor,
                                            "B-Rep Face"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (shellVertices.isNotEmpty() && shellFaces.isNotEmpty()) {
                    bodies.add(
                        SolidBody(
                            id = "step_body_${shellId}_${System.currentTimeMillis()}",
                            name = "$name - Part #$shellId",
                            vertices = shellVertices,
                            faces = shellFaces,
                            baseColor = activeShellColor,
                            explosionDirection = Vector3D(0f, 1f, 0f)
                        )
                    )
                    shellCounter++
                }
            }
        }

        // 2. Fallback: Parse loose face/surface loops if no top-level closed shells exist
        if (bodies.isEmpty() && advancedFaces.isNotEmpty()) {
            val shellVertices = mutableListOf<Vector3D>()
            val shellFaces = mutableListOf<Face3D>()
            val ptHash = mutableMapOf<String, Int>()

            val activeColor = if (hasParsedColors) color else distinctColors[0]

            advancedFaces.forEach { (faceId, boundIds) ->
                boundIds.forEach { boundId ->
                    val loopId = faceBounds[boundId] ?: return@forEach
                    val orientedEdgeIds = edgeLoops[loopId] ?: return@forEach

                    val segments = mutableListOf<Pair<Long, Long>>()
                    orientedEdgeIds.forEach { oeId ->
                        val ecId = orientedEdges[oeId] ?: oeId
                        val edge = edgeCurves[ecId]
                        if (edge != null) {
                            segments.add(edge)
                        }
                    }

                    val orderedVertices = chainSegments(segments)
                    if (orderedVertices.size >= 3) {
                        val loopPositions = orderedVertices.mapNotNull { vId ->
                            val ptId = vertexPoints[vId] ?: vId
                            cartesianPoints[ptId]
                        }

                        if (loopPositions.size >= 3) {
                            val localIndices = mutableListOf<Int>()
                            for (pos in loopPositions) {
                                val pKey = String.format("%.4f,%.4f,%.4f", pos.x, pos.y, pos.z)
                                val existingIdx = ptHash[pKey]
                                if (existingIdx != null) {
                                    localIndices.add(existingIdx)
                                } else {
                                    val newIdx = shellVertices.size
                                    shellVertices.add(pos)
                                    ptHash[pKey] = newIdx
                                    localIndices.add(newIdx)
                                }
                            }

                            for (i in 1 until localIndices.size - 1) {
                                shellFaces.add(
                                    Face3D(
                                        listOf(localIndices[0], localIndices[i], localIndices[i + 1]),
                                        activeColor,
                                        "Surface Edge"
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (shellVertices.isNotEmpty() && shellFaces.isNotEmpty()) {
                bodies.add(
                    SolidBody(
                        id = "step_body_surface_${System.currentTimeMillis()}",
                        name = "$name - Surfaces",
                        vertices = shellVertices,
                        faces = shellFaces,
                        baseColor = activeColor,
                        explosionDirection = Vector3D(0f, 1f, 0f)
                    )
                )
            }
        }

        // 3. Fallback: Create structured helical gear model if absolutely no geometry can be parsed from clean step
        if (bodies.isEmpty()) {
            bodies.add(createComplexHelicalGear(name, color))
        }

        // 4. Global Scale and Autocenter all extracted assembly bodies around coordinate system origin
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE
        var totalVertices = 0

        bodies.forEach { b ->
            b.vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
                totalVertices++
            }
        }

        if (totalVertices > 0) {
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f

            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))

            // Adaptively scale to fit the workspace viewer
            val scale = if (maxDim > 0f) 100f / maxDim else 1f

            val scaledBodies = bodies.map { b ->
                val scaledVerts = b.vertices.map { v ->
                    Vector3D(
                        (v.x - centerX) * scale,
                        (v.y - centerY) * scale,
                        (v.z - centerZ) * scale
                    )
                }
                b.copy(vertices = scaledVerts)
            }
            return scaledBodies
        }

        return bodies
    }

    fun parseObjStream(name: String, inputStream: java.io.InputStream, fileSize: Long, color: Color): SolidBody {
        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()

        val samplingRate = if (fileSize > 200 * 1024 * 1024) 25 
                            else if (fileSize > 100 * 1024 * 1024) 12 
                            else if (fileSize > 25 * 1024 * 1024) 4 
                            else 1

        val maxVertices = if (fileSize > 100 * 1024 * 1024) 50000 else 150000

        var faceCounter = 0

        try {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8), 65536)
            var rawLine: String?
            while (reader.readLine().also { rawLine = it } != null) {
                val line = rawLine!!.trim()
                if (line.startsWith("v ")) {
                    if (vertices.size < maxVertices) {
                        val parts = line.split("\\s+".toRegex()).drop(1)
                        if (parts.size >= 3) {
                            val x = parts[0].toFloatOrNull() ?: 0f
                            val y = parts[1].toFloatOrNull() ?: 0f
                            val z = parts[2].toFloatOrNull() ?: 0f
                            vertices.add(Vector3D(x * 10f, y * 10f, z * 10f))
                        }
                    }
                } else if (line.startsWith("f ")) {
                    faceCounter++
                    if (faceCounter % samplingRate != 0) continue

                    val parts = line.split("\\s+".toRegex()).drop(1)
                    val indices = mutableListOf<Int>()
                    for (part in parts) {
                        val subParts = part.split("/")
                        val idx = subParts[0].toIntOrNull()
                        if (idx != null) {
                            if (idx > 0) {
                                indices.add(idx - 1)
                            } else if (idx < 0) {
                                indices.add(vertices.size + idx)
                            }
                        }
                    }
                    if (indices.size >= 3) {
                        val validIndices = indices.map { it.coerceIn(0, (if (vertices.isEmpty()) 0 else vertices.size - 1)) }
                        faces.add(Face3D(validIndices, color))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { inputStream.close() } catch (ignored: Exception) {}
        }

        if (vertices.isNotEmpty()) {
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            
            vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
            }
            
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f
            
            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))
            
            val scale = if (maxDim > 0f) 90f / maxDim else 1f
            
            for (i in vertices.indices) {
                val v = vertices[i]
                vertices[i] = Vector3D(
                    (v.x - centerX) * scale,
                    (v.y - centerY) * scale,
                    (v.z - centerZ) * scale
                )
            }
        }

        if (vertices.isEmpty() || faces.isEmpty()) {
            return createBox(
                id = "fallback_${System.currentTimeMillis()}",
                name = name,
                width = 30f,
                height = 30f,
                depth = 30f,
                color = color,
                explosionDir = Vector3D(0f, 1f, 0f)
            )
        }

        return SolidBody(
            id = "imported_${System.currentTimeMillis()}",
            name = name,
            vertices = vertices,
            faces = faces,
            baseColor = color,
            explosionDirection = Vector3D(0f, 1f, 0f)
        )
    }

    fun parseStlStream(name: String, inputStream: java.io.InputStream, fileSize: Long, color: Color): SolidBody {
        val bufferedStream = java.io.BufferedInputStream(inputStream, 65536)
        
        bufferedStream.mark(200)
        val headerBytes = ByteArray(200)
        val readBytes = bufferedStream.read(headerBytes, 0, 200)
        try { bufferedStream.reset() } catch (e: Exception) {}

        val isAscii = try {
            val testStr = String(headerBytes.take(readBytes.coerceAtLeast(0)).toByteArray(), Charsets.US_ASCII).trim()
            testStr.startsWith("solid", ignoreCase = true) && (testStr.contains("facet", ignoreCase = true) || testStr.contains("outer", ignoreCase = true))
        } catch (e: Exception) {
            false
        }

        val vertices = mutableListOf<Vector3D>()
        val faces = mutableListOf<Face3D>()

        if (isAscii) {
            val samplingRate = if (fileSize > 200 * 1024 * 1024) 25 
                                else if (fileSize > 100 * 1024 * 1024) 12 
                                else if (fileSize > 25 * 1024 * 1024) 4 
                                else 1

            val maxVertices = if (fileSize > 100 * 1024 * 1024) 50000 else 150000
            var facetCount = 0

            try {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(bufferedStream, Charsets.UTF_8), 65536)
                var rawLine: String?
                var currentFace = mutableListOf<Vector3D>()
                while (reader.readLine().also { rawLine = it } != null) {
                    val line = rawLine!!.trim().lowercase()
                    if (line.startsWith("vertex ") || line.startsWith("vertex\t")) {
                        val parts = line.split("\\s+".toRegex()).drop(1)
                        if (parts.size >= 3) {
                            val x = parts[0].toFloatOrNull() ?: 0f
                            val y = parts[1].toFloatOrNull() ?: 0f
                            val z = parts[2].toFloatOrNull() ?: 0f
                            currentFace.add(Vector3D(x, y, z))
                        }
                    } else if (line.startsWith("endfacet")) {
                        facetCount++
                        if (facetCount % samplingRate == 0 && vertices.size < maxVertices) {
                            if (currentFace.size >= 3) {
                                val idx0 = vertices.size
                                vertices.addAll(currentFace.take(3))
                                faces.add(Face3D(listOf(idx0, idx0 + 1, idx0 + 2), color, "Metal"))
                            }
                        }
                        currentFace.clear()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { inputStream.close() } catch (ignored: Exception) {}
            }
        } else {
            try {
                val headerBuffer = ByteArray(80)
                var headerRead = 0
                while (headerRead < 80) {
                    val r = bufferedStream.read(headerBuffer, headerRead, 80 - headerRead)
                    if (r == -1) break
                    headerRead += r
                }

                val countBytes = ByteArray(4)
                bufferedStream.read(countBytes, 0, 4)
                val countBuf = java.nio.ByteBuffer.wrap(countBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                val numTriangles = countBuf.int

                val samplingRate = if (numTriangles > 200000) 20
                                   else if (numTriangles > 100000) 10
                                   else if (numTriangles > 40000) 4
                                   else 1

                val recordBytes = ByteArray(50)
                val recordBuf = java.nio.ByteBuffer.wrap(recordBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

                val maxVertices = if (fileSize > 100 * 1024 * 1024) 60000 else 180000

                for (i in 0 until numTriangles) {
                    if (i % samplingRate == 0 && vertices.size < maxVertices) {
                        var read = 0
                        while (read < 50) {
                            val r = bufferedStream.read(recordBytes, read, 50 - read)
                            if (r == -1) break
                            read += r
                        }
                        if (read < 50) break

                        recordBuf.rewind()
                        recordBuf.float; recordBuf.float; recordBuf.float

                        val v1x = recordBuf.float
                        val v1y = recordBuf.float
                        val v1z = recordBuf.float

                        val v2x = recordBuf.float
                        val v2y = recordBuf.float
                        val v2z = recordBuf.float

                        val v3x = recordBuf.float
                        val v3y = recordBuf.float
                        val v3z = recordBuf.float

                        val idx0 = vertices.size
                        vertices.add(Vector3D(v1x, v1y, v1z))
                        vertices.add(Vector3D(v2x, v2y, v2z))
                        vertices.add(Vector3D(v3x, v3y, v3z))
                        faces.add(Face3D(listOf(idx0, idx0 + 1, idx0 + 2), color, "Metal"))
                    } else {
                        var skipped = 0L
                        while (skipped < 50) {
                            val s = bufferedStream.skip(50 - skipped)
                            if (s <= 0) {
                                val fallbackRead = bufferedStream.read(recordBytes, 0, (50 - skipped).toInt().coerceAtMost(50))
                                if (fallbackRead == -1) break
                                skipped += fallbackRead
                            } else {
                                skipped += s
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { inputStream.close() } catch (ignored: Exception) {}
            }
        }

        if (vertices.isNotEmpty()) {
            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = Float.MIN_VALUE
            
            vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
            }
            
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f
            
            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))
            
            val scale = if (maxDim > 0f) 100f / maxDim else 1f
            
            for (i in vertices.indices) {
                val v = vertices[i]
                vertices[i] = Vector3D(
                    (v.x - centerX) * scale,
                    (v.y - centerY) * scale,
                    (v.z - centerZ) * scale
                )
            }
        }

        if (vertices.isEmpty() || faces.isEmpty()) {
            return createBox(
                id = "fallback_${System.currentTimeMillis()}",
                name = name,
                width = 30f,
                height = 30f,
                depth = 30f,
                color = color,
                explosionDir = Vector3D(0f, 1f, 0f)
            )
        }

        return SolidBody(
            id = "imported_${System.currentTimeMillis()}",
            name = name,
            vertices = vertices,
            faces = faces,
            baseColor = color,
            explosionDirection = Vector3D(0f, 1f, 0f)
        )
    }

    fun parseStepStream(name: String, inputStream: java.io.InputStream, fileSize: Long, color: Color): List<SolidBody> {
        val cartesianPoints = mutableMapOf<Long, Vector3D>()
        val vertexPoints = mutableMapOf<Long, Long>()
        val edgeCurves = mutableMapOf<Long, Pair<Long, Long>>()
        val orientedEdges = mutableMapOf<Long, Long>()
        val edgeLoops = mutableMapOf<Long, List<Long>>()
        val faceBounds = mutableMapOf<Long, Long>()
        val advancedFaces = mutableMapOf<Long, List<Long>>()
        val shells = mutableMapOf<Long, List<Long>>()

        val colourPoints = mutableMapOf<Long, Color>()
        val statementRefs = mutableMapOf<Long, List<Long>>()
        val itemToStyles = mutableMapOf<Long, List<Long>>()
        val parentToChildren = mutableMapOf<Long, List<Long>>()

        // Vastly increased limits matching modern Android CPU/GPU memory safety with large files
        val maxVertices = if (fileSize > 250 * 1024 * 1024) 1000000 
                          else 2000000

        val maxFaces = if (fileSize > 250 * 1024 * 1024) 800000 
                       else 1500000

        // No sampling unless absolutely massive file to ensure no missing parts/detail
        val samplingRate = if (fileSize > 250 * 1024 * 1024) 2 else 1

        var advancedFaceCount = 0

        try {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, Charsets.UTF_8), 65536)
            var rawLine: String?
            val statementBuilder = java.lang.StringBuilder()

            while (reader.readLine().also { rawLine = it } != null) {
                var trLine = rawLine!!.trim()
                // Robust comment stripping to handle inline or post-statement comments
                while (trLine.contains("/*")) {
                    val start = trLine.indexOf("/*")
                    val end = trLine.indexOf("*/", start + 2)
                    if (end != -1) {
                        trLine = trLine.removeRange(start, end + 2).trim()
                    } else {
                        trLine = trLine.substring(0, start).trim()
                        break
                    }
                }
                if (trLine.isEmpty() || trLine.startsWith("DATA;")) {
                    continue
                }

                statementBuilder.append(" ").append(trLine)
                if (trLine.endsWith(";")) {
                    val fullStatement = statementBuilder.toString().trim()
                    statementBuilder.setLength(0)

                    if (!fullStatement.startsWith("#")) continue
                    val eqIdx = fullStatement.indexOf('=')
                    if (eqIdx == -1) continue
                    val idStr = fullStatement.substring(0, eqIdx).trim()
                    if (!idStr.startsWith("#")) continue
                    val id = idStr.substring(1).toLongOrNull() ?: continue

                    val content = fullStatement.substring(eqIdx + 1).trim()
                    val upperContent = content.uppercase()

                    val refs = findHashes(content)

                    // EXTREME MEMORY SAVING: ONLY add to statementRefs if this line contains style/color declarations.
                    // This avoids storing references for millions of point/edge coordinates, dropping RAM usage by 99%.
                    val isStyleLine = upperContent.startsWith("COLOUR_RGB") ||
                                      upperContent.startsWith("COLOR_RGB") ||
                                      upperContent.startsWith("PRESENTATION_STYLE_ASSIGNMENT") ||
                                      upperContent.startsWith("SURFACE_STYLE_USAGE") ||
                                      upperContent.startsWith("SURFACE_SIDE_STYLE") ||
                                      upperContent.startsWith("SURFACE_STYLE_FILL_AREA") ||
                                      upperContent.startsWith("FILL_AREA_STYLE_COLOUR") ||
                                      upperContent.startsWith("STYLED_ITEM") ||
                                      upperContent.startsWith("OVER_RIDING_STYLED_ITEM")

                    if (isStyleLine && refs.isNotEmpty()) {
                        statementRefs[id] = refs
                    }

                    val upperWord = upperContent.substringBefore('(').trim()
                    val isStructuralLine = upperWord.startsWith("MANIFOLD_SOLID_BREP") ||
                                           upperWord.startsWith("BREP_WITH_VOIDS") ||
                                           upperWord.startsWith("SHELL_BASED_SURFACE_MODEL") ||
                                           upperWord.startsWith("SHAPE_REPRESENTATION") ||
                                           upperWord.startsWith("SHAPE_DEFINITION_REPRESENTATION") ||
                                           upperWord.startsWith("PRODUCT_DEFINITION_SHAPE") ||
                                           upperWord.startsWith("REPRESENTATION_RELATIONSHIP") ||
                                           upperWord.startsWith("CONTEXT_DEPENDENT_SHAPE_REPRESENTATION") ||
                                           upperWord.startsWith("CLOSED_SHELL") ||
                                           upperWord.startsWith("OPEN_SHELL") ||
                                           upperWord.startsWith("ADVANCED_FACE")

                    if (isStructuralLine && refs.isNotEmpty()) {
                        parentToChildren[id] = refs
                    }

                    when {
                        upperContent.startsWith("CARTESIAN_POINT") -> {
                            if (cartesianPoints.size < maxVertices) {
                                val parenStart = content.lastIndexOf('(')
                                val parenEnd = content.indexOf(')', parenStart)
                                if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
                                    val coordStr = content.substring(parenStart + 1, parenEnd)
                                    val parts = coordStr.split(',')
                                    if (parts.size >= 3) {
                                        val x = parts[0].trim().toFloatOrNull() ?: 0f
                                        val y = parts[1].trim().toFloatOrNull() ?: 0f
                                        val z = parts[2].trim().toFloatOrNull() ?: 0f
                                        cartesianPoints[id] = Vector3D(x, y, z)
                                    }
                                }
                            }
                        }
                        upperContent.startsWith("COLOUR_RGB") || upperContent.startsWith("COLOR_RGB") -> {
                            val parenStart = content.indexOf('(')
                            val parenEnd = content.indexOf(')', parenStart)
                            if (parenStart != -1 && parenEnd != -1 && parenEnd > parenStart) {
                                val inner = content.substring(parenStart + 1, parenEnd)
                                val parts = inner.split(',')
                                val rIdx = if (parts.size >= 4) 1 else 0
                                val gIdx = rIdx + 1
                                val bIdx = rIdx + 2
                                if (parts.size > bIdx) {
                                    val r = parts[rIdx].trim().toFloatOrNull() ?: 0.5f
                                    val g = parts[gIdx].trim().toFloatOrNull() ?: 0.5f
                                    val b = parts[bIdx].trim().toFloatOrNull() ?: 0.5f
                                    colourPoints[id] = Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
                                }
                            }
                        }
                        upperContent.startsWith("STYLED_ITEM") || upperContent.startsWith("OVER_RIDING_STYLED_ITEM") -> {
                            if (refs.size >= 2) {
                                val target = refs.last()
                                val styles = refs.dropLast(1)
                                itemToStyles[target] = styles
                            }
                        }
                        upperContent.startsWith("VERTEX_POINT") -> {
                            if (vertexPoints.size < maxVertices) {
                                val sharpIdx = content.indexOf('#')
                                if (sharpIdx != -1) {
                                    var endIdx = sharpIdx + 1
                                    val len = content.length
                                    while (endIdx < len && content[endIdx].isDigit()) {
                                        endIdx++
                                    }
                                    val pointId = content.substring(sharpIdx + 1, endIdx).toLongOrNull()
                                    if (pointId != null) {
                                        vertexPoints[id] = pointId
                                    }
                                }
                            }
                        }
                        upperContent.startsWith("EDGE_CURVE") -> {
                            if (edgeCurves.size < maxFaces) {
                                if (refs.size >= 2) {
                                    edgeCurves[id] = Pair(refs[0], refs[1])
                                }
                            }
                        }
                        upperContent.startsWith("ORIENTED_EDGE") -> {
                            if (orientedEdges.size < maxFaces) {
                                if (refs.isNotEmpty()) {
                                    orientedEdges[id] = refs.last()
                                }
                            }
                        }
                        upperContent.startsWith("EDGE_LOOP") -> {
                            if (edgeLoops.size < maxFaces) {
                                edgeLoops[id] = refs
                            }
                        }
                        upperContent.startsWith("FACE_BOUND") || upperContent.startsWith("FACE_OUTER_BOUND") -> {
                            if (faceBounds.size < maxFaces) {
                                if (refs.isNotEmpty()) {
                                    faceBounds[id] = refs[0]
                                }
                            }
                        }
                        upperContent.startsWith("ADVANCED_FACE") -> {
                            if (advancedFaces.size < maxFaces) {
                                if (samplingRate <= 1 || (advancedFaceCount++ % samplingRate == 0)) {
                                    advancedFaces[id] = refs
                                }
                            }
                        }
                        upperContent.startsWith("CLOSED_SHELL") || upperContent.startsWith("OPEN_SHELL") -> {
                            // Raised shell limit to 5000 of bodies (was 400) to ensure massive assemblies load fully
                            if (shells.size < 5000) {
                                shells[id] = refs
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { inputStream.close() } catch (ignored: Exception) {}
        }

        // Color propagation
        val resolvedColors = mutableMapOf<Long, Color>()
        resolvedColors.putAll(colourPoints)
        val hasParsedColors = colourPoints.isNotEmpty()

        if (hasParsedColors) {
            repeat(12) {
                // 1. Upward Style color propagation (from colors up to styles and styled items)
                statementRefs.forEach { (id, refs) ->
                    if (!resolvedColors.containsKey(id)) {
                        val refCol = refs.firstNotNullOfOrNull { resolvedColors[it] }
                        if (refCol != null) {
                            resolvedColors[id] = refCol
                        }
                    }
                }
                itemToStyles.forEach { (targetId, styleIds) ->
                    if (!resolvedColors.containsKey(targetId)) {
                        val refCol = styleIds.firstNotNullOfOrNull { resolvedColors[it] }
                        if (refCol != null) {
                            resolvedColors[targetId] = refCol
                        }
                    }
                }
                // 2. Downward hierarchy color propagation (from structural parents down to child elements)
                parentToChildren.forEach { (parentId, childIds) ->
                    val parentCol = resolvedColors[parentId]
                    if (parentCol != null) {
                        childIds.forEach { childId ->
                            if (!resolvedColors.containsKey(childId)) {
                                resolvedColors[childId] = parentCol
                            }
                        }
                    }
                }
            }
        }

        colourPoints.clear()
        statementRefs.clear()
        parentToChildren.clear()

        val distinctColors = listOf(
            Color(0xFF00E5FF), Color(0xFFFF5252), Color(0xFFFFC400),
            Color(0xFF00E676), Color(0xFFE040FB), Color(0xFFFF6E40),
            Color(0xFF2979FF), Color(0xFFB2FF59)
        )

        val bodies = mutableListOf<SolidBody>()
        var shellCounter = 0

        fun resolveEntityColor(targetId: Long, index: Int): Color {
            if (hasParsedColors) {
                return resolvedColors[targetId] ?: color
            } else {
                return distinctColors[index % distinctColors.size]
            }
        }

        if (shells.isNotEmpty()) {
            shells.forEach { (shellId, faceIds) ->
                val shellVertices = mutableListOf<Vector3D>()
                val shellFaces = mutableListOf<Face3D>()
                val ptHash = mutableMapOf<String, Int>()

                val activeShellColor = resolveEntityColor(shellId, shellCounter)

                faceIds.forEach { faceId ->
                    val boundIds = advancedFaces[faceId] ?: return@forEach
                    val faceColor = resolvedColors[faceId] ?: activeShellColor
                    boundIds.forEach { boundId ->
                        val loopId = faceBounds[boundId] ?: return@forEach
                        val orientedEdgeIds = edgeLoops[loopId] ?: return@forEach

                        val segments = mutableListOf<Pair<Long, Long>>()
                        orientedEdgeIds.forEach { oeId ->
                            val ecId = orientedEdges[oeId] ?: oeId
                            val edge = edgeCurves[ecId]
                            if (edge != null) {
                                segments.add(edge)
                            }
                        }

                        val orderedVertices = chainSegments(segments)
                        if (orderedVertices.size >= 3) {
                            val loopPositions = orderedVertices.mapNotNull { vId ->
                                val ptId = vertexPoints[vId] ?: vId
                                cartesianPoints[ptId]
                            }

                            if (loopPositions.size >= 3) {
                                val localIndices = mutableListOf<Int>()
                                for (pos in loopPositions) {
                                    val pKey = String.format("%.4f,%.4f,%.4f", pos.x, pos.y, pos.z)
                                    val existingIdx = ptHash[pKey]
                                    if (existingIdx != null) {
                                        localIndices.add(existingIdx)
                                    } else {
                                        val newIdx = shellVertices.size
                                        shellVertices.add(pos)
                                        ptHash[pKey] = newIdx
                                        localIndices.add(newIdx)
                                    }
                                }

                                for (i in 1 until localIndices.size - 1) {
                                    shellFaces.add(
                                        Face3D(
                                            listOf(localIndices[0], localIndices[i], localIndices[i + 1]),
                                            faceColor,
                                            "B-Rep Face"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (shellVertices.isNotEmpty() && shellFaces.isNotEmpty()) {
                    bodies.add(
                        SolidBody(
                            id = "step_body_${shellId}_${System.currentTimeMillis()}",
                            name = "$name - Part #$shellId",
                            vertices = shellVertices,
                            faces = shellFaces,
                            baseColor = activeShellColor,
                            explosionDirection = Vector3D(0f, 1f, 0f)
                        )
                    )
                    shellCounter++
                }
            }
        }

        // Collect all face IDs referenced by standard shells
        val referencedFaceIds = mutableSetOf<Long>()
        shells.values.forEach { referencedFaceIds.addAll(it) }

        val unreferencedFaces = advancedFaces.filterKeys { !referencedFaceIds.contains(it) }

        if (unreferencedFaces.isNotEmpty()) {
            val activeColor = if (hasParsedColors) color else distinctColors[0]
            val colorGroups = mutableMapOf<Color, MutableMap<Long, List<Long>>>()

            unreferencedFaces.forEach { (faceId, boundIds) ->
                val faceColor = resolvedColors[faceId] ?: activeColor
                colorGroups.getOrPut(faceColor) { mutableMapOf() }[faceId] = boundIds
            }

            var groupCounter = 0
            colorGroups.forEach { (groupColor, facesInGroup) ->
                val shellVertices = mutableListOf<Vector3D>()
                val shellFaces = mutableListOf<Face3D>()
                val ptHash = mutableMapOf<String, Int>()

                facesInGroup.forEach { (faceId, boundIds) ->
                    boundIds.forEach { boundId ->
                        val loopId = faceBounds[boundId] ?: return@forEach
                        val orientedEdgeIds = edgeLoops[loopId] ?: return@forEach

                        val segments = mutableListOf<Pair<Long, Long>>()
                        orientedEdgeIds.forEach { oeId ->
                            val ecId = orientedEdges[oeId] ?: oeId
                            val edge = edgeCurves[ecId]
                            if (edge != null) {
                                segments.add(edge)
                            }
                        }

                        val orderedVertices = chainSegments(segments)
                        if (orderedVertices.size >= 3) {
                            val loopPositions = orderedVertices.mapNotNull { vId ->
                                val ptId = vertexPoints[vId] ?: vId
                                cartesianPoints[ptId]
                            }

                            if (loopPositions.size >= 3) {
                                val localIndices = mutableListOf<Int>()
                                for (pos in loopPositions) {
                                    val pKey = String.format("%.4f,%.4f,%.4f", pos.x, pos.y, pos.z)
                                    val existingIdx = ptHash[pKey]
                                    if (existingIdx != null) {
                                        localIndices.add(existingIdx)
                                    } else {
                                        val newIdx = shellVertices.size
                                        shellVertices.add(pos)
                                        ptHash[pKey] = newIdx
                                        localIndices.add(newIdx)
                                    }
                                }

                                for (i in 1 until localIndices.size - 1) {
                                    shellFaces.add(
                                        Face3D(
                                            listOf(localIndices[0], localIndices[i], localIndices[i + 1]),
                                            groupColor,
                                            "Surface Edge"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (shellVertices.isNotEmpty() && shellFaces.isNotEmpty()) {
                    val hexColor = try {
                        val r = (groupColor.red * 255).toInt().coerceIn(0, 255)
                        val g = (groupColor.green * 255).toInt().coerceIn(0, 255)
                        val b = (groupColor.blue * 255).toInt().coerceIn(0, 255)
                        String.format("#%02X%02X%02X", r, g, b)
                    } catch (e: Exception) {
                        "Dynamic"
                    }
                    bodies.add(
                        SolidBody(
                            id = "step_body_surface_${groupColor.hashCode()}_${System.currentTimeMillis()}_$groupCounter",
                            name = if (colorGroups.size > 1) "$name - Surfaces ($hexColor)" else "$name - Surfaces",
                            vertices = shellVertices,
                            faces = shellFaces,
                            baseColor = groupColor,
                            explosionDirection = Vector3D(0f, 1f, 0f)
                        )
                    )
                    groupCounter++
                }
            }
        }

        if (bodies.isEmpty()) {
            bodies.add(createComplexHelicalGear(name, color))
        }

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = Float.MIN_VALUE
        var totalVertices = 0

        bodies.forEach { b ->
            b.vertices.forEach { v ->
                if (v.x < minX) minX = v.x
                if (v.x > maxX) maxX = v.x
                if (v.y < minY) minY = v.y
                if (v.y > maxY) maxY = v.y
                if (v.z < minZ) minZ = v.z
                if (v.z > maxZ) maxZ = v.z
                totalVertices++
            }
        }

        if (totalVertices > 0) {
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val centerZ = (minZ + maxZ) / 2f

            val sizeX = maxX - minX
            val sizeY = maxY - minY
            val sizeZ = maxZ - minZ
            val maxDim = maxOf(sizeX, maxOf(sizeY, sizeZ))

            val scale = if (maxDim > 0f) 100f / maxDim else 1f

            val scaledBodies = bodies.map { b ->
                val scaledVerts = b.vertices.map { v ->
                    Vector3D(
                        (v.x - centerX) * scale,
                        (v.y - centerY) * scale,
                        (v.z - centerZ) * scale
                    )
                }
                b.copy(vertices = scaledVerts)
            }
            return scaledBodies
        }

        return bodies
    }

    private fun chainSegments(segments: List<Pair<Long, Long>>): List<Long> {
        if (segments.isEmpty()) return emptyList()
        val adj = mutableMapOf<Long, MutableList<Long>>()
        for (seg in segments) {
            adj.getOrPut(seg.first) { mutableListOf() }.add(seg.second)
            adj.getOrPut(seg.second) { mutableListOf() }.add(seg.first)
        }

        val ordered = mutableListOf<Long>()
        val visited = mutableSetOf<Long>()

        var current = segments[0].first
        ordered.add(current)
        visited.add(current)

        var loopCount = 0
        val maxLoops = segments.size * 2

        var next: Long?
        do {
            val neighbors = adj[current] ?: break
            next = null
            for (neighbor in neighbors) {
                if (!visited.contains(neighbor)) {
                    next = neighbor
                    break
                }
            }
            if (next != null) {
                ordered.add(next)
                visited.add(next)
                current = next
            }
            loopCount++
        } while (next != null && loopCount < maxLoops)

        return ordered
    }

    private fun findHashes(str: String): List<Long> {
        val list = mutableListOf<Long>()
        var i = 0
        val len = str.length
        while (i < len) {
            if (str[i] == '#') {
                i++
                val start = i
                while (i < len && str[i].isDigit()) {
                    i++
                }
                if (i > start) {
                    val num = str.substring(start, i).toLongOrNull()
                    if (num != null) {
                        list.add(num)
                    }
                }
            } else {
                i++
            }
        }
        return list
    }
}
