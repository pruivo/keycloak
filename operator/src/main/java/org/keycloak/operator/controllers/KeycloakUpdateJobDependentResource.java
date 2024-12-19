/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.operator.controllers;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerFluent;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.api.model.PodTemplateSpecFluent;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecFluent;
import io.fabric8.zjsonpatch.JsonDiff;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfigBuilder;
import io.quarkus.logging.Log;
import org.keycloak.operator.Constants;
import org.keycloak.operator.Utils;
import org.keycloak.operator.crds.v2alpha1.CRDUtils;
import org.keycloak.operator.crds.v2alpha1.deployment.Keycloak;

public class KeycloakUpdateJobDependentResource extends CRUDKubernetesDependentResource<Job, Keycloak> {

    private static final String OLD_DEPLOYMENT_KEY = "update_old_stateful_set";
    private static final String NEW_DEPLOYMENT_KEY = "update_new_stateful_set";
    private static final String UPDATE_STRATEGY_KEY = "update_strategy";

    private static final String INIT_CONTAINER_NAME = "actual";
    private static final String CONTAINER_NAME = "desired";
    private static final List<String> INIT_CONTAINER_ARGS = List.of("update", "save");
    private static final List<String> CONTAINER_ARGS = List.of("update", "check");

    private static final String PATH = "path";
    // Container fields that require the update job if changed.
    private static final Collection<String> FIELDS = Set.of("/env", "/envFrom", "/image");

    public KeycloakUpdateJobDependentResource() {
        super(Job.class);
        this.configureWith(new KubernetesDependentResourceConfigBuilder<Job>()
                .withLabelSelector(Constants.DEFAULT_LABELS_AS_STRING)
                .build());
    }

    @Override
    protected Job desired(Keycloak primary, Context<Keycloak> context) {
        var builder = new JobBuilder();
        addMetadata(builder, primary);
        var specBuilder = builder.withNewSpec();
        addPodSpecTemplate(specBuilder, primary, context);
        // we don't need retries; we use exit code != 1 to signal the upgrade decision.
        specBuilder.withBackoffLimit(0);
        specBuilder.endSpec();
        return builder.build();
    }

    private static String jobName(Keycloak keycloak) {
        return keycloak.getMetadata().getName() + "-update-job";
    }

    private static String podName(Keycloak keycloak) {
        return keycloak.getMetadata().getName() + "-update-pod";
    }

    private static void addMetadata(JobBuilder builder, Keycloak keycloak) {
        builder.withNewMetadata()
                .withName(jobName(keycloak))
                .withNamespace(keycloak.getMetadata().getNamespace())
                .withLabels(Utils.allInstanceLabels(keycloak))
                .endMetadata();
    }

    private static void addMetadata(PodTemplateSpecFluent<?> builder, Keycloak keycloak) {
        builder.withNewMetadata()
                .withName(podName(keycloak))
                .withNamespace(keycloak.getMetadata().getNamespace())
                .withLabels(Utils.allInstanceLabels(keycloak))
                .endMetadata();
    }

    private static void addPodSpecTemplate(JobSpecFluent<?> builder, Keycloak keycloak, Context<Keycloak> context) {
        var podTemplate = builder.withNewTemplate();
        addMetadata(podTemplate, keycloak);
        podTemplate.withSpec(createPodSpec(context));
        podTemplate.endTemplate();
    }

    private static PodSpec createPodSpec(Context<Keycloak> context) {
        var builder = new PodSpecBuilder();
        builder.withRestartPolicy("Never");
        addInitContainer(builder, context);
        addContainer(builder, context);
        return builder.build();
    }

    private static void addInitContainer(PodSpecBuilder builder, Context<Keycloak> context) {
        var existing = getOldDeployment(context)
                .flatMap(CRDUtils::firstContainerOf)
                .orElseThrow();
        var containerBuilder = builder.addNewInitContainerLike(existing);
        configureContainer(containerBuilder, INIT_CONTAINER_NAME, INIT_CONTAINER_ARGS);
        containerBuilder.endInitContainer();
    }

    private static void addContainer(PodSpecBuilder builder, Context<Keycloak> context) {
        var existing = getNewDeployment(context)
                .flatMap(CRDUtils::firstContainerOf)
                .orElseThrow();
        var containerBuilder = builder.addNewContainerLike(existing);
        configureContainer(containerBuilder, CONTAINER_NAME, CONTAINER_ARGS);
        containerBuilder.endContainer();
    }

    private static void configureContainer(ContainerFluent<?> containerBuilder, String name, List<String> args) {
        containerBuilder.withName(name);
        containerBuilder.withArgs(args);
        // remove volumes, won't be used
        containerBuilder.withVolumeDevices();
        containerBuilder.withVolumeMounts();
        // remove restart policy and probes
        containerBuilder.withRestartPolicy(null);
        containerBuilder.withReadinessProbe(null);
        containerBuilder.withLivenessProbe(null);
        containerBuilder.withStartupProbe(null);
    }

    public static void setOldDeployment(Context<Keycloak> context, StatefulSet oldDeployment) {
        context.managedDependentResourceContext().put(OLD_DEPLOYMENT_KEY, oldDeployment);
    }

