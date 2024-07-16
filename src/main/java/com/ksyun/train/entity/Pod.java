package com.ksyun.train.entity;

import java.math.BigDecimal;
import java.util.List;
import com.ksyun.train.util.SkipMappingValueAnnotation;

public class Pod {

    private Metadata metadata;

    private List<Container> container;

    private BigDecimal cpu;

    private BigDecimal memory;

    private boolean autoCreated = true;

    @SkipMappingValueAnnotation
    private String apiVersion = "v1";

    public static class Metadata {
        private long generation;

        private String name;

        public long getGeneration() {
            return generation;
        }

        public String getName() {
            return name;
        }
    }

    public static class Container {
        private String name;

        private List<String> command;

        private List<Environment> environment;

        private Integer port;

        @SkipMappingValueAnnotation
        private String imagePullPolicy = "Always";

        public String getName() {
            return name;
        }

        public List<String> getCommand() {
            return command;
        }

        public List<Environment> getEnvironment() {
            return environment;
        }

        public Integer getPort() {
            return port;
        }

        public String getImagePullPolicy() {
            return imagePullPolicy;
        }
    }

    public static class Environment {
        private String key;

        private String value;

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public List<Container> getContainer() {
        return container;
    }

    public BigDecimal getCpu() {
        return cpu;
    }

    public BigDecimal getMemory() {
        return memory;
    }

    public boolean isAutoCreated() {
        return autoCreated;
    }

    public String getApiVersion() {
        return apiVersion;
    }
}