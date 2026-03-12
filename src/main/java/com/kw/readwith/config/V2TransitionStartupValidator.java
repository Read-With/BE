package com.kw.readwith.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class V2TransitionStartupValidator {

    private final ApiContractProperties apiContractProperties;
    private final EpubNormalizationProperties epubNormalizationProperties;
    private final LocatorProperties locatorProperties;

    @PostConstruct
    void validate() {
        ApiContractMode apiContractMode = apiContractProperties.getResolvedMode();
        LocatorMode locatorMode = locatorProperties.getResolvedMode();

        log.info(
                "V2 transition flags loaded: epub.normalization.enabled={}, locator.mode={}, api.contract.mode={}",
                epubNormalizationProperties.isEnabled(),
                locatorMode.getPropertyValue(),
                apiContractMode.getPropertyValue()
        );

        if (apiContractMode.blocksLegacyRoutes() && !epubNormalizationProperties.isEnabled()) {
            throw new IllegalStateException(
                    "api.contract.mode=" + apiContractMode.getPropertyValue()
                            + " 를 사용하려면 epub.normalization.enabled=true 여야 합니다."
            );
        }

        if (apiContractMode.blocksLegacyRoutes() && locatorMode.isOff()) {
            throw new IllegalStateException(
                    "api.contract.mode=" + apiContractMode.getPropertyValue()
                            + " 를 사용하려면 locator.mode=on 이상이어야 합니다."
            );
        }

        if (apiContractMode.isStrict() && !locatorMode.isStrict()) {
            throw new IllegalStateException("api.contract.mode=v2_only_strict 는 locator.mode=strict 와 함께 사용해야 합니다.");
        }
    }
}
