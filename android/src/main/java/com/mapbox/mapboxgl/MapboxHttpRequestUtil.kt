//package com.mapbox.mapboxgl
//
//import io.flutter.plugin.common.MethodChannel
//import com.mapbox.mapboxsdk.module.http.HttpRequestUtil
//import okhttp3.Interceptor
//import okhttp3.Request
//import java.lang.Exception
//import java.lang.RuntimeException
//
//internal object MapboxHttpRequestUtil {
//    fun setHttpHeaders(headers: Map<String?, String?>, result: MethodChannel.Result) {
////        HttpRequestUtil.setOkHttpClient(getOkHttpClient(headers, result).build())
//        result.success(null)
//    }
//
//    private fun getOkHttpClient(
//        headers: Map<String?, String?>, result: MethodChannel.Result
//    ): OkHttpClient.Builder {
//        return try {
//            okhttp3.OkHttpClient.Builder()
//                .addNetworkInterceptor(
//                    Interceptor { chain: Interceptor.Chain ->
//                        val builder: Request.Builder = chain.request().newBuilder()
//                        for ((key, value) in headers) {
//                            if (key == null || key.trim { it <= ' ' }.isEmpty()) {
//                                continue
//                            }
//                            if (value == null || value.trim { it <= ' ' }.isEmpty()) {
//                                builder.removeHeader(key)
//                            } else {
//                                builder.header(key, value)
//                            }
//                        }
//                        chain.proceed(builder.build())
//                    })
//        } catch (e: Exception) {
//            result.error(
//                "OK_HTTP_CLIENT_ERROR",
//                "An unexcepted error happened during creating http " + "client" + e.message,
//                null
//            )
//            throw RuntimeException(e)
//        }
//    }
//}