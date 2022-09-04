// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.mapbox.mapboxgl

import android.content.Context
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.*

/** Conversions between JSON-like values and MapboxMaps data types.  */
internal object Convert {
    private const val TAG = "Convert"

    @JvmStatic
    fun toBoolean(o: Any): Boolean {
        return o as Boolean
    }

    @JvmStatic
    fun toCameraPosition(o: Any): CameraOptions {
        val data = toMap(o)
        val builder = CameraOptions.Builder()
        builder.bearing(toFloat(data["bearing"]).toDouble())
        builder.center(toLatLng(data["target"]))
        builder.pitch(toFloat(data["tilt"]).toDouble())
        builder.zoom(toFloat(data["zoom"]).toDouble())
        return builder.build()
    }

    fun isScrollByCameraUpdate(o: Any?): Boolean {
        return toString(toList(o)!![0]!!) == "scrollBy"
    }

    @JvmStatic
    fun toCameraUpdate(o: Any, mapboxView: MapView, density: Float): CameraOptions? {
        val data = toList(o)
        return when (toString(data!![0]!!)) {
            "newCameraPosition" -> toCameraPosition(data[1]!!)
            "newLatLng" -> CameraOptions.Builder().center(toLatLng(data[1])).build()
//            "newLatLngBounds" -> CameraUpdateFactory.newLatLngBounds(
//                toLatLngBounds(data[1])!!,
//                toPixels(data[2]!!, density),
//                toPixels(data[3]!!, density),
//                toPixels(data[4]!!, density),
//                toPixels(data[5]!!, density)
//            )
            "newLatLngZoom" -> CameraOptions.Builder().center(toLatLng(data[1]))
                .zoom(toFloat(data[2]).toDouble()).build()
//            "scrollBy" -> {
//                mapboxMap.scrollBy(
//                    toFractionalPixels(data[1]!!, density), toFractionalPixels(
//                        data[2]!!, density
//                    )
//                )
//                null
//            }
//            "zoomBy" -> if (data.size == 2) {
//                CameraUpdateFactory.zoomBy(toFloat(data[1]).toDouble())
//            } else {
//                CameraUpdateFactory.zoomBy(
//                    toFloat(data[1]).toDouble(), toPoint(
//                        data[2], density
//                    )
//                )
//            }
//            "zoomIn" -> CameraUpdateFactory.zoomIn()
//            "zoomOut" -> CameraUpdateFactory.zoomOut()
            "zoomTo" -> CameraOptions.Builder().zoom(toFloat(data[1]).toDouble()).build()
            "bearingTo" -> CameraOptions.Builder().bearing(toFloat(data[1]).toDouble()).build()
            "tiltTo" -> CameraOptions.Builder().pitch(toFloat(data[1]).toDouble()).build()
            else -> throw IllegalArgumentException("Cannot interpret $o as CameraUpdate")
        }
    }

    private fun toDouble(o: Any): Double {
        return (o as Number).toDouble()
    }

    @JvmStatic
    fun toFloat(o: Any?): Float {
        return (o as Number?)!!.toFloat()
    }

    private fun toFloatWrapper(o: Any?): Float? {
        return if (o == null) null else toFloat(o)
    }

    @JvmStatic
    fun toInt(o: Any): Int {
        return (o as Number).toInt()
    }

    fun toJson(position: CameraState?): Any? {
        if (position == null) {
            return null
        }
        val data: MutableMap<String, Any> = HashMap()
        data["bearing"] = position.bearing
        data["target"] = toJson(position.center)
        data["tilt"] = position.pitch
        data["zoom"] = position.zoom
        return data
    }

    private fun toJson(latLng: Point): Any {
        return listOf(latLng.latitude(), latLng.longitude())
    }

    private fun toLatLng(o: Any?): Point {
        val data = toList(o)
        return Point.fromLngLat(toDouble(data!![1]!!), toDouble(data[1]!!))
    }

    private fun toLatLngBounds(o: Any?): CameraBoundsOptions? {
        if (o == null) {
            return null
        }
        val data = toList(o)
        val builder = CameraBoundsOptions.Builder()
        builder.bounds(CoordinateBounds(toLatLng(data!![0]), toLatLng(data[1])))
        return builder.build()
    }

    @JvmStatic
    fun toLatLngList(o: Any?, flippedOrder: Boolean): List<Point>? {
        if (o == null) {
            return null
        }
        val data = toList(o)
        val latLngList: MutableList<Point> = ArrayList()
        for (i in data!!.indices) {
            val coords = toList(data[i])
            if (flippedOrder) {
                latLngList.add(Point.fromLngLat(toDouble(coords!![0]!!),toDouble(coords[1]!!)))
            } else {
                latLngList.add(Point.fromLngLat(toDouble(coords!![1]!!),toDouble(coords[0]!!)))
            }
        }
        return latLngList
    }

    private fun toLatLngListList(o: Any?): List<List<Point>?>? {
        if (o == null) {
            return null
        }
        val data = toList(o)
        val latLngListList: MutableList<List<Point>?> = ArrayList()
        for (i in data!!.indices) {
            val latLngList = toLatLngList(
                data[i], false
            )
            latLngListList.add(latLngList)
        }
        return latLngListList
    }

    fun interpretListLatLng(geometry: List<List<Point>>): Polygon {
        val points: MutableList<List<Point>> = ArrayList(geometry.size)
        for (innerGeometry in geometry) {
            val innerPoints: MutableList<Point> = ArrayList(innerGeometry.size)
            for (latLng in innerGeometry) {
                innerPoints.add(Point.fromLngLat(latLng.longitude(), latLng.latitude()))
            }
            points.add(innerPoints)
        }
        return Polygon.fromLngLats(points)
    }

    @JvmStatic
    fun toList(o: Any?): List<*>? {
        return o as List<*>?
    }

    fun toLong(o: Any): Long {
        return (o as Number).toLong()
    }

    @JvmStatic
    fun toMap(o: Any): Map<*, *> {
        return o as Map<*, *>
    }

    private fun toFractionalPixels(o: Any, density: Float): Float {
        return toFloat(o) * density
    }

    @JvmStatic
    fun toPixels(o: Any, density: Float): Int {
        return toFractionalPixels(o, density).toInt()
    }

    private fun toPoint(o: Any?, density: Float): android.graphics.Point {
        val data = toList(o)
        return android.graphics.Point(
            toPixels(data!![0]!!, density), toPixels(
                data[1]!!, density
            )
        )
    }

    @JvmStatic
    fun toString(o: Any): String {
        return o as String
    }

    @JvmStatic
    fun interpretMapboxMapOptions(o: Any, sink: MapboxMapOptionsSink, context: Context) {
        val metrics = context.resources.displayMetrics
        val data = toMap(o)
        val cameraTargetBounds = data["cameraTargetBounds"]
        if (cameraTargetBounds != null) {
            val targetData = toList(cameraTargetBounds)
            sink.setCameraTargetBounds(toLatLngBounds(targetData!![0]))
        }
        val compassEnabled = data["compassEnabled"]
        if (compassEnabled != null) {
            sink.setCompassEnabled(toBoolean(compassEnabled))
        }
        val styleString = data["styleString"]
        if (styleString != null) {
            sink.setStyleString(toString(styleString))
        }
        val minMaxZoomPreference = data["minMaxZoomPreference"]
        if (minMaxZoomPreference != null) {
            val zoomPreferenceData = toList(minMaxZoomPreference)
            sink.setMinMaxZoomPreference( //
                toFloatWrapper(zoomPreferenceData!![0]),  //
                toFloatWrapper(zoomPreferenceData[1])
            )
        }
        val rotateGesturesEnabled = data["rotateGesturesEnabled"]
        if (rotateGesturesEnabled != null) {
            sink.setRotateGesturesEnabled(toBoolean(rotateGesturesEnabled))
        }
        val scrollGesturesEnabled = data["scrollGesturesEnabled"]
        if (scrollGesturesEnabled != null) {
            sink.setScrollGesturesEnabled(toBoolean(scrollGesturesEnabled))
        }
        val tiltGesturesEnabled = data["tiltGesturesEnabled"]
        if (tiltGesturesEnabled != null) {
            sink.setTiltGesturesEnabled(toBoolean(tiltGesturesEnabled))
        }
        val trackCameraPosition = data["trackCameraPosition"]
        if (trackCameraPosition != null) {
            sink.setTrackCameraPosition(toBoolean(trackCameraPosition))
        }
        val zoomGesturesEnabled = data["zoomGesturesEnabled"]
        if (zoomGesturesEnabled != null) {
            sink.setZoomGesturesEnabled(toBoolean(zoomGesturesEnabled))
        }
        val myLocationEnabled = data["myLocationEnabled"]
        if (myLocationEnabled != null) {
            sink.setMyLocationEnabled(toBoolean(myLocationEnabled))
        }
        val myLocationTrackingMode = data["myLocationTrackingMode"]
        if (myLocationTrackingMode != null) {
            sink.setMyLocationTrackingMode(toInt(myLocationTrackingMode))
        }
        val myLocationRenderMode = data["myLocationRenderMode"]
        if (myLocationRenderMode != null) {
            sink.setMyLocationRenderMode(toInt(myLocationRenderMode))
        }
        val logoViewMargins = data["logoViewMargins"]
        if (logoViewMargins != null) {
            val logoViewMarginsData = toList(logoViewMargins)
            val point = toPoint(logoViewMarginsData, metrics.density)
            sink.setLogoViewMargins(point.x, point.y)
        }
        val compassGravity = data["compassViewPosition"]
        if (compassGravity != null) {
            sink.setCompassGravity(toInt(compassGravity))
        }
        val compassViewMargins = data["compassViewMargins"]
        if (compassViewMargins != null) {
            val compassViewMarginsData = toList(compassViewMargins)
            val point = toPoint(compassViewMarginsData, metrics.density)
            sink.setCompassViewMargins(point.x, point.y)
        }
        val attributionButtonGravity = data["attributionButtonPosition"]
        if (attributionButtonGravity != null) {
            sink.setAttributionButtonGravity(toInt(attributionButtonGravity))
        }
        val attributionButtonMargins = data["attributionButtonMargins"]
        if (attributionButtonMargins != null) {
            val attributionButtonMarginsData = toList(attributionButtonMargins)
            val point = toPoint(attributionButtonMarginsData, metrics.density)
            sink.setAttributionButtonMargins(point.x, point.y)
        }
    }
}