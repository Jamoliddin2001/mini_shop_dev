package com.shop.common;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequestIdFilter}: id generation/echo, untrusted-header sanitization
 * (log-injection defense), MDC visibility during the chain, and MDC cleanup afterwards.
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures the MDC value visible while the downstream chain runs. */
    private static final class MdcCapturingChain implements FilterChain {
        private String requestIdDuringChain;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            requestIdDuringChain = MDC.get(RequestIdFilter.MDC_KEY);
        }
    }

    @Test
    void generatesIdWhenHeaderAbsentAndEchoesItBack() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();

        filter.doFilter(request, response, chain);

        String header = response.getHeader(RequestIdFilter.HEADER);
        assertThat(header).isNotBlank();
        assertThat(chain.requestIdDuringChain).isEqualTo(header);
    }

    @Test
    void preservesValidInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc-123_DEF.456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("abc-123_DEF.456");
        assertThat(chain.requestIdDuringChain).isEqualTo("abc-123_DEF.456");
    }

    @Test
    void sanitizesMaliciousInboundIdToPreventLogInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "foo\r\nFAKE LOG injected=true");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();

        filter.doFilter(request, response, chain);

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).doesNotContain("\r").doesNotContain("\n").doesNotContain(" ");
        assertThat(id).isEqualTo("fooFAKELOGinjectedtrue");
    }

    @Test
    void removesIdFromMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MdcCapturingChain());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
