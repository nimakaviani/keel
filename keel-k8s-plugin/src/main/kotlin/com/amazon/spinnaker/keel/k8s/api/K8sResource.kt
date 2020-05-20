package com.amazon.spinnaker.keel.k8s.api

import com.netflix.spinnaker.keel.api.ResourceSpec

typealias SpecType = Map<String, Any?>

data class K8sResourceSpec(
  val apiVersion: String,
  val kind: String,
  val spec: SpecType,
  val metadata: Map<String, Any?>
) : ResourceSpec {

  override val application: String
    get() = "test" // $apiVersion/$kind"

  override val id: String
    get() = "teet" // $apiVersion/$kind/${((spec["metadata"] as Map<String, Any?>)["name"] as String)}"

  fun location(): String {
    return metadata["namespace"]?.toString() ?: "default"
  }

  fun name(): String {
    return "$kind ${(metadata["name"] as String)}"
  }

  fun resource(): K8sResource =
    K8sResource(apiVersion, kind, metadata, spec)
}

data class K8sResource(
  val apiVersion: String,
  val kind: String,
  val metadata: Map<String, Any?>,
  val spec: SpecType
)
