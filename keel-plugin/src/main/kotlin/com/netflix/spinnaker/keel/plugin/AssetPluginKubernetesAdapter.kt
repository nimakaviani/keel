/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import com.google.gson.reflect.TypeToken
import com.netflix.spinnaker.keel.api.Asset
import com.squareup.okhttp.Call
import io.kubernetes.client.apis.ApiextensionsV1beta1Api
import io.kubernetes.client.apis.CustomObjectsApi
import io.kubernetes.client.models.V1beta1CustomResourceDefinition
import io.kubernetes.client.util.Config
import io.kubernetes.client.util.Watch
import io.kubernetes.client.util.Watch.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import java.net.SocketException
import kotlin.reflect.KClass

internal class AssetPluginKubernetesAdapter(
  private val extensionsApi: ApiextensionsV1beta1Api,
  private val customObjectsApi: CustomObjectsApi,
  private val plugin: AssetPlugin
) {
  private var job: Job? = null
  private var calls: MutableMap<String, Call> = mutableMapOf()

  fun start() {
    runBlocking {
      launch {
        if (job != null) throw IllegalStateException("Watcher for ${plugin.name} already running")
        job = GlobalScope.launch {
          for ((name, type) in plugin.supportedKinds) {
            val crd = extensionsApi
              .readCustomResourceDefinition(name, "true", null, null)
            watchForResourceChanges(crd, type)
          }
        }
      }
        .join()
      log.debug("All CRDs are registered")
    }
  }

  fun stop() {
    runBlocking {
      job?.cancel()
      calls.forEach { _, call -> call.cancel() }
      job?.join()
    }
  }

  private suspend fun <T : Any> CoroutineScope.watchForResourceChanges(
    crd: V1beta1CustomResourceDefinition,
    type: KClass<T>
  ) {
    var seen = 0L
    while (isActive) {
      val call = customObjectsApi.listClusterCustomObjectCall(
        crd.spec.group,
        crd.spec.version,
        crd.spec.names.plural,
        "true",
        null,
        "0", // TODO: this should update based on `seen`
        true,
        null,
        null
      )
      calls[crd.metadata.name] = call
      try {
        val watch: Watch<Asset<T>> = call.createResourceWatch(type)
        watch?.use { watch ->
          watch.forEach {
            log.info("Event {} on {}", it.type, it.`object`)
            log.info("Event {} on {} v{}, last seen {}", it.type, it.`object`.metadata.name, it.`object`.metadata.resourceVersion, seen)
            val version = it.`object`.metadata.resourceVersion ?: 0L
            if (version > seen) {
              seen = version
              when (it.type) {
                "ADDED" -> plugin.create(it.`object`)
                "MODIFIED" -> plugin.update(it.`object`)
                "DELETED" -> plugin.delete(it.`object`)
              }
            }
          }
        }
      } catch (e: Exception) {
        if (e.cause is SocketException) {
          log.debug("Socket timed out or call was cancelled.")
        } else {
          throw e
        }
      }
      yield()
    }
  }

  /**
   * Gets a reified type token for the watch response. This looks complex but
   * `T` is a runtime type and gets erased before the JSON parser can reference
   * it otherwise.
   */
  private fun <T : Any> Call.createResourceWatch(type: KClass<T>): Watch<Asset<T>> =
    TypeToken.getParameterized(Asset::class.java, type.java).type
      .let { TypeToken.getParameterized(Watch.Response::class.java, it).type }
      .also { log.info("Watching for $it") }
      .let { createWatch<Asset<T>>(Config.defaultClient(), this, it) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
