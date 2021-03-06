package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.deb
import com.netflix.spinnaker.keel.api.artifacts.ArtifactType.docker
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.comparator
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ArtifactSyncEvent
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.kork.exceptions.IntegrationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ArtifactListener(
  private val repository: KeelRepository,
  private val artifactService: ArtifactService,
  private val clouddriverService: CloudDriverService,
  private val publisher: ApplicationEventPublisher
) {

  @EventListener(ArtifactEvent::class)
  fun onArtifactEvent(event: ArtifactEvent) {
    log.debug("Received artifact event: {}", event)
    event
      .artifacts
      .filter { it.type.toLowerCase() in artifactTypeNames }
      .forEach { artifact ->
        if (repository.isRegistered(artifact.name, artifact.type())) {
          val version: String
          var status: ArtifactStatus? = null
          when (artifact.type()) {
            deb -> {
              version = "${artifact.name}-${artifact.version}"
              status = debStatus(artifact)
            }
            docker -> {
              version = artifact.version
            }
          }
          log.info("Registering version {} ({}) of {} {}", version, status, artifact.name, artifact.type)
          repository.storeArtifact(artifact.name, artifact.type(), version, status)
            .also { wasAdded ->
              if (wasAdded) {
                publisher.publishEvent(ArtifactVersionUpdated(artifact.name, artifact.type()))
              }
            }
        }
      }
  }

  /**
   * Fetch latest version of an artifact after it is registered.
   */
  @EventListener(ArtifactRegisteredEvent::class)
  fun onArtifactRegisteredEvent(event: ArtifactRegisteredEvent) {
    val artifact = event.artifact

    if (repository.artifactVersions(artifact).isEmpty()) {
      when (artifact) {
        is DebianArtifact -> storeLatestDebVersion(artifact)
        is DockerArtifact -> storeLatestDockerVersion(artifact)
      }
    }
  }

  @EventListener(ArtifactSyncEvent::class)
  fun triggerDebSync(event: ArtifactSyncEvent) {
    if (event.controllerTriggered) {
      log.info("Fetching latest version of all registered artifacts...")
    }
    syncArtifactVersions()
  }

  /**
   * For each registered debian artifact, get the last version, and persist if it's newer than what we have.
   */
  // todo eb: should we fetch more than one version?
  @Scheduled(fixedDelayString = "\${keel.artifact-refresh.frequency:PT6H}")
  fun syncArtifactVersions() =
    runBlocking {
      repository.getAllArtifacts().forEach { artifact ->
        launch {
          val lastRecordedVersion = getLatestStoredVersion(artifact)
          val latestVersion = when (artifact) {
            is DebianArtifact -> getLatestDeb(artifact)?.let { "${artifact.name}-$it" }
            is DockerArtifact -> getLatestDockerTag(artifact)
          }
          if (latestVersion != null) {
            val hasNew = when {
              lastRecordedVersion == null -> true
              latestVersion != lastRecordedVersion -> {
                artifact.versioningStrategy.comparator.compare(lastRecordedVersion, latestVersion) > 0
              }
              else -> false
            }

            if (hasNew) {
              log.debug("Artifact {} has a missing version {}, persisting..", artifact, latestVersion)
              val status = when (artifact.type) {
                deb -> debStatus(artifactService.getArtifact(artifact.name, latestVersion.removePrefix("${artifact.name}-")))
                // todo eb: is there a better way to think of docker status?
                docker -> null
              }
              repository.storeArtifact(artifact.name, artifact.type, latestVersion, status)
            }
          }
        }
      }
    }

  private fun getLatestStoredVersion(artifact: DeliveryArtifact): String? =
    repository.artifactVersions(artifact).sortedWith(artifact.versioningStrategy.comparator).firstOrNull()

  private suspend fun getLatestDeb(artifact: DebianArtifact): String? =
    artifactService.getVersions(artifact.name).firstOrNull()

  private suspend fun getLatestDockerTag(artifact: DockerArtifact): String? {
    val serviceAccount = artifact.deliveryConfigName?.let { repository.getDeliveryConfig(it) }
      ?.serviceAccount
      ?: DEFAULT_SERVICE_ACCOUNT
    return clouddriverService
      .findDockerTagsForImage("*", artifact.name, serviceAccount)
      .distinct()
      .sortedWith(artifact.versioningStrategy.comparator)
      .firstOrNull()
  }

  /**
   * Grab the latest version which matches the statuses we care about, so the artifact is relevant.
   */
  protected fun storeLatestDebVersion(artifact: DebianArtifact) =
    runBlocking {
      getLatestDeb(artifact)
        ?.let { firstVersion ->
          val version = "${artifact.name}-$firstVersion"
          val status = debStatus(artifactService.getArtifact(artifact.name, firstVersion))
          log.debug("Storing latest version {} ({}) for registered artifact {}", version, status, artifact)
          repository.storeArtifact(artifact.name, artifact.type, version, status)
        }
    }

  /**
   * Grabs the latest tag and stores it.
   */
  protected fun storeLatestDockerVersion(artifact: DockerArtifact) =
    runBlocking {
      getLatestDockerTag(artifact)
        ?.let { firstVersion ->
          log.debug("Storing latest version {} for registered artifact {}", firstVersion, artifact)
          repository.storeArtifact(artifact.name, artifact.type, firstVersion, null)
        }
    }

  /**
   * Parses the status from a kork artifact, and throws an error if [releaseStatus] isn't
   * present in [metadata]
   */
  private fun debStatus(artifact: Artifact): ArtifactStatus {
    val status = artifact.metadata["releaseStatus"]?.toString()
      ?: throw IntegrationException("Artifact event received without 'releaseStatus' field")
    return ArtifactStatus.valueOf(status)
  }

  private fun Artifact.type() = ArtifactType.valueOf(type.toLowerCase())

  private val artifactTypeNames by lazy { ArtifactType.values().map(ArtifactType::name) }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
