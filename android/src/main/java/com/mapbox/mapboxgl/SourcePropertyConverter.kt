package com.mapbox.mapboxgl

import android.net.Uri
import com.mapbox.mapboxgl.Convert.toList
import com.mapbox.mapboxgl.Convert.toFloat
import com.mapbox.mapboxgl.Convert.toString
import com.mapbox.mapboxgl.Convert.toInt
import com.mapbox.mapboxgl.Convert.toBoolean
import com.mapbox.mapboxgl.Convert.toLatLngList
import com.mapbox.mapboxgl.SourcePropertyConverter
import com.google.gson.Gson
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngQuad
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.sources.*
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.sources.Source
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.RasterDemSource
import com.mapbox.maps.extension.style.sources.generated.RasterSource
import com.mapbox.maps.extension.style.sources.generated.VectorSource
import java.net.URI
import java.net.URISyntaxException
import java.util.ArrayList

internal object SourcePropertyConverter {
    private const val TAG = "SourcePropertyConverter"
    fun buildTileset(data: Map<String?, Any?>): TileSet? {
        val tiles = data["tiles"] ?: return null

        // options are only valid with tiles
        val tileSet = TileSet("2.1.0", *toList(tiles)?.toTypedArray() as Array<String?>)
        val bounds = data["bounds"]
        if (bounds != null) {
            val boundsFloat: MutableList<Float> = ArrayList()
            for (item in toList(bounds)!!) {
                boundsFloat.add(toFloat(item))
            }
            tileSet.setBounds(*boundsFloat.toTypedArray())
        }
        val scheme = data["scheme"]
        if (scheme != null) {
            tileSet.scheme = toString(scheme)
        }
        val minzoom = data["minzoom"]
        if (minzoom != null) {
            tileSet.minZoom = toFloat(minzoom)
        }
        val maxzoom = data["maxzoom"]
        if (maxzoom != null) {
            tileSet.maxZoom = toFloat(maxzoom)
        }
        val attribution = data["attribution"]
        if (attribution != null) {
            tileSet.attribution = toString(attribution)
        }
        return tileSet
    }

    fun buildGeojsonOptions(data: Map<String?, Any?>): GeoJsonOptions {
        var options = GeoJsonOptions()
        val buffer = data["buffer"]
        if (buffer != null) {
            options = options.withBuffer(toInt(buffer))
        }
        val cluster = data["cluster"]
        if (cluster != null) {
            options = options.withCluster(toBoolean(cluster))
        }
        val clusterMaxZoom = data["clusterMaxZoom"]
        if (clusterMaxZoom != null) {
            options = options.withClusterMaxZoom(toInt(clusterMaxZoom))
        }
        val clusterRadius = data["clusterRadius"]
        if (clusterRadius != null) {
            options = options.withClusterRadius(toInt(clusterRadius))
        }
        val lineMetrics = data["lineMetrics"]
        if (lineMetrics != null) {
            options = options.withLineMetrics(toBoolean(lineMetrics))
        }
        val maxZoom = data["maxZoom"]
        if (maxZoom != null) {
            options = options.withMaxZoom(toInt(maxZoom))
        }
        val minZoom = data["minZoom"]
        if (minZoom != null) {
            options = options.withMinZoom(toInt(minZoom))
        }
        val tolerance = data["tolerance"]
        if (tolerance != null) {
            options = options.withTolerance(toFloat(tolerance))
        }
        return options
    }

    fun buildGeojsonSource(id: String?, properties: Map<String?, Any?>): GeoJsonSource? {
        val data = properties["data"]
        val options = buildGeojsonOptions(properties)
        if (data != null) {
            if (data is String) {
                try {
                    val uri = URI(toString(data))
                    return GeoJsonSource(id, uri, options)
                } catch (e: URISyntaxException) {
                }
            } else {
                val gson = Gson()
                val geojson = gson.toJson(data)
                val featureCollection = FeatureCollection.fromJson(geojson)
                return GeoJsonSource(id, featureCollection, options)
            }
        }
        return null
    }

    fun buildImageSource(id: String?, properties: Map<String?, Any?>): ImageSource? {
        val url = properties["url"]
        val coordinates = toLatLngList(properties["coordinates"], true)
        val quad = LatLngQuad(
            coordinates!![0], coordinates[1], coordinates[2], coordinates[3]
        )
        try {
            val uri = URI(toString(url!!))
            return ImageSource(id, quad, uri)
        } catch (e: URISyntaxException) {
        }
        return null
    }

    private fun buildVectorSource(id: String, properties: Map<String?, Any?>): VectorSource? {
        val url = properties["url"]
        if (url != null) {
            return VectorSource.Builder(id).url(toString(url)).build()
        }
        val tileSet = buildTileset(properties)
        if (tileSet != null) {
            return  VectorSource.Builder(id).tileSet(tileSet).build()
        }
        return null
    }

    private fun buildRasterSource(id: String, properties: Map<String?, Any?>): RasterSource? {
        val url = properties["url"]
        if (url != null) {
            try {
                return RasterSource.Builder(id).url(toString(url)).build()
            } catch (e: URISyntaxException) {
            }
        }
        val tileSet = buildTileset(properties)
        if (tileSet != null) {
            return RasterSource.Builder(id).tileSet(tileSet).build()
        }
        return null
    }

    private fun buildRasterDemSource(id: String, properties: Map<String?, Any?>): RasterDemSource? {
        val url = properties["url"]
        if (url != null) {
            try {
                return RasterDemSource.Builder(id).url(toString(url)).build()
            } catch (e: URISyntaxException) {
            }
        }
        val tileSet = buildTileset(properties)
        if (tileSet != null) {
            return  RasterDemSource.Builder(id).tileSet(tileSet).build()
        }
        return  null
    }

    fun addSource(id: String, properties: Map<String?, Any?>, style: Style) {
        val type = properties["type"]
        var source: Source? = null
        if (type != null) {
            when (toString(type)) {
                "vector" -> source = buildVectorSource(id, properties)
                "raster" -> source = buildRasterSource(id, properties)
                "raster-dem" -> source = buildRasterDemSource(id, properties)
                "image" -> source = buildImageSource(id, properties)
                "geojson" -> source = buildGeojsonSource(id, properties)
                else -> {}
            }
        }
        if (source != null) {
            style.addSource(source)
        }
    }
}