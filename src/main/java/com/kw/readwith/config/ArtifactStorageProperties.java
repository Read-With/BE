package com.kw.readwith.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "artifact.storage")
@Getter
@Setter
public class ArtifactStorageProperties {

    private String publicPrefix = "public";
    private String privatePrefix = "private";
    private String cloudFrontBaseUrl = "";
}
