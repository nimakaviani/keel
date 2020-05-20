package com.amazon.spinnaker.keel.config

import com.amazon.spinnaker.keel.k8s.handlers.K8sResournceHandler
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.plugins.Resolver
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("keel.plugins.k8s.enabled")
class K8sConfig {

  @Bean
  fun k8sResourceV1() = K8S_RESOURCE_SPEC_V1

  @Bean
  fun k8sResourceHandler(
    cloudDriverService: CloudDriverService,
    taskLauncher: TaskLauncher,
    normalizers: List<Resolver<*>>
  ): K8sResournceHandler =
    K8sResournceHandler(
      cloudDriverService,
      taskLauncher,
      normalizers
    )
}
