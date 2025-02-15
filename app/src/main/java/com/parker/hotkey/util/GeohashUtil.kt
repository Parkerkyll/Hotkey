package com.parker.hotkey.util

import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// LatLngBounds 확장 함수
fun LatLngBounds.centerLatitude(): Double = (northLatitude + southLatitude) / 2
fun LatLngBounds.centerLongitude(): Double = (eastLongitude + westLongitude) / 2

object GeohashUtil {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private const val PRECISION = 6 // 약 1.2km 정도의 정확도 (geohash6 기준)

    fun encode(lat: Double, lon: Double, precision: Int = PRECISION): String {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        
        val bits = mutableListOf<Boolean>()
        var isEven = true
        
        while (bits.size < precision * 5) {
            if (isEven) {
                val lonMid = (lonMin + lonMax) / 2
                if (lon >= lonMid) {
                    bits.add(true)
                    lonMin = lonMid
                } else {
                    bits.add(false)
                    lonMax = lonMid
                }
            } else {
                val latMid = (latMin + latMax) / 2
                if (lat >= latMid) {
                    bits.add(true)
                    latMin = latMid
                } else {
                    bits.add(false)
                    latMax = latMid
                }
            }
            isEven = !isEven
        }
        
        // Convert bits to base32
        val hash = StringBuilder()
        for (i in 0 until precision * 5 step 5) {
            var value = 0
            for (j in 0..4) {
                if (bits.getOrNull(i + j) == true) {
                    value = value or (1 shl (4 - j))
                }
            }
            hash.append(BASE32[value])
        }
        
        return hash.toString()
    }

    fun encode(position: LatLng, precision: Int = PRECISION): String {
        return encode(position.latitude, position.longitude, precision)
    }

    fun decode(geohash: String): Pair<Double, Double> {
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var isEven = true
        
        for (c in geohash) {
            val value = BASE32.indexOf(c)
            for (i in 4 downTo 0) {
                val bit = (value shr i) and 1
                if (isEven) {
                    val lonMid = (lonMin + lonMax) / 2
                    if (bit == 1) {
                        lonMin = lonMid
                    } else {
                        lonMax = lonMid
                    }
                } else {
                    val latMid = (latMin + latMax) / 2
                    if (bit == 1) {
                        latMin = latMid
                    } else {
                        latMax = latMid
                    }
                }
                isEven = !isEven
            }
        }
        
        val lat = (latMin + latMax) / 2
        val lon = (lonMin + lonMax) / 2
        return Pair(lat, lon)
    }

    fun getNeighbors(geohash: String): List<String> {
        val (lat, lon) = decode(geohash)
        val precision = geohash.length
        val width = getGeohashWidthDegrees(precision)
        val height = getGeohashHeightDegrees(precision)
        
        return listOf(
            encode(lat + height, lon, precision),           // north
            encode(lat + height, lon + width, precision),   // northeast
            encode(lat, lon + width, precision),            // east
            encode(lat - height, lon + width, precision),   // southeast
            encode(lat - height, lon, precision),           // south
            encode(lat - height, lon - width, precision),   // southwest
            encode(lat, lon - width, precision),            // west
            encode(lat + height, lon - width, precision)    // northwest
        )
    }

    private fun getGeohashWidthDegrees(precision: Int): Double {
        return 360.0 / 2.0.pow(precision * 2.5)
    }
    
    private fun getGeohashHeightDegrees(precision: Int): Double {
        return 180.0 / 2.0.pow(precision * 2.5)
    }

    fun getGeohashesInBounds(bounds: LatLngBounds, precision: Int = PRECISION): Set<String> {
        val geohashes = mutableSetOf<String>()
        val step = when (precision) {
            1 -> 5.0
            2 -> 1.0
            3 -> 0.2
            4 -> 0.04
            5 -> 0.008
            6 -> 0.0016
            7 -> 0.0004
            else -> 0.0001
        }

        var lat = bounds.southLatitude
        while (lat <= bounds.northLatitude) {
            var lon = bounds.westLongitude
            while (lon <= bounds.eastLongitude) {
                geohashes.add(encode(lat, lon, precision))
                lon += step
            }
            lat += step
        }

        return geohashes
    }
} 