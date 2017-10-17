/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 QAware GmbH, Munich, Germany
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.qaware.oss.cloud.control.oc

import de.qaware.oss.cloud.control.CloudControlLabels
import de.qaware.oss.cloud.control.ClusterOrchestrator
import de.qaware.oss.cloud.control.midi.KnobEvent
import de.qaware.oss.cloud.control.midi.LaunchControl
import de.qaware.oss.cloud.control.midi.MidiDeviceController
import io.fabric8.kubernetes.api.KubernetesHelper
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.client.OpenShiftClient
import org.apache.deltaspike.core.api.config.ConfigProperty
import org.apache.deltaspike.core.api.exclude.Exclude
import org.slf4j.Logger
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject

/**
 * The representation for our OpenShift cluster with its deployment configs.
 */
@Exclude(onExpression = "cluster.orchestrator!=openshift")
@ApplicationScoped
open class OpenShiftCluster @Inject constructor(private val client: OpenShiftClient,
                                                @ConfigProperty(name = "openshift.project")
                                                private val namespace: String,
                                                @ConfigProperty(name = "cloudcontrol.factor")
                                                private val factor: Int,
                                                private val deviceController: MidiDeviceController,
                                                private val logger: Logger) : Watcher<DeploymentConfig>, ClusterOrchestrator {
    override fun name(): String {
        return "OpenShift"
    }

    override fun display() {
        deployments.filterNotNull().forEach {
            logger.info("DeploymentConfig(${it.metadata.name}, ${it.metadata.labels})")
        }
    }

    private val deployments = Array<DeploymentConfig?>(16, { i -> null })

    @PostConstruct
    open fun init() {
        logger.info("Connecting to OpenShift URL {}.", client.masterUrl)

        val operation = client.deploymentConfigs().inNamespace(namespace)
        operation.list().items.filter {
            cloudControlEnabled(it)
        }.forEach {
            add(it)
        }

        // does not work with GCE (No HTTP 101)
        operation.watch(this)
    }

    private fun cloudControlEnabled(deployment: DeploymentConfig?): Boolean {
        val enabled = labels(deployment)[CloudControlLabels.ENABLED.label] ?: "false"
        return "true".equals(enabled, true)
    }

    private fun labels(deployment: DeploymentConfig?): Map<String, String> = KubernetesHelper.getLabels(deployment)

    private fun index(deployment: DeploymentConfig?): Int = (labels(deployment)[CloudControlLabels.INDEX.label] ?: "-1").toInt()

    private fun instances(deployment: DeploymentConfig?): Int = deployment?.spec?.replicas ?: 0

    private fun find(deployment: DeploymentConfig, firstNull: Boolean = false): Pair<Int, String> {
        val name = KubernetesHelper.getName(deployment)
        var index = index(deployment)

        if (index == -1) {
            index = if (firstNull)
                deployments.indexOfFirst { it == null }
            else
                deployments.indexOfFirst { KubernetesHelper.getName(it) == name }
        }

        return Pair(index, name)
    }

    private fun add(deployment: DeploymentConfig) {
        val (index, name) = find(deployment, true)

        logger.info("Adding OpenShift DeploymentConfig {} at index {}.", name, index)
        deployments[index] = deployment
        display(index, deployment)
    }

    private fun modify(deployment: DeploymentConfig) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Modifying OpenShift DeploymentConfig {} at index {}.", name, index)
            deployments[index] = deployment
            display(index, deployment)
        }
    }

    private fun delete(deployment: DeploymentConfig) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Removing OpenShift DeploymentConfig {} at index {}.", name, index)
            deployments[index] = null
            deviceController.off(index)
        }
    }

    private fun failure(deployment: DeploymentConfig) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Error OpenShift DeploymentConfig {} at index {}.", name, index)
            deployments[index] = null
            deviceController.failure(index)
        }
    }

    private fun display(index: Int, deployment: DeploymentConfig) {
        val instances = instances(deployment)
        if (instances == 0) {
            deviceController.disable(index)
        } else {
            deviceController.enable(index)
        }
    }

    open fun scale(@Observes event: KnobEvent) {
        // currently we only support the first row of knobs
        if (event.row != 1) return

        // now get and check the deployment
        val index = if (event.channel == LaunchControl.Channel.USER) event.index + 8 else event.index
        if (deployments[index] == null) return

        val name = KubernetesHelper.getName(deployments[index])
        // maybe make the factor configurable later
        val replicas = (event.value / factor) + 1

        logger.info("Scaling DeploymentConfig {} to {} replicas.", name, replicas)

        synchronized(client) {
            deployments[index] = client.deploymentConfigs()
                    .inNamespace(namespace)
                    .withName(name)
                    .edit()
                    .editSpec()
                    .withReplicas(replicas)
                    .endSpec()
                    .done()
        }
    }

    override fun eventReceived(action: Watcher.Action?, deployment: DeploymentConfig?) {
        when (action) {
            Watcher.Action.ADDED -> {
                if (cloudControlEnabled(deployment)) {
                    add(deployment!!)
                }
            }

            Watcher.Action.MODIFIED -> {
                if (cloudControlEnabled(deployment)) {
                    modify(deployment!!)
                }
            }

            Watcher.Action.DELETED -> {
                if (cloudControlEnabled(deployment)) {
                    delete(deployment!!)
                }
            }

            Watcher.Action.ERROR -> {
                if (cloudControlEnabled(deployment)) {
                    failure(deployment!!)
                }
            }
        }
    }

    override fun onClose(cause: KubernetesClientException?) {
        logger.info("Reconnecting to OpenShift master {} due to {}.", client.masterUrl, cause)
        for (i in deployments.indices) deployments[i] = null
        init()
    }
}