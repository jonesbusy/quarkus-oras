package io.quarkiverse.oras.deployment;

import java.util.Map;
import java.util.function.Supplier;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import io.quarkiverse.oras.runtime.Registries;
import io.quarkiverse.oras.runtime.RegistriesConfiguration;
import io.quarkiverse.oras.runtime.RegistriesRecorder;
import io.quarkiverse.oras.runtime.RegistryConfiguration;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import land.oras.Annotations;
import land.oras.ArtifactType;
import land.oras.Config;
import land.oras.Describable;
import land.oras.Descriptor;
import land.oras.Index;
import land.oras.Layer;
import land.oras.Manifest;
import land.oras.ManifestDescriptor;
import land.oras.OCILayout;
import land.oras.Registry;
import land.oras.Subject;
import land.oras.Tags;
import land.oras.exception.Error;
import land.oras.utils.HttpClient;

class RegistryProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void produce(
            RegistriesConfiguration registriesConfiguration,
            RegistriesRecorder registriesRecorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (registriesConfiguration.names().isEmpty()) {
            return;
        }

        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(Named.class).build());
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(Registries.class)
                .setUnremovable()
                .setDefaultScope(DotNames.SINGLETON).build());

        for (Map.Entry<String, RegistryConfiguration> entry : registriesConfiguration.names()
                .entrySet()) {
            var registryName = entry.getKey();
            syntheticBeanBuildItemBuildProducer.produce(createRegistryBuildItem(
                    registryName,
                    Registry.class,
                    // Pass runtime configuration to ensure initialization order
                    registriesRecorder.registrySupplier(registryName)));
        }
    }

    @BuildStep
    ReflectiveClassBuildItem registerReflection() {
        // Register model for reflection
        return ReflectiveClassBuildItem.builder(
                Annotations.class,
                ArtifactType.class,
                Config.class,
                Descriptor.class,
                Describable.class,
                Error.class,
                HttpClient.TokenResponse.class,
                Index.class,
                Layer.class,
                Manifest.class,
                ManifestDescriptor.class,
                OCILayout.class,
                Subject.class,
                Tags.class).methods().constructors().fields().build();
    }

    private static <T> SyntheticBeanBuildItem createRegistryBuildItem(
            String registryName, Class<T> clientClass, Supplier<T> clientSupplier) {
        SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                .configure(clientClass)
                .scope(Singleton.class)
                .setRuntimeInit()
                .supplier(clientSupplier);

        configurator
                .addQualifier()
                .annotation(DotNames.NAMED)
                .addValue("value", registryName)
                .done();
        configurator.addQualifier().annotation(DotNames.NAMED).addValue("value", registryName).done();
        configurator.addQualifier().annotation(Named.class)
                .addValue("value", registryName).done();

        return configurator.done();
    }
}
