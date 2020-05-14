package com.amazon.spinnaker.keel.k8s.api

import com.netflix.spinnaker.keel.api.ResourceSpec

data class K8sResourceSpec(
  val metadata: Map<String, Any?>,
  val spec: Map<String, Any?>
) : ResourceSpec {

  override val application: String
    get() = "test"
//    get() = "${spec["apiVersion"]}/${spec["kind"]}"

  override val id: String
    get() = "test"
//    get() = "${spec["apiVersion"]}/${spec["kind"]}" // /${(spec["metadata"] as Map<String, Any?>)["name"]}"
}

data class K8sResource(
  val apiVersion: String,
  val kind: String,
  val metadata: Any?,
  val spec: Map<String, Any?>
)