    public static void setNewDeployment(Context<Keycloak> context, StatefulSet oldDeployment) {
        context.managedDependentResourceContext().put(NEW_DEPLOYMENT_KEY, oldDeployment);
    }

    public static Optional<StatefulSet> getOldDeployment(Context<Keycloak> context) {
        return context.managedDependentResourceContext().get(OLD_DEPLOYMENT_KEY, StatefulSet.class);
    }

    private static Optional<StatefulSet> getNewDeployment(Context<Keycloak> context) {
        return context.managedDependentResourceContext().get(NEW_DEPLOYMENT_KEY, StatefulSet.class);
    }

    public static boolean isJobRunning(Job job, Context<Keycloak> context) {
        var status = job.getStatus();
        Log.infof("Job Status:%n%s", CRDUtils.toJsonNode(status, context).toPrettyString());
        var completed = Optional.ofNullable(status).stream()
                .mapMultiToInt((jobStatus, downstream) -> {
                    if (jobStatus.getSucceeded() != null) {
                        downstream.accept(jobStatus.getSucceeded());
                    }
                    if (jobStatus.getFailed() != null) {
                        downstream.accept(jobStatus.getFailed());
                    }
                }).sum();
        // we only have a single pod, so completed will be zero if running or 1 if finished.
        return completed == 0;
    }

    public static boolean requiresUpdateJob(Container actual, Container desired, Context<Keycloak> context) {
        var actualJson = CRDUtils.toJsonNode(actual, context);
        var desiredJson = CRDUtils.toJsonNode(desired, context);
        var diff = JsonDiff.asJson(actualJson, desiredJson);

        Log.debugf("Container diff outcome:%n%s", diff.toPrettyString());

        for (int i = 0; i < diff.size(); i++) {
            var path = diff.get(i).get(PATH).asText();
            if (FIELDS.contains(path)) {
                Log.infof("Found different value:%n%s", diff.get(i).toPrettyString());
                return true;
            }
        }
        return false;
    }

    public static Optional<Pod> findPodForJob(Job job, Context<Keycloak> context) {
        return context.getClient().pods()
                .inNamespace(job.getMetadata().getNamespace())
                .withLabelSelector(Objects.requireNonNull(job.getSpec().getSelector()))
                .list()
                .getItems()
                .stream()
                .findFirst();
    }

    public static void computeDecision(Pod pod, Context<?> context) {
        // check init container.
        var initContainerExitCode = initContainer(pod)
                .map(KeycloakUpdateJobDependentResource::exitCode);
        if (initContainerExitCode.isEmpty()) {
            Log.fatal("InitContainer not found for Update Job.");
            decideRecreateStrategy(context);
            return;
        }
        if (initContainerExitCode.get() != 0) {
            Log.error("InitContainer unexpectedly failed for Update Job.");
            decideRecreateStrategy(context);
            return;
        }

        // check container.
        var containerExitCode = container(pod)
                .map(KeycloakUpdateJobDependentResource::exitCode);
        if (containerExitCode.isEmpty()) {
            Log.error("Container not found for Update Job.");
            decideRecreateStrategy(context);
            return;
        }
        switch (containerExitCode.get()) {
            case 0: {
                decideRollingStrategy(context);
            }
            case 1: {
                Log.error("Container has an unexpected error for Update Job");
                decideRecreateStrategy(context);
            }
            case 2: {
                Log.fatal("Container has an invalid arguments for Update Job.");
                decideRecreateStrategy(context);
            }
            default: {
                decideRecreateStrategy(context);
            }
        }
    }

    private static Optional<ContainerStatus> initContainer(Pod pod) {
        return Optional.ofNullable(pod.getStatus())
                .map(PodStatus::getInitContainerStatuses)
                .map(Collection::stream)
                .flatMap(Stream::findFirst);
    }

    private static Optional<ContainerStatus> container(Pod pod) {
        return Optional.ofNullable(pod.getStatus())
                .map(PodStatus::getContainerStatuses)
                .map(Collection::stream)
                .flatMap(Stream::findFirst);
    }

    private static int exitCode(ContainerStatus containerStatus) {
        return Optional.ofNullable(containerStatus)
                .map(ContainerStatus::getState)
                .map(ContainerState::getTerminated)
                .map(ContainerStateTerminated::getExitCode)
                .orElse(1);
    }

    private static void decideRecreateStrategy(Context<?> context) {
        Log.info("Using RECREATE upgrade strategy");
        context.managedDependentResourceContext().put(UPDATE_STRATEGY_KEY, UpdateStrategy.RECREATE);
    }

    private static void decideRollingStrategy(Context<?> context) {
        Log.info("Using ROLLING upgrade strategy");
        context.managedDependentResourceContext().put(UPDATE_STRATEGY_KEY, UpdateStrategy.ROLLING);
    }

    public static Optional<UpdateStrategy> getUpdateStrategy(Context<?> context) {
        return context.managedDependentResourceContext().get(UPDATE_STRATEGY_KEY, UpdateStrategy.class);
    }

    public enum UpdateStrategy {
        RECREATE,
        ROLLING
    }
}
