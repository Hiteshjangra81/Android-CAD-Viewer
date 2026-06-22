package com.example.cad

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3D(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3D(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector3D(x / scalar, y / scalar, z / scalar) else Vector3D(0f, 0f, 0f)

    fun dot(other: Vector3D): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3D): Vector3D {
        return Vector3D(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3D {
        val len = length()
        return if (len > 0f) this / len else Vector3D()
    }

    fun rotateX(angleRad: Float): Vector3D {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3D(x, y * cosA - z * sinA, y * sinA + z * cosA)
    }

    fun rotateY(angleRad: Float): Vector3D {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3D(x * cosA + z * sinA, y, -x * sinA + z * cosA)
    }

    fun rotateZ(angleRad: Float): Vector3D {
        val cosA = cos(angleRad)
        val sinA = sin(angleRad)
        return Vector3D(x * cosA - y * sinA, x * sinA + y * cosA, z)
    }

    fun rotate(yaw: Float, pitch: Float, roll: Float): Vector3D {
        return this.rotateY(yaw).rotateX(pitch).rotateZ(roll)
    }

    fun distanceTo(other: Vector3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
