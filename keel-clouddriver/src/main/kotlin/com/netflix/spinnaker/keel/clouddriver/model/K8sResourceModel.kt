package com.netflix.spinnaker.keel.clouddriver.model

data class K8sResourceModel(
  val apiVersion: String,
  val kind: String,
  val metadata: Map<String, Any?>,
  val spec: Map<String, Any?>
)
