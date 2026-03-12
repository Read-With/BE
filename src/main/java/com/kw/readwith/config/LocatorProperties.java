package com.kw.readwith.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "locator")
@Getter
@Setter
public class LocatorProperties {

    private String mode = LocatorMode.ON.getPropertyValue();

    public LocatorMode getResolvedMode() {
        return LocatorMode.from(mode);
    }
}
