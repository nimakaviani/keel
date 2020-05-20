package com.amazon.spinnaker.keel.k8s.handlers

import com.amazon.spinnaker.keel.config.K8S_PROVIDER
import com.amazon.spinnaker.keel.config.K8S_RESOURCE_SPEC_V1
import com.amazon.spinnaker.keel.config.SOURCE_TYPE
import com.amazon.spinnaker.keel.k8s.api.K8sResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceDiff
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.api.plugins.ResourceHandler
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.K8sResourceModel
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

class K8sResournceHandler(
  private val cloudDriverService: CloudDriverService,
//  private val cloudDriverCache: CloudDriverCache,
//  private val orcaService: OrcaService,
  private val taskLauncher: TaskLauncher,
  resolvers: List<Resolver<*>>
) : ResourceHandler<K8sResourceSpec, K8sResourceSpec>(resolvers) {

  override val supportedKind = K8S_RESOURCE_SPEC_V1

  override suspend fun toResolvedType(resource: Resource<K8sResourceSpec>): K8sResourceSpec =
    K8sResourceSpec(
      apiVersion = resource.spec.apiVersion,
      kind = resource.spec.kind,
      spec = resource.spec.spec,
      metadata = resource.spec.metadata
    )

  override suspend fun current(resource: Resource<K8sResourceSpec>): K8sResourceSpec? =
    cloudDriverService.getK8sResource(
      resource.spec,
      resource.metadata["account"] as String,
      resource.serviceAccount
    )

  private suspend fun CloudDriverService.getK8sResource(
    resource: K8sResourceSpec,
    account: String,
    serviceAccount: String
  ): K8sResourceSpec? =
    coroutineScope {
        try {
          getK8sResource(
            serviceAccount,
            account,
            resource.location(),
            resource.name()
          ).toResourceModel()
        } catch (e: HttpException) {
          if (e.isNotFound) {
            null
          } else {
            throw e
          }
        }
    }

  private fun K8sResourceModel.toResourceModel() =
    K8sResourceSpec(
      apiVersion = manifest.apiVersion,
      kind = manifest.kind,
      metadata = manifest.metadata,
      spec = manifest.spec
    )

  override suspend fun upsert(
    resource: Resource<K8sResourceSpec>,
    diff: ResourceDiff<K8sResourceSpec>
  ): List<Task> {

    if (!diff.hasChanges()) {
      return listOf<Task>()
    }

    val spec = (diff.desired)
    val account = (resource.metadata["account"] as String)

    return listOf(
      taskLauncher.submitJob(
        resource = resource,
        description = "k8s resource: ${spec.name()} ",
        correlationId = spec.name(),
        job = spec.job(resource.application, account)
      )
    )
  }

  private fun K8sResourceSpec.job(app: String, account: String): Job =
    Job(
      "deployManifest",
      mapOf(
        "moniker" to mapOf(
          "app" to app,
          "location" to location()
        ),
        "application" to app,
        "cloudProvider" to K8S_PROVIDER,
        "credentials" to account,
        "manifests" to listOf(this.resource()),
        "optionalArtifacts" to listOf<Map<Object, Object>>(),
        "requiredArtifacts" to listOf<Map<String, Any?>>(),
        "source" to SOURCE_TYPE,
        "enableTraffic" to true.toString()
      )
    )
}
