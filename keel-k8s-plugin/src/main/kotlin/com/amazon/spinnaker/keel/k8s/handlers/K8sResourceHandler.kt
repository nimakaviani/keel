package com.amazon.spinnaker.keel.k8s.handlers

import com.amazon.spinnaker.keel.config.K8S_RESOURCE_SPEC
import com.amazon.spinnaker.keel.k8s.api.K8sResource
import com.amazon.spinnaker.keel.k8s.api.K8sResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import kotlinx.coroutines.coroutineScope

class K8sResournceHandler(
  private val cloudDriverService: CloudDriverService,
//  private val cloudDriverCache: CloudDriverCache,
//  private val orcaService: OrcaService,
//  private val taskLauncher: TaskLauncher,
  resolvers: List<Resolver<*>>
) : ResourceHandler<K8sResourceSpec, Any>(resolvers) {

  override val supportedKind = K8S_RESOURCE_SPEC

  override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sResource =
    with(resource.spec) {
      K8sResource(
        apiVersion = (((resource.spec as Map<String, Any?>)["apiVersion"]) as String),
        kind = (((resource.spec as Map<String, Any?>)["kind"]) as String),
        metadata = (resource.spec as Map<String, Any?>)["metadata"],
        spec = ((resource.spec as Map<String, Any?>)["spec"] as Map<String, Any?>)
      )
    }

  override suspend fun current(resource: Resource<K8sResourceSpec>): Any? =
    cloudDriverService.getK8sResource(
      (((resource.spec as Map<String, Any?>)["apiVersion"]) as String),
      (((resource.spec as Map<String, Any?>)["kind"]) as String)
    )

  private suspend fun CloudDriverService.getK8sResource(
    apiVersion: String,
    kind: String
  ): List<K8sResource> =
    coroutineScope {
        try {
          listOf(K8sResource(
            apiVersion = apiVersion,
            kind = kind,
            metadata = "random",
            spec = mapOf(Pair(first = "random", second = "random"))
          ))
        } catch (e: Exception) {
          throw e
        }
    }
}
