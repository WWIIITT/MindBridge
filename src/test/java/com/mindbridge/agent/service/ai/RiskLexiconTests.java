package com.mindbridge.agent.service.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiskLexiconTests {

    @Test
    void recognisesSimplifiedHighRiskInput() {
        assertThat(RiskLexicon.hasHighRiskSignal("\u6211\u60f3\u4f24\u5bb3\u81ea\u5df1")).isTrue();
    }

    @Test
    void recognisesSimplifiedConsultInput() {
        assertThat(RiskLexicon.hasConsultSignal("\u6211\u6700\u8fd1\u538b\u529b\u5f88\u5927\uff0c\u7761\u4e0d\u7740"))
                .isTrue();
    }
}
