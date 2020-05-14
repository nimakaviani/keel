package com.amazon.spinnaker.keel.config

import com.amazon.spinnaker.keel.k8s.api.K8sResourceSpec
import com.netflix.spinnaker.keel.api.plugins.kind

val K8S_RESOURCE_SPEC = kind <K8sResourceSpec>("k8s/resource@v1")
