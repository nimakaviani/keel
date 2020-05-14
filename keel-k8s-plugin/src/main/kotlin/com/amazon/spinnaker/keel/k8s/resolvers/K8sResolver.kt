package com.amazon.spinnaker.keel.k8s.resolvers

import com.amazon.spinnaker.keel.config.K8S_RESOURCE_SPEC
import com.amazon.spinnaker.keel.k8s.api.K8sResourceSpec
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.plugins.Resolver
import org.springframework.stereotype.Component

@Component
class K8sResolver() : Resolver<K8sResourceSpec> {
  override val supportedKind = K8S_RESOURCE_SPEC

  override fun invoke(p1: Resource<K8sResourceSpec>): Resource<K8sResourceSpec> {
//    TODO("Not yet implemented")
    return p1
  }
}
