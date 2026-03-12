package com.kw.readwith.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "api.contract")
@Getter
@Setter
public class ApiContractProperties {

    private String mode = ApiContractMode.PREPARE.getPropertyValue();

    public ApiContractMode getResolvedMode() {
        return ApiContractMode.from(mode);
    }
}
