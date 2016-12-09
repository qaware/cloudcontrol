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
package de.qaware.oss.cloud.control.k8s

import de.qaware.oss.cloud.control.CloudControlLabels
import de.qaware.oss.cloud.control.midi.KnobEvent
import de.qaware.oss.cloud.control.midi.LaunchControl.Channel
import de.qaware.oss.cloud.control.midi.MidiDeviceController
import io.fabric8.kubernetes.api.KubernetesHelper
import io.fabric8.kubernetes.api.model.extensions.Deployment
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.Watcher
import org.apache.deltaspike.core.api.config.ConfigProperty
import org.apache.deltaspike.core.api.exclude.Exclude
import org.slf4j.Logger
import javax.annotation.PostConstruct
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.inject.Inject

/**
 * The representation for our Kubernetes cluster with its deployments.
 * Takes care of interacting with the K8S REST API on MIDI device events
 * and keeping the current cluster state.
 */
@Exclude(onExpression = "cluster.orchestrator!=kubernetes")
@ApplicationScoped
open class KubernetesCluster @Inject constructor(private val client: KubernetesClient,
                                                 @ConfigProperty(name = "kubernetes.namespace")
                                                 private val namespace: String,
                                                 private val deviceController: MidiDeviceController,
                                                 private val logger: Logger) : Watcher<Deployment> {

    private val deployments = Array<Deployment?>(16, { i -> null })

    @PostConstruct
    open fun init() {
        logger.info("Connecting to K8s master {}.", client.masterUrl)

        val operation = client.extensions().deployments().inNamespace(namespace)
        operation.list().items.filter {
            cloudControlEnabled(it)
        }.forEach {
            add(it)
        }

        // does not work with GCE (No HTTP 101)
        operation.watch(this)
    }

    private fun cloudControlEnabled(deployment: Deployment?): Boolean {
        val enabled = labels(deployment)[CloudControlLabels.ENABLED.label] ?: "false"
        return "true".equals(enabled, true)
    }

    private fun labels(deployment: Deployment?): Map<String, String> = KubernetesHelper.getLabels(deployment)

    private fun index(deployment: Deployment?): Int = (labels(deployment)[CloudControlLabels.INDEX.label] ?: "-1").toInt()

    private fun instances(deployment: Deployment?): Int = deployment?.spec?.replicas ?: 0

    private fun find(deployment: Deployment, firstNull: Boolean = false): Pair<Int, String> {
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

    private fun add(deployment: Deployment) {
        val (index, name) = find(deployment, true)

        logger.info("Adding K8s deployment {} at index {}.", name, index)
        deployments[index] = deployment
        display(index, deployment)
    }

    private fun modify(deployment: Deployment) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Modifying K8s deployment {} at index {}.", name, index)
            deployments[index] = deployment
            display(index, deployment)
        }
    }

    private fun delete(deployment: Deployment) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Removing K8s deployment {} at index {}.", name, index)
            deployments[index] = null
            deviceController.off(index)
        }
    }

    private fun failure(deployment: Deployment) {
        val (index, name) = find(deployment)

        if (index > -1) {
            logger.info("Error K8s deployment {} at index {}.", name, index)
            deployments[index] = null
            deviceController.failure(index)
        }
    }

    private fun display(index: Int, deployment: Deployment) {
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
        val index = if (event.channel == Channel.USER) event.index + 8 else event.index
        if (deployments[index] == null) return

        val name = KubernetesHelper.getName(deployments[index])
        // maybe make the factor configurable later
        val replicas = event.value * .1

        synchronized(client) {
            deployments[index] = client.extensions().deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .edit()
                    .editSpec()
                    .withReplicas(replicas.toInt())
                    .endSpec()
                    .done()
        }
    }

    override fun eventReceived(action: Watcher.Action?, deployment: Deployment?) {
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
        logger.info("Reconnecting to K8s master {} due to {}.", client.masterUrl, cause)
        for (i in deployments.indices) deployments[i] = null
        init()
    }
}