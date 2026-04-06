package com.kw.readwith.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "epub.normalization")
@Getter
@Setter
public class EpubNormalizationProperties {

    private boolean enabled = false;
    private String ruleVersion = "v2";
    private String locatorVersion = "v2";
}
