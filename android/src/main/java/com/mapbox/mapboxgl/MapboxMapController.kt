// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
package com.mapbox.mapboxgl

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.location.Location
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.gestures.AndroidGesturesManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.android.gestures.MoveGestureDetector.OnMoveGestureListener
import com.mapbox.bindgen.Value
import com.mapbox.common.TelemetryUtils
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxgl.Convert.interpretMapboxMapOptions
import com.mapbox.mapboxgl.Convert.toCameraUpdate
import com.mapbox.mapboxgl.Convert.toJson
import com.mapbox.mapboxgl.Convert.toPixels
import com.mapbox.mapboxgl.Convert.toString
import com.mapbox.mapboxgl.MapboxMapsPlugin.LifecycleProvider
import com.mapbox.maps.*
import com.mapbox.maps.extension.localization.localizeLabels
import com.mapbox.maps.extension.observable.eventdata.CameraChangedEventData
import com.mapbox.maps.extension.observable.eventdata.MapIdleEventData
import com.mapbox.maps.extension.observable.eventdata.StyleImageMissingEventData
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.Layer
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.*
import com.mapbox.maps.extension.style.layers.getLayerAs
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.delegates.listeners.OnMapIdleListener
import com.mapbox.maps.plugin.gestures.*
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.logo.logo
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.localization.LocalizationPlugin
import io.flutter.plugin.platform.PlatformView
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import kotlin.math.ceil

/** Controller of a single MapboxMaps MapView instance.  */
@SuppressLint("MissingPermission")
internal class MapboxMapController(
    id: Int,
    context: Context,
    messenger: BinaryMessenger?,
    lifecycleProvider: LifecycleProvider,
    options: MapInitOptions,
    accessToken: String?,
    styleStringInitial: String,
    dragEnabled: Boolean
) : DefaultLifecycleObserver, MapboxMapOptionsSink, MethodCallHandler, PlatformView,
    OnMapClickListener, OnMapLongClickListener, OnMapIdleListener, OnCameraChangeListener {
    private val id: Int
    private val methodChannel: MethodChannel
    private val lifecycleProvider: LifecycleProvider
    private val density: Float
    private val context: Context
    private val styleStringInitial: String
    private var mapView: MapView
    private var mapboxMap: MapboxMap? = null
    private var trackCameraPosition = false
    private var myLocationEnabled = false
    private var myLocationTrackingMode = 0
    private var myLocationRenderMode = 0
    private var disposed = false
    private var dragEnabled = true
    private var mapReadyResult: MethodChannel.Result? = null

    //    private var locationComponent: LocationComponent? = null
    private var locationEngine: LocationEngine? = null
    private var locationEngineCallback: LocationEngineCallback<LocationEngineResult>? = null

    //    private var localizationPlugin: LocalizationPlugin? = null
    private var style: Style? = null
    private var draggedFeature: Feature? = null
    private var androidGesturesManager: AndroidGesturesManager? = null
    private var dragOrigin: Point? = null
    private var dragPrevious: Point? = null
    private val interactiveFeatureLayerIds: MutableSet<String?>
    private val addedFeaturesByLayer: MutableMap<String?, FeatureCollection>
    private var bounds: CameraBoundsOptions? = null

    init {
        this.id = id
        this.context = context
        this.dragEnabled = dragEnabled
        this.styleStringInitial = styleStringInitial
        mapView = MapView(context, options)
        interactiveFeatureLayerIds = HashSet()
        addedFeaturesByLayer = HashMap()
        density = context.resources.displayMetrics.density
        this.lifecycleProvider = lifecycleProvider
        if (dragEnabled) {
            androidGesturesManager = AndroidGesturesManager(mapView.context, false)
        }
        methodChannel = MethodChannel(messenger!!, "plugins.flutter.io/mapbox_maps_$id")
        methodChannel.setMethodCallHandler(this)
    }

    var onStyleLoadedCallback = Style.OnStyleLoaded { style ->
        this@MapboxMapController.style = style
        updateMyLocationEnabled()
        setWorldView(style)
        if (null != bounds) {
            mapView.getMapboxMap().setBounds(bounds!!)
        }
//        localizationPlugin = LocalizationPlugin(mapView, mapboxMap!!, style)
        style.localizeLabels(Locale.CHINA)
        methodChannel.invokeMethod("map#onStyleLoaded", null)
    }

    override fun getView(): View {
        return mapView
    }

    fun init() {
        lifecycleProvider.getLifecycle()?.addObserver(this)
        onMapReady(mapView.getMapboxMap())
    }

    private fun moveCamera(cameraUpdate: CameraOptions) {
        mapboxMap!!.easeTo(cameraUpdate)
    }

    private fun animateCamera(cameraUpdate: CameraOptions) {
        mapboxMap!!.flyTo(cameraUpdate)
    }

    private val cameraPosition: CameraState?
        get() = if (trackCameraPosition) mapView.getMapboxMap().cameraState else null

    @SuppressLint("ClickableViewAccessibility")
    private fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        if (mapReadyResult != null) {
            mapReadyResult!!.success(null)
            mapReadyResult = null
        }
        mapView.getMapboxMap().addOnMapClickListener(this)
        mapView.getMapboxMap().addOnMapLongClickListener(this)

//        mapboxMap.addOnCameraMoveStartedListener(this)
//        mapboxMap.addOnCameraMoveListener(this)
//        mapboxMap.addOnCameraIdleListener(this)
        mapView.getMapboxMap().addOnMapIdleListener(this)
        mapView.getMapboxMap().addOnCameraChangeListener(this)
        mapView.getMapboxMap().addOnMoveListener(object :OnMoveListener {
            override fun onMove(detector: MoveGestureDetector): Boolean {
                return this@MapboxMapController.onMove(detector)
            }

            override fun onMoveBegin(detector: MoveGestureDetector) {
                this@MapboxMapController.onMoveBegin(detector)
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                this@MapboxMapController.onMoveEnd(detector)
            }

        })

        // 移除Logo判断的感叹号按钮
        mapView.attribution.enabled = false
        mapView.logo.enabled = false
        // 移除Logo
        // mapboxMap.getUiSettings().setLogoEnabled(false);

        if (androidGesturesManager != null) {
            androidGesturesManager?.setMoveGestureListener(MoveGestureListener())
            mapView.setOnTouchListener { v, event ->
                androidGesturesManager?.onTouchEvent(event)
                draggedFeature != null
            }
        }
        mapView.getMapboxMap().addOnStyleImageMissingListener { data: StyleImageMissingEventData ->
            val displayMetrics = context.resources.displayMetrics
            val bitmap = getScaledImage(data.id, displayMetrics.density)
            if (bitmap != null) {
                mapView.getMapboxMap().getStyle()!!.addImage(data.id, bitmap)
            }
        }
//        mapView.addOnDidBecomeIdleListener(this)
        setStyleString(styleStringInitial)
    }

    override fun setStyleString(styleString: String) {
        // clear old layer id from the location Component
        clearLocationComponentLayer()

        // Check if json, url, absolute path or asset path:
        if (styleString.isEmpty()) {
            Log.e(TAG, "setStyleString - string empty or null")
        } else if (styleString.startsWith("{") || styleString.startsWith("[")) {
            mapView.getMapboxMap().loadStyleJson(styleString, onStyleLoadedCallback)
        } else if (styleString.startsWith("/")) {
            // Absolute path
            mapView.getMapboxMap().loadStyleUri("file://$styleString", onStyleLoadedCallback)
        } else if (!styleString.startsWith("http://")
            && !styleString.startsWith("https://")
            && !styleString.startsWith("mapbox://")
        ) {
            // We are assuming that the style will be loaded from an asset here.
            val key = MapboxMapsPlugin.flutterAssets?.getAssetFilePathByName(styleString)
            mapView.getMapboxMap().loadStyleUri("asset://$key", onStyleLoadedCallback)
        } else {
            mapView.getMapboxMap().loadStyleUri(styleString, onStyleLoadedCallback)
        }
    }

    private fun enableLocationComponent(style: Style) {
        mapView.location.enabled = false
//        if (hasLocationPermission()) {
//            locationEngine = LocationEngineProvider.getBestLocationEngine(context)
//            locationComponent = mapboxMap!!.locationComponent
//            locationComponent!!.activateLocationComponent(
//                context, style, buildLocationComponentOptions(style)
//            )
//            locationComponent!!.isLocationComponentEnabled = true
//            // locationComponent.setRenderMode(RenderMode.COMPASS); // remove or keep default?
//            locationComponent!!.locationEngine = locationEngine
//            locationComponent!!.setMaxAnimationFps(30)
//            updateMyLocationTrackingMode()
//            updateMyLocationRenderMode()
//            locationComponent!!.addOnCameraTrackingChangedListener(this)
//        } else {
//            Log.e(TAG, "missing location permissions")
//        }
    }

    private fun updateLocationComponentLayer() {
//        if (locationComponent != null && locationComponentRequiresUpdate()) {
//            locationComponent!!.applyStyle(buildLocationComponentOptions(style))
//        }
    }

    private fun clearLocationComponentLayer() {
//        if (locationComponent != null) {
//            locationComponent!!.applyStyle(buildLocationComponentOptions(null))
//        }
    }

    private fun getLastLayerOnStyle(style: Style?): String? {
        if (style != null) {
            val layers = style.styleLayers
            if (layers.isNotEmpty()) {
                return layers[layers.size - 1].id
            }
        }
        return null
    }

    /// only update if the last layer is not the mapbox-location-bearing-layer
    fun locationComponentRequiresUpdate(): Boolean {
        val lastLayerId = getLastLayerOnStyle(style)
        return lastLayerId != null && lastLayerId != "mapbox-location-bearing-layer"
    }

//    private fun buildLocationComponentOptions(style: Style?): LocationComponentOptions {
//        val optionsBuilder = LocationComponentOptions.builder(context)
//        optionsBuilder.trackingGesturesManagement(true)
//        val lastLayerId = getLastLayerOnStyle(style)
//        if (lastLayerId != null) {
//            optionsBuilder.layerAbove(lastLayerId)
//        }
//        return optionsBuilder.build()
//    }

    private fun onUserLocationUpdate(location: Location?) {
        if (location == null) {
            return
        }
        val userLocation: MutableMap<String, Any?> = HashMap(6)
        userLocation["position"] = doubleArrayOf(location.latitude, location.longitude)
        userLocation["speed"] = location.speed
        userLocation["altitude"] = location.altitude
        userLocation["bearing"] = location.bearing
        userLocation["horizontalAccuracy"] = location.accuracy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            userLocation["verticalAccuracy"] =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) location.verticalAccuracyMeters else null
        }
        userLocation["timestamp"] = location.time
        val arguments: MutableMap<String, Any> = HashMap(1)
        arguments["userLocation"] = userLocation
        methodChannel.invokeMethod("map#onUserLocationUpdated", arguments)
    }

    private fun addGeoJsonSource(sourceName: String, source: String) {
        val featureCollection = FeatureCollection.fromJson(source)
        val geoJsonSource =
            GeoJsonSource.Builder(sourceName).featureCollection(featureCollection).build()
        addedFeaturesByLayer[sourceName] = featureCollection
        style!!.addSource(geoJsonSource)
    }

    private fun setGeoJsonSource(sourceName: String, geojson: String) {
        val featureCollection = FeatureCollection.fromJson(geojson)
        val geoJsonSource = style!!.getSourceAs<GeoJsonSource>(sourceName)
        addedFeaturesByLayer[sourceName] = featureCollection
        geoJsonSource?.featureCollection(featureCollection)
    }

    private fun setGeoJsonFeature(sourceName: String?, geojsonFeature: String?) {
        val feature = Feature.fromJson(geojsonFeature!!)
        val featureCollection = addedFeaturesByLayer[sourceName]
        val geoJsonSource = style!!.getSourceAs<GeoJsonSource>(sourceName!!)
        if (featureCollection != null && geoJsonSource != null) {
            val features = featureCollection.features()
            for (i in features!!.indices) {
                val id = features[i].id()
                if (id == feature.id()) {
                    features[i] = feature
                    break
                }
            }
            geoJsonSource.featureCollection(featureCollection)
        }
    }

    private fun addSymbolLayer(
        layerName: String,
        sourceName: String,
        belowLayerId: String?,
        sourceLayer: String?,
        minZoom: Float?,
        maxZoom: Float?,
        enableInteraction: Boolean,
        filter: Expression?
    ) {
        val symbolLayer = SymbolLayer(layerName, sourceName)
        if (sourceLayer != null) {
            symbolLayer.sourceLayer(sourceLayer)
        }
        if (minZoom != null) {
            symbolLayer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            symbolLayer.maxZoom(maxZoom.toDouble())
        }
        if (filter != null) {
            symbolLayer.filter(filter)
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(symbolLayer, belowLayerId)
        } else {
            style!!.addLayer(symbolLayer)
        }
        if (enableInteraction) {
            interactiveFeatureLayerIds.add(layerName)
        }
    }

    private fun addLineLayer(
        layerName: String,
        sourceName: String,
        belowLayerId: String?,
        sourceLayer: String?,
        minZoom: Float?,
        maxZoom: Float?,
        enableInteraction: Boolean,
        filter: Expression?
    ) {
        val lineLayer = LineLayer(layerName, sourceName)
        if (sourceLayer != null) {
            lineLayer.sourceLayer(sourceLayer)
        }
        if (minZoom != null) {
            lineLayer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            lineLayer.maxZoom(maxZoom.toDouble())
        }
        if (filter != null) {
            lineLayer.filter(filter)
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(lineLayer, belowLayerId)
        } else {
            style!!.addLayer(lineLayer)
        }
        if (enableInteraction) {
            interactiveFeatureLayerIds.add(layerName)
        }
    }

    private fun addFillLayer(
        layerName: String,
        sourceName: String,
        belowLayerId: String?,
        sourceLayer: String?,
        minZoom: Float?,
        maxZoom: Float?,
        enableInteraction: Boolean,
        filter: Expression?
    ) {
        val fillLayer = FillLayer(layerName, sourceName)
        if (sourceLayer != null) {
            fillLayer.sourceLayer(sourceLayer)
        }
        if (minZoom != null) {
            fillLayer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            fillLayer.maxZoom(maxZoom.toDouble())
        }
        if (filter != null) {
            fillLayer.filter(filter)
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(fillLayer, belowLayerId)
        } else {
            style!!.addLayer(fillLayer)
        }
        if (enableInteraction) {
            interactiveFeatureLayerIds.add(layerName)
        }
    }

    private fun addCircleLayer(
        layerName: String,
        sourceName: String,
        belowLayerId: String?,
        sourceLayer: String?,
        minZoom: Float?,
        maxZoom: Float?,
        enableInteraction: Boolean,
        filter: Expression?
    ) {
        val circleLayer = CircleLayer(layerName, sourceName)
        if (sourceLayer != null) {
            circleLayer.sourceLayer(sourceLayer)
        }
        if (minZoom != null) {
            circleLayer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            circleLayer.maxZoom(maxZoom.toDouble())
        }
        if (filter != null) {
            circleLayer.filter(filter)
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(circleLayer, belowLayerId)
        } else {
            style!!.addLayer(circleLayer)
        }
        if (enableInteraction) {
            interactiveFeatureLayerIds.add(layerName)
        }
    }

    private fun parseFilter(filter: String?): Expression? {
        return if (filter.isNullOrBlank()) null else Expression.fromRaw(filter)
    }

    private fun addRasterLayer(
        layerName: String?,
        sourceName: String?,
        minZoom: Float?,
        maxZoom: Float?,
        belowLayerId: String?,
    ) {
        if (layerName.isNullOrBlank() or sourceName.isNullOrBlank()) return
        val layer = RasterLayer(layerName!!, sourceName!!)
        if (minZoom != null) {
            layer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            layer.maxZoom(maxZoom.toDouble())
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(layer, belowLayerId)
        } else {
            style!!.addLayer(layer)
        }
    }

    private fun addHillshadeLayer(
        layerName: String?,
        sourceName: String?,
        minZoom: Float?,
        maxZoom: Float?,
        belowLayerId: String?,
    ) {
        if (layerName.isNullOrBlank() or sourceName.isNullOrBlank()) return
        val layer = HillshadeLayer(layerName!!, sourceName!!)
        if (minZoom != null) {
            layer.minZoom(minZoom.toDouble())
        }
        if (maxZoom != null) {
            layer.maxZoom(maxZoom.toDouble())
        }
        if (belowLayerId != null) {
            style!!.addLayerBelow(layer, belowLayerId)
        } else {
            style!!.addLayer(layer)
        }
    }

    private fun firstFeatureOnLayers(
        screenCoordinate: ScreenCoordinate,
        callback: (feature: Feature?) -> Unit
    ) {
        if (style != null) {
            val layers = style!!.styleLayers
            val layersInOrder: MutableList<String?> = ArrayList()
            for (layer in layers) {
                val id = layer.id
                if (interactiveFeatureLayerIds.contains(id)) layersInOrder.add(id)
            }
            layersInOrder.reverse()
            mapView.getMapboxMap().queryRenderedFeatures(
                RenderedQueryGeometry(screenCoordinate),
                RenderedQueryOptions(layersInOrder, null)
            ) {
                if (it.isValue && it.value!!.isNotEmpty()) {
                    callback.invoke(it.value!![0].feature)
                } else {
                    callback.invoke(null)
                }
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "map#takeSnapshot" -> {
                mapView.snapshot { bitmap: Bitmap? ->
                    if (bitmap == null) {
                        result.success(null)
                        return@snapshot
                    }
                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    val byteArray = stream.toByteArray()
                    bitmap.recycle()
                    result.success(byteArray)
                }
            }
            "map#waitForMap" -> {
                if (mapboxMap != null) {
                    result.success(null)
                    return
                }
                mapReadyResult = result
            }
            "map#update" -> {
                interpretMapboxMapOptions(call.argument("options")!!, this, context)
                result.success(toJson(cameraPosition))
            }
            "map#updateMyLocationTrackingMode" -> {
                val myLocationTrackingMode = call.argument<Int>("mode")!!
                setMyLocationTrackingMode(myLocationTrackingMode)
                result.success(null)
            }
            "map#matchMapLanguageWithDeviceDefault" -> {
                try {
                    style?.localizeLabels(Locale.getDefault())
                    result.success(null)
                } catch (exception: RuntimeException) {
                    Log.d(TAG, exception.toString())
                    result.error("MAPBOX LOCALIZATION PLUGIN ERROR", exception.toString(), null)
                }
            }
            "map#updateContentInsets" -> {
                val insets = call.argument<HashMap<String, Any>>("bounds")!!
                val cameraUpdate = CameraOptions.Builder().anchor(
                    ScreenCoordinate(
                        toPixels(insets["right"]!!, density).toDouble(),
                        toPixels(insets["bottom"]!!, density).toDouble()
                    )
                ).build()
                if (call.argument("animated")!!) {
                    animateCamera(cameraUpdate, null, result)
                } else {
                    moveCamera(cameraUpdate, result)
                }
            }
            "map#setMapLanguage" -> {
                val language = call.argument<String>("language")
                try {
                    val local = LocalizationPlugin.localeFromString(language!!)
                    style?.localizeLabels(local)
                    result.success(null)
                } catch (exception: RuntimeException) {
                    Log.d(TAG, exception.toString())
                    result.error("MAPBOX LOCALIZATION PLUGIN ERROR", exception.toString(), null)
                }
            }
            "map#setStyle" -> {
                if (mapboxMap != null) {
                    try {
                        val style = call.argument<String>("style")
                        setStyleString(style!!)
                        result.success(null)
                    } catch (e: Exception) {
                        Log.d(TAG, e.toString())
                        result.error("MAPBOX PLUGIN ERROR", e.toString(), null)
                    }
                }
            }
            "map#getVisibleRegion" -> {
                val reply: MutableMap<String, Any> = HashMap()
                val options = mapView.getMapboxMap().cameraState.toCameraOptions()
                val bounds = mapView.getMapboxMap().coordinateBoundsForCamera(options)
                reply["sw"] = listOf(
                    bounds.southwest.latitude(), bounds.southwest.longitude()
                )
                reply["ne"] = listOf(
                    bounds.northeast.latitude(), bounds.northeast.longitude()
                )
                result.success(reply)
            }
            "map#toScreenLocation" -> {
                val reply: MutableMap<String, Any> = HashMap()
                val lng = call.argument<Double>("longitude") ?: 0.0
                val lat = call.argument<Double>("latitude") ?: 0.0
                val screenCoordinate =
                    mapView.getMapboxMap().pixelForCoordinate(Point.fromLngLat(lng, lat))
                reply["x"] = screenCoordinate.x
                reply["y"] = screenCoordinate.y
                result.success(reply)
            }
            "map#toScreenLocationBatch" -> {
                val param = call.argument<Any>("coordinates") as DoubleArray?
                if (param == null) {
                    result.success(null)
                    return
                }
                var i = 0
                val pointList = mutableListOf<Point>()
                val resultList = mutableListOf<Double>()
                while (i < param.size) {
                    val lat = param[i]
                    val lng = param[i + 1]
                    pointList.add(Point.fromLngLat(lng, lat))
                    i += 2
                }
                val coordinates = mapView.getMapboxMap().pixelsForCoordinates(pointList)
                coordinates.forEach {
                    resultList.add(it.x)
                    resultList.add(it.y)
                }
                result.success(resultList.toTypedArray())
            }
            "map#toLatLng" -> {
                val reply: MutableMap<String, Any> = HashMap()
                val x = call.argument<Any>("x") as Double?
                val y = call.argument<Any>("y") as Double?
                if (x == null || y == null) {
                    result.success(null)
                    return
                }
                val point = mapView.getMapboxMap().coordinateForPixel(ScreenCoordinate(x, y))
                reply["latitude"] = point.latitude()
                reply["longitude"] = point.longitude()
                result.success(reply)
            }
            "map#getMetersPerPixelAtLatitude" -> {
                val reply: MutableMap<String, Any> = HashMap()
                val retVal = mapView.getMapboxMap()
                    .getMetersPerPixelAtLatitude((call.argument<Any>("latitude") as Double?)!!)
                reply["metersperpixel"] = retVal ?: 0
                result.success(reply)
            }
            "camera#move" -> {
                val cameraUpdate =
                    toCameraUpdate(call.argument("cameraUpdate")!!, mapView, density)
                moveCamera(cameraUpdate, result)
            }
            "camera#animate" -> {
                val cameraUpdate =
                    toCameraUpdate(call.argument("cameraUpdate")!!, mapView, density)
                val duration = call.argument<Long>("duration")
                animateCamera(cameraUpdate, duration, result)
            }
            "map#queryRenderedFeatures" -> {
                val reply: MutableMap<String, Any> = HashMap()
                val layerIds = call.argument<Any>("layerIds") as List<String>?
                val filter = call.argument<List<Any>>("filter")
                val jsonElement = if (filter == null) null else Gson().toJsonTree(filter).asString
                val value = if (jsonElement != null) Value.fromJson(jsonElement).value else null
                val featuresJson: MutableList<String> = ArrayList()
                val geometry = if (call.hasArgument("x")) {
                    val x = call.argument<Double>("x") ?: 0.0
                    val y = call.argument<Double>("y") ?: 0.0
                    RenderedQueryGeometry(ScreenCoordinate(x, y))
                } else {
                    val left = call.argument<Double>("left") ?: 0.0
                    val top = call.argument<Double>("top") ?: 0.0
                    val right = call.argument<Double>("right") ?: 0.0
                    val bottom = call.argument<Double>("bottom") ?: 0.0
                    RenderedQueryGeometry(
                        ScreenBox(
                            ScreenCoordinate(left, top),
                            ScreenCoordinate(right, bottom)
                        )
                    )
                }
                mapView.getMapboxMap()
                    .queryRenderedFeatures(geometry, RenderedQueryOptions(layerIds, value)) {
                        if (it.isValue && it.value!!.isNotEmpty()) {
                            it.value!!.forEach { future ->
                                featuresJson.add(future.feature.toJson())
                            }
                            reply["features"] = featuresJson
                            result.success(reply)
                        } else {
                            result.success(null)
                        }
                    }
            }
            "map#setTelemetryEnabled" -> {
                val enabled = call.argument<Boolean>("enabled")!!
                TelemetryUtils.setEventsCollectionState(enabled) {
                    result.success(null)
                }
            }
            "map#getTelemetryEnabled" -> {
                result.success(TelemetryUtils.getEventsCollectionState())
            }
            "map#invalidateAmbientCache" -> {
//                val fileSource = OfflineManager.getInstance(context)
//                fileSource.invalidateAmbientCache(
//                    object : FileSourceCallback {
//                        override fun onSuccess() {
//                            result.success(null)
//                        }
//
//                        override fun onError(message: String) {
//                            result.error("MAPBOX CACHE ERROR", message, null)
//                        }
//                    })
            }
            "source#addGeoJson" -> {
                val sourceId = call.argument<String>("sourceId")
                val geojson = call.argument<String>("geojson")
                if (sourceId.isNullOrBlank().not() and geojson.isNullOrBlank().not()) {
                    addGeoJsonSource(sourceId!!, geojson!!)
                }
                result.success(null)
            }
            "source#setGeoJson" -> {
                val sourceId = call.argument<String>("sourceId")
                val geojson = call.argument<String>("geojson")
                if (sourceId.isNullOrBlank().not() and geojson.isNullOrBlank().not()) {
                    setGeoJsonSource(sourceId!!, geojson!!)
                }
                result.success(null)
            }
            "source#setFeature" -> {
                val sourceId = call.argument<String>("sourceId")
                val geojsonFeature = call.argument<String>("geojsonFeature")
                setGeoJsonFeature(sourceId, geojsonFeature)
                result.success(null)
            }
            "symbolLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val sourceLayer = call.argument<String>("sourceLayer")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                val filter = call.argument<String>("filter")
                val enableInteraction = call.argument<Boolean>("enableInteraction")!!
                val filterExpression = parseFilter(filter)
                if (sourceId == null || layerId == null) {
                    result.success(null)
                    return
                }
                addSymbolLayer(
                    layerId,
                    sourceId,
                    belowLayerId,
                    sourceLayer,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    enableInteraction,
                    filterExpression
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "lineLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val sourceLayer = call.argument<String>("sourceLayer")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                val filter = call.argument<String>("filter")
                val enableInteraction = call.argument<Boolean>("enableInteraction")!!
                val filterExpression = parseFilter(filter)
                if (sourceId == null || layerId == null) {
                    result.success(null)
                    return
                }
                addLineLayer(
                    layerId,
                    sourceId,
                    belowLayerId,
                    sourceLayer,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    enableInteraction,
                    filterExpression
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "fillLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val sourceLayer = call.argument<String>("sourceLayer")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                val filter = call.argument<String>("filter")
                val enableInteraction = call.argument<Boolean>("enableInteraction")!!
                val filterExpression = parseFilter(filter)
                if (sourceId == null || layerId == null) {
                    result.success(null)
                    return
                }
                addFillLayer(
                    layerId,
                    sourceId,
                    belowLayerId,
                    sourceLayer,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    enableInteraction,
                    filterExpression
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "circleLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val sourceLayer = call.argument<String>("sourceLayer")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                val filter = call.argument<String>("filter")
                val enableInteraction = call.argument<Boolean>("enableInteraction")!!
                val filterExpression = parseFilter(filter)
                if (sourceId == null || layerId == null) {
                    result.success(null)
                    return
                }
                addCircleLayer(
                    layerId,
                    sourceId,
                    belowLayerId,
                    sourceLayer,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    enableInteraction,
                    filterExpression
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "rasterLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                addRasterLayer(
                    layerId,
                    sourceId,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    belowLayerId
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "hillshadeLayer#add" -> {
                val sourceId = call.argument<String>("sourceId")
                val layerId = call.argument<String>("layerId")
                val belowLayerId = call.argument<String>("belowLayerId")
                val minzoom = call.argument<Double>("minzoom")
                val maxzoom = call.argument<Double>("maxzoom")
                addHillshadeLayer(
                    layerId,
                    sourceId,
                    minzoom?.toFloat(),
                    maxzoom?.toFloat(),
                    belowLayerId,
                )
                updateLocationComponentLayer()
                result.success(null)
            }
            "locationComponent#getLastLocation" -> {
                Log.e(TAG, "location component: getLastLocation")
//                if (myLocationEnabled && locationComponent != null && locationEngine != null) {
//                    val reply: MutableMap<String, Any> = HashMap()
//                    locationEngine!!.getLastLocation(
//                        object : LocationEngineCallback<LocationEngineResult> {
//                            override fun onSuccess(locationEngineResult: LocationEngineResult) {
//                                val lastLocation = locationEngineResult.lastLocation
//                                if (lastLocation != null) {
//                                    reply["latitude"] = lastLocation.latitude
//                                    reply["longitude"] = lastLocation.longitude
//                                    reply["altitude"] = lastLocation.altitude
//                                    result.success(reply)
//                                } else {
//                                    result.error("", "", null) // ???
//                                }
//                            }
//
//                            override fun onFailure(exception: Exception) {
//                                result.error("", "", null) // ???
//                            }
//                        })
//                }
                result.error("", "", null)
            }
            "style#addImage" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                style!!.addImage(
                    call.argument("name")!!,
                    BitmapFactory.decodeByteArray(
                        call.argument("bytes"),
                        0,
                        call.argument("length")!!
                    ),
                    call.argument("sdf")!!
                )
                result.success(null)
            }
            "style#addImageSource" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                val sourceId = call.argument<String?>("imageSourceId")
                if (sourceId.isNullOrBlank()) {
                    result.success(null)
                    return
                }

                style?.addImage(
                    sourceId, BitmapFactory.decodeByteArray(
                        call.argument("bytes"), 0, call.argument("length")!!
                    )
                )
                result.success(null)
            }
            "style#addSource" -> {
                val id = toString(call.argument("sourceId")!!)
                val properties = call.argument<Any>("properties") as Map<String?, Any?>?
                if (properties == null || style == null) {
                    result.success(null)
                    return
                }
                SourcePropertyConverter.addSource(id, properties, style!!)
                result.success(null)
            }
            "style#removeSource" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                style!!.removeStyleSource((call.argument<Any>("sourceId") as String?)!!)
                result.success(null)
            }
            "style#addLayer" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                addRasterLayer(
                    call.argument("imageLayerId"),
                    call.argument("imageSourceId"),
                    if (call.argument<Any?>("minzoom") != null) (call.argument<Any>("minzoom") as Double?)!!.toFloat() else null,
                    if (call.argument<Any?>("maxzoom") != null) (call.argument<Any>("maxzoom") as Double?)!!.toFloat() else null,
                    null,
                )
                result.success(null)
            }
            "style#addLayerBelow" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                addRasterLayer(
                    call.argument("imageLayerId"),
                    call.argument("imageSourceId"),
                    if (call.argument<Any?>("minzoom") != null) (call.argument<Any>("minzoom") as Double?)!!.toFloat() else null,
                    if (call.argument<Any?>("maxzoom") != null) (call.argument<Any>("maxzoom") as Double?)!!.toFloat() else null,
                    call.argument("belowLayerId"),
                )
                result.success(null)
            }
            "style#removeLayer" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                val layerId = call.argument<String>("layerId")
                style!!.removeStyleLayer(layerId!!)
                interactiveFeatureLayerIds.remove(layerId)
                result.success(null)
            }
            "style#setFilter" -> {
                if (style == null) {
                    result.error(
                        "STYLE IS NULL",
                        "The style is null. Has onStyleLoaded() already been invoked?",
                        null
                    )
                }
                val layerId = call.argument<String>("layerId")
                val filter = call.argument<String>("filter")
                if (filter == null) {
                    result.success(null)
                    return
                }
                val layer = style!!.getLayerAs<Layer>(layerId!!)
                when (layer) {
                    is CircleLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    is FillExtrusionLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    is FillLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    is HeatmapLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    is LineLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    is SymbolLayer -> {
                        layer.filter(Expression.fromRaw(filter))
                    }
                    else -> {
                        result.error(
                            "INVALID LAYER TYPE",
                            String.format("Layer '%s' does not support filtering.", layerId),
                            null
                        )
                    }
                }
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

//    override fun onCameraMoveStarted(reason: Int) {
//        val arguments: MutableMap<String, Any> = HashMap(2)
//        val isGesture = reason == OnCameraMoveStartedListener.REASON_API_GESTURE
//        arguments["isGesture"] = isGesture
//        methodChannel.invokeMethod("camera#onMoveStarted", arguments)
//    }


    override fun onMapIdle(eventData: MapIdleEventData) {
        val arguments: MutableMap<String, Any?> = HashMap(2)
        if (trackCameraPosition) {
            arguments["position"] = toJson(mapView.getMapboxMap().cameraState)
        }
        methodChannel.invokeMethod("camera#onIdle", arguments)
    }

    override fun onCameraChanged(eventData: CameraChangedEventData) {
        if (!trackCameraPosition) {
            return
        }
        val arguments: MutableMap<String, Any?> = HashMap(2)
        arguments["position"] = toJson(cameraPosition)
        methodChannel.invokeMethod("camera#onMove", arguments)
    }

//    override fun onCameraTrackingChanged(currentMode: Int) {
//        val arguments: MutableMap<String, Any> = HashMap(2)
//        arguments["mode"] = currentMode
//        methodChannel.invokeMethod("map#onCameraTrackingChanged", arguments)
//    }

//    override fun onCameraTrackingDismissed() {
//        myLocationTrackingMode = 0
//        methodChannel.invokeMethod("map#onCameraTrackingDismissed", HashMap<Any, Any>())
//    }

//    override fun onDidBecomeIdle() {
//        methodChannel.invokeMethod("map#onIdle", HashMap<Any, Any>())
//    }

    override fun onMapClick(point: Point): Boolean {
        val screenCoordinate = mapboxMap!!.pixelForCoordinate(point)
        firstFeatureOnLayers(screenCoordinate) {
            val arguments: MutableMap<String, Any?> = HashMap()
            arguments["x"] = screenCoordinate.x
            arguments["y"] = screenCoordinate.y
            arguments["lng"] = point.longitude()
            arguments["lat"] = point.latitude()

            if (it != null) {
                arguments["id"] = it.id()
                methodChannel.invokeMethod("feature#onTap", arguments)
            } else {
                methodChannel.invokeMethod("map#onMapClick", arguments)
            }
        }
        return true
    }

    override fun onMapLongClick(point: Point): Boolean {
        val screenCoordinate = mapboxMap!!.pixelForCoordinate(point)
        firstFeatureOnLayers(screenCoordinate) {
            val arguments: MutableMap<String, Any?> = HashMap()
            arguments["x"] = screenCoordinate.x
            arguments["y"] = screenCoordinate.y
            arguments["lng"] = point.longitude()
            arguments["lat"] = point.latitude()
            if (it != null) {
                arguments["id"] = it.id()
                methodChannel.invokeMethod("feature#onLongTap", arguments)
            } else {
                methodChannel.invokeMethod("map#onMapLongClick", arguments)
            }
        }
        return true
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        methodChannel.setMethodCallHandler(null)
        destroyMapViewIfNecessary()
        val lifecycle = lifecycleProvider.getLifecycle()
        lifecycle?.removeObserver(this)
    }

    private fun moveCamera(cameraUpdate: CameraOptions?, result: MethodChannel.Result) {
        if (cameraUpdate != null) {
            // camera transformation not handled yet
            mapboxMap!!.easeTo(
                cameraUpdate,
                MapAnimationOptions.mapAnimationOptions {
                    this.animatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator?) {
                            result.success(false)
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            result.success(true)
                        }
                    })
                }
            )
        } else {
            result.success(false)
        }
    }

    private fun animateCamera(
        cameraUpdate: CameraOptions?, duration: Long?, result: MethodChannel.Result
    ) {
        val animationListener = object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                result.success(true)
            }

            override fun onAnimationCancel(animation: Animator?) {
                result.success(false)
            }
        }
        if (cameraUpdate != null && duration != null) {
            // camera transformation not handled yet
            mapboxMap!!.flyTo(
                cameraUpdate,
                MapAnimationOptions.Builder().duration(duration).animatorListener(animationListener)
                    .build()
            )
        } else if (cameraUpdate != null) {
            // camera transformation not handled yet
            mapboxMap!!.flyTo(cameraUpdate, MapAnimationOptions.mapAnimationOptions {
                this.animatorListener(animationListener)
            })
        } else {
            result.success(false)
        }
    }

    private fun destroyMapViewIfNecessary() {
//        if (locationComponent != null) {
//            locationComponent!!.isLocationComponentEnabled = false
//        }
        stopListeningForLocationUpdates()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (disposed) {
            return
        }
        if (myLocationEnabled) {
            startListeningForLocationUpdates()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
        if (disposed) {
            return
        }
        destroyMapViewIfNecessary()
    }

    // MapboxMapOptionsSink methods
    override fun setCameraTargetBounds(bounds: CameraBoundsOptions?) {
        this.bounds = bounds
    }

    override fun setCompassEnabled(compassEnabled: Boolean) {
        mapView.compass.enabled = compassEnabled
    }

    override fun setTrackCameraPosition(trackCameraPosition: Boolean) {
        this.trackCameraPosition = trackCameraPosition
    }

    override fun setRotateGesturesEnabled(rotateGesturesEnabled: Boolean) {
        mapView.gestures.rotateEnabled = rotateGesturesEnabled
    }

    override fun setScrollGesturesEnabled(scrollGesturesEnabled: Boolean) {
        mapView.gestures.scrollEnabled = scrollGesturesEnabled
    }

    override fun setTiltGesturesEnabled(tiltGesturesEnabled: Boolean) {
        mapView.gestures.pitchEnabled = tiltGesturesEnabled
    }

    override fun setMinMaxZoomPreference(min: Float?, max: Float?) {
//        mapboxMap!!.setMinZoomPreference(min?.toDouble() ?: MapboxConstants.MINIMUM_ZOOM.toDouble())
//        mapboxMap!!.setMaxZoomPreference(max?.toDouble() ?: MapboxConstants.MAXIMUM_ZOOM.toDouble())
    }

    override fun setZoomGesturesEnabled(zoomGesturesEnabled: Boolean) {
        mapView.gestures.doubleTouchToZoomOutEnabled = zoomGesturesEnabled
    }

    override fun setMyLocationEnabled(myLocationEnabled: Boolean) {
        if (this.myLocationEnabled == myLocationEnabled) {
            return
        }
        this.myLocationEnabled = myLocationEnabled
        if (mapboxMap != null) {
            updateMyLocationEnabled()
        }
    }

    override fun setMyLocationTrackingMode(myLocationTrackingMode: Int) {
        if (mapboxMap != null) {
            // ensure that location is trackable
            updateMyLocationEnabled()
        }
        if (this.myLocationTrackingMode == myLocationTrackingMode) {
            return
        }
        this.myLocationTrackingMode = myLocationTrackingMode
//        if (mapboxMap != null && locationComponent != null) {
//            updateMyLocationTrackingMode()
//        }
    }

    override fun setMyLocationRenderMode(myLocationRenderMode: Int) {
        if (this.myLocationRenderMode == myLocationRenderMode) {
            return
        }
        this.myLocationRenderMode = myLocationRenderMode
//        if (mapboxMap != null && locationComponent != null) {
//            updateMyLocationRenderMode()
//        }
    }

    override fun setLogoViewMargins(x: Int, y: Int) {
        mapView.logo.marginLeft = x.toFloat()
        mapView.logo.marginBottom = y.toFloat()
    }

    override fun setCompassGravity(gravity: Int) {
        when (gravity) {
            0 -> mapView.compass.position = Gravity.TOP or Gravity.START
            1 -> mapView.compass.position = Gravity.TOP or Gravity.END
            2 -> mapView.compass.position = Gravity.BOTTOM or Gravity.START
            3 -> mapView.compass.position = Gravity.BOTTOM or Gravity.END
            else -> mapView.compass.position = Gravity.TOP or Gravity.END
        }
    }

    override fun setCompassViewMargins(x: Int, y: Int) {
        when (mapView.compass.position) {
            Gravity.TOP or Gravity.START -> {
                mapView.compass.also {
                    it.marginLeft = x.toFloat()
                    it.marginTop = y.toFloat()
                }
            }
            Gravity.TOP or Gravity.END -> {
                mapView.compass.also {
                    it.marginRight = x.toFloat()
                    it.marginTop = y.toFloat()
                }
            }
            Gravity.BOTTOM or Gravity.START -> {
                mapView.compass.also {
                    it.marginLeft = x.toFloat()
                    it.marginBottom = y.toFloat()
                }
            }
            Gravity.BOTTOM or Gravity.END -> {
                mapView.compass.also {
                    it.marginRight = x.toFloat()
                    it.marginBottom = y.toFloat()
                }
            }
            else -> {
                mapView.compass.also {
                    it.marginRight = x.toFloat()
                    it.marginTop = y.toFloat()
                }
            }
        }
    }

    override fun setAttributionButtonGravity(gravity: Int) {
        when (gravity) {
            0 -> mapView.attribution.position = Gravity.TOP or Gravity.START
            1 -> mapView.attribution.position = Gravity.TOP or Gravity.END
            2 -> mapView.attribution.position = Gravity.BOTTOM or Gravity.START
            3 -> mapView.attribution.position = Gravity.BOTTOM or Gravity.END
            else -> mapView.attribution.position = Gravity.TOP or Gravity.END
        }
    }

    override fun setAttributionButtonMargins(x: Int, y: Int) {
        when (mapView.attribution.position) {
            Gravity.TOP or Gravity.START -> {
                mapView.attribution.marginLeft = x.toFloat()
                mapView.attribution.marginTop = y.toFloat()
            }
            Gravity.TOP or Gravity.END -> {
                mapView.attribution.marginRight = x.toFloat()
                mapView.attribution.marginTop = y.toFloat()
            }
            Gravity.BOTTOM or Gravity.START -> {
                mapView.attribution.marginLeft = x.toFloat()
                mapView.attribution.marginBottom = y.toFloat()
            }
            Gravity.BOTTOM or Gravity.END -> {
                mapView.attribution.marginRight = x.toFloat()
                mapView.attribution.marginBottom = y.toFloat()
            }
            else -> {
                mapView.attribution.marginRight = x.toFloat()
                mapView.attribution.marginTop = y.toFloat()
            }
        }
    }

    // 将地图世界观设置为中国
    private fun setWorldView(style: Style) {
        for (layer in style.styleLayers) {
            if (layer.id == "admin-0-boundary" || layer.id == "admin-1-boundary" || layer.id == "admin-0-boundary-disputed" || layer.id == "admin-1-boundary-bg" || layer.id == "admin-0-boundary-bg") {
                val lineLayer = style.getLayerAs<LineLayer>(layer.id)
                lineLayer?.filter(
                    Expression.match(
                        Expression.get("worldview"),
                        Expression.all(Expression.literal("CN")),
                        Expression.literal(true),
                        Expression.literal(false)
                    )
                )
            }
        }
    }

    private fun updateMyLocationEnabled() {
//        if (locationComponent == null && myLocationEnabled) {
//            enableLocationComponent(mapboxMap!!.style!!)
//        }
//        if (myLocationEnabled) {
//            startListeningForLocationUpdates()
//        } else {
//            stopListeningForLocationUpdates()
//        }
//        if (locationComponent != null) {
//            locationComponent!!.isLocationComponentEnabled = myLocationEnabled
//        }
    }

    private fun startListeningForLocationUpdates() {
//        if (locationEngineCallback == null && locationComponent != null && locationComponent!!.locationEngine != null) {
//            locationEngineCallback = object : LocationEngineCallback<LocationEngineResult> {
//                override fun onSuccess(result: LocationEngineResult) {
//                    onUserLocationUpdate(result.lastLocation)
//                }
//
//                override fun onFailure(exception: Exception) {}
//            }
//            locationComponent!!
//                .locationEngine!!
//                .requestLocationUpdates(
//                    locationComponent!!.locationEngineRequest, locationEngineCallback!!, null
//                )
//        }
    }

    private fun stopListeningForLocationUpdates() {
//        if (locationEngineCallback != null && locationComponent != null && locationComponent!!.locationEngine != null) {
//            locationComponent!!.locationEngine!!
//                .removeLocationUpdates(locationEngineCallback!!)
//            locationEngineCallback = null
//        }
    }

    private fun updateMyLocationTrackingMode() {
//        val mapboxTrackingModes = intArrayOf(
//            CameraMode.NONE,
//            CameraMode.TRACKING,
//            CameraMode.TRACKING_COMPASS,
//            CameraMode.TRACKING_GPS
//        )
//        locationComponent!!.cameraMode = mapboxTrackingModes[myLocationTrackingMode]
    }

    private fun updateMyLocationRenderMode() {
//        val mapboxRenderModes = intArrayOf(RenderMode.NORMAL, RenderMode.COMPASS, RenderMode.GPS)
//        locationComponent!!.renderMode = mapboxRenderModes[myLocationRenderMode]
    }

    private fun hasLocationPermission(): Boolean {
        return (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
    }

    private fun checkSelfPermission(permission: String?): Int {
        requireNotNull(permission) { "permission is null" }
        return context.checkPermission(
            permission, Process.myPid(), Process.myUid()
        )
    }

    /**
     * Tries to find highest scale image for display type
     *
     * @param imageId
     * @param density
     * @return
     */
    private fun getScaledImage(imageId: String, density: Float): Bitmap? {
        var assetFileDescriptor: AssetFileDescriptor

        // Split image path into parts.
        val imagePathList = Arrays.asList(*imageId.split("/").toTypedArray())
        val assetPathList: MutableList<String> = ArrayList()

        // "On devices with a device pixel ratio of 1.8, the asset .../2.0x/my_icon.png would be chosen.
        // For a device pixel ratio of 2.7, the asset .../3.0x/my_icon.png would be chosen."
        // Source: https://flutter.dev/docs/development/ui/assets-and-images#resolution-aware
        for (i in ceil(density.toDouble()).toInt() downTo 1) {
            val assetPath: String? = if (i == 1) {
                // If density is 1.0x then simply take the default asset path
                MapboxMapsPlugin.flutterAssets?.getAssetFilePathByName(imageId)
            } else {
                // Build a resolution aware asset path as follows:
                // <directory asset>/<ratio>/<image name>
                // where ratio is 1.0x, 2.0x or 3.0x.
                val stringBuilder = StringBuilder()
                for (j in 0 until imagePathList.size - 1) {
                    stringBuilder.append(imagePathList[j])
                    stringBuilder.append("/")
                }
                stringBuilder.append(i.toFloat().toString() + "x")
                stringBuilder.append("/")
                stringBuilder.append(imagePathList[imagePathList.size - 1])
                MapboxMapsPlugin.flutterAssets?.getAssetFilePathByName(stringBuilder.toString())
            }
            // Build up a list of resolution aware asset paths.
            assetPath?.also { assetPathList.add(it) }
        }

        // Iterate over asset paths and get the highest scaled asset (as a bitmap).
        var bitmap: Bitmap? = null
        for (assetPath in assetPathList) {
            try {
                // Read path (throws exception if doesn't exist).
                assetFileDescriptor = mapView.context.assets.openFd(assetPath)
                val assetStream: InputStream = assetFileDescriptor.createInputStream()
                bitmap = BitmapFactory.decodeStream(assetStream)
                assetFileDescriptor.close() // Close for memory
                break // If exists, break
            } catch (e: IOException) {
                // Skip
            }
        }
        return bitmap
    }

    fun onMoveBegin(detector: MoveGestureDetector): Boolean {
        // onMoveBegin gets called even during a move - move end is also not called unless this function
        // returns
        // true at least once. To avoid redundant queries only check for feature if the previous event
        // was ACTION_DOWN
        if (detector.previousEvent.actionMasked == MotionEvent.ACTION_DOWN
            && detector.pointersCount == 1
        ) {
            val pointf = detector.focalPoint
            val screenCoordinate = ScreenCoordinate(pointf.x.toDouble(), pointf.y.toDouble())
            val origin = mapView.getMapboxMap().coordinateForPixel(screenCoordinate)
            firstFeatureOnLayers(screenCoordinate) {
                if (it != null && startDragging(it, origin)) {
                    invokeFeatureDrag(pointf, "start")
                    return@firstFeatureOnLayers
                }
            }
            return true
        }
        return false
    }

    private fun invokeFeatureDrag(pointf: PointF, eventType: String) {
        val screenCoordinate = ScreenCoordinate(pointf.x.toDouble(), pointf.y.toDouble())
        val current = mapView.getMapboxMap().coordinateForPixel(screenCoordinate)
        val arguments: MutableMap<String, Any?> = HashMap(9)
        arguments["id"] = draggedFeature!!.id()
        arguments["x"] = pointf.x
        arguments["y"] = pointf.y
        arguments["originLng"] = dragOrigin!!.longitude()
        arguments["originLat"] = dragOrigin!!.latitude()
        arguments["currentLng"] = current.longitude()
        arguments["currentLat"] = current.latitude()
        arguments["eventType"] = eventType
        arguments["deltaLng"] = current.longitude() - dragPrevious!!.longitude()
        arguments["deltaLat"] = current.latitude() - dragPrevious!!.latitude()
        dragPrevious = current
        methodChannel.invokeMethod("feature#onDrag", arguments)
    }

    fun onMove(detector: MoveGestureDetector): Boolean {
        if (draggedFeature != null) {
            if (detector.pointersCount > 1) {
                stopDragging()
                return true
            }
            val pointf = detector.focalPoint
            invokeFeatureDrag(pointf, "drag")
            return false
        }
        return true
    }

    fun onMoveEnd(detector: MoveGestureDetector) {
        val pointf = detector.focalPoint
        invokeFeatureDrag(pointf, "end")
        stopDragging()
    }

    private fun startDragging(feature: Feature, origin: Point): Boolean {
        val draggable =
            if (feature.hasNonNullValueForProperty("draggable")) feature.getBooleanProperty("draggable") else false
        if (draggable) {
            draggedFeature = feature
            dragPrevious = origin
            dragOrigin = origin
            return true
        }
        return false
    }

    fun stopDragging() {
        draggedFeature = null
        dragOrigin = null
        dragPrevious = null
    }

    private inner class MoveGestureListener : OnMoveGestureListener {
        override fun onMoveBegin(detector: MoveGestureDetector): Boolean {
            return this@MapboxMapController.onMoveBegin(detector)
        }

        override fun onMove(
            detector: MoveGestureDetector,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            return this@MapboxMapController.onMove(detector)
        }

        override fun onMoveEnd(detector: MoveGestureDetector, velocityX: Float, velocityY: Float) {
            this@MapboxMapController.onMoveEnd(detector)
        }
    }

    companion object {
        private const val TAG = "MapboxMapController"
    }
}