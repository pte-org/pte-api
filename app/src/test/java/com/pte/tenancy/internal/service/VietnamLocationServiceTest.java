package com.pte.tenancy.internal.service;

import com.pte.tenancy.internal.config.VietnamLocationProperties;
import com.pte.tenancy.internal.dto.response.LocationOptionResponse;
import com.pte.tenancy.internal.exception.InvalidLocationSelectionException;
import com.pte.tenancy.internal.exception.LocationCatalogUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Provider boundary of the Vietnam location catalog: parsing, caching and selection checks. */
class VietnamLocationServiceTest {

    private static final String PROVINCES = """
            [{"code":"01","name":"Ha Noi","division_type":"thanh pho"},
             {"code":"79","name":"Ho Chi Minh","division_type":"thanh pho"}]""";
    private static final String WARDS_OF_01 = """
            {"code":"01","wards":[{"code":"0001","name":"Ba Dinh","division_type":"phuong"}]}""";

    private MockRestServiceServer server;
    private VietnamLocationService service;
    private AtomicLong now;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://loc/api");
        server = MockRestServiceServer.bindTo(builder).build();
        VietnamLocationProperties properties = new VietnamLocationProperties();
        properties.setCacheTtlMs(1_000);
        now = new AtomicLong(1_000_000L);
        Clock clock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(now.get());
            }
        };
        service = new VietnamLocationService(builder.build(), properties, JsonMapper.builder().build(), clock);
    }

    private void expectProvinces(ExpectedCount count, String body) {
        server.expect(count, requestTo("http://loc/api/p")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void listProvinces_parsesProviderPayloadIntoStableOptions() {
        expectProvinces(ExpectedCount.once(), PROVINCES);

        List<LocationOptionResponse> provinces = service.listProvinces();

        assertThat(provinces).containsExactly(
                new LocationOptionResponse("01", "Ha Noi", "thanh pho"),
                new LocationOptionResponse("79", "Ho Chi Minh", "thanh pho"));
    }

    @Test
    void listProvinces_calledTwiceInsideTtl_hitsProviderOnce() {
        expectProvinces(ExpectedCount.once(), PROVINCES);

        service.listProvinces();
        now.addAndGet(999);
        service.listProvinces();

        server.verify();
    }

    @Test
    void listProvinces_afterTtlExpires_reloadsFromProvider() {
        expectProvinces(ExpectedCount.twice(), PROVINCES);

        service.listProvinces();
        now.addAndGet(1_000);
        service.listProvinces();

        server.verify();
    }

    @Test
    void listProvinces_providerError_isReportedAsCatalogUnavailable() {
        server.expect(requestTo("http://loc/api/p")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.listProvinces()).isInstanceOf(LocationCatalogUnavailableException.class);
    }

    @Test
    void listProvinces_blankBody_isReportedAsCatalogUnavailable() {
        expectProvinces(ExpectedCount.once(), "  ");

        assertThatThrownBy(() -> service.listProvinces()).isInstanceOf(LocationCatalogUnavailableException.class);
    }

    @Test
    void listProvinces_emptyArray_isReportedAsCatalogUnavailable() {
        expectProvinces(ExpectedCount.once(), "[]");

        assertThatThrownBy(() -> service.listProvinces()).isInstanceOf(LocationCatalogUnavailableException.class);
    }

    @Test
    void listProvinces_optionWithoutName_isReportedAsCatalogUnavailable() {
        expectProvinces(ExpectedCount.once(), "[{\"code\":\"01\"}]");

        assertThatThrownBy(() -> service.listProvinces()).isInstanceOf(LocationCatalogUnavailableException.class);
    }

    @Test
    void listProvinces_payloadThatIsNotAnArray_isReportedAsCatalogUnavailable() {
        expectProvinces(ExpectedCount.once(), "{\"code\":\"01\"}");

        assertThatThrownBy(() -> service.listProvinces()).isInstanceOf(LocationCatalogUnavailableException.class);
    }

    @Test
    void listWards_knownProvince_returnsItsWards() {
        expectProvinces(ExpectedCount.once(), PROVINCES);
        server.expect(requestTo("http://loc/api/p/01?depth=2"))
                .andRespond(withSuccess(WARDS_OF_01, MediaType.APPLICATION_JSON));

        List<LocationOptionResponse> wards = service.listWards(" 01 ");

        assertThat(wards).containsExactly(new LocationOptionResponse("0001", "Ba Dinh", "phuong"));
    }

    @Test
    void listWards_unknownProvince_isInvalidSelection() {
        expectProvinces(ExpectedCount.once(), PROVINCES);

        assertThatThrownBy(() -> service.listWards("99")).isInstanceOf(InvalidLocationSelectionException.class);
    }

    @Test
    void listWards_blankProvince_isInvalidSelectionWithoutCallingProvider() {
        assertThatThrownBy(() -> service.listWards("  ")).isInstanceOf(InvalidLocationSelectionException.class);
        assertThatThrownBy(() -> service.listWards(null)).isInstanceOf(InvalidLocationSelectionException.class);
        server.verify();
    }

    @Test
    void resolve_noProvinceAndNoWard_returnsEmptyLocation() {
        assertThat(service.resolve(null, " ")).isEqualTo(VietnamLocationService.ResolvedLocation.empty());
    }

    @Test
    void resolve_wardWithoutProvince_isInvalidSelection() {
        assertThatThrownBy(() -> service.resolve(null, "0001")).isInstanceOf(InvalidLocationSelectionException.class);
    }

    @Test
    void resolve_provinceOnly_returnsProvinceWithoutWard() {
        expectProvinces(ExpectedCount.once(), PROVINCES);

        VietnamLocationService.ResolvedLocation location = service.resolve("79", null);

        assertThat(location.provinceName()).isEqualTo("Ho Chi Minh");
        assertThat(location.wardCode()).isNull();
        assertThat(location.wardName()).isNull();
    }

    @Test
    void resolve_provinceAndMatchingWard_returnsBothNames() {
        expectProvinces(ExpectedCount.once(), PROVINCES);
        server.expect(requestTo("http://loc/api/p/01?depth=2"))
                .andRespond(withSuccess(WARDS_OF_01, MediaType.APPLICATION_JSON));

        VietnamLocationService.ResolvedLocation location = service.resolve("01", "0001");

        assertThat(location.provinceName()).isEqualTo("Ha Noi");
        assertThat(location.wardName()).isEqualTo("Ba Dinh");
    }

    @Test
    void resolve_wardOfAnotherProvince_isInvalidSelection() {
        expectProvinces(ExpectedCount.once(), PROVINCES);
        server.expect(requestTo("http://loc/api/p/01?depth=2"))
                .andRespond(withSuccess(WARDS_OF_01, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.resolve("01", "9999")).isInstanceOf(InvalidLocationSelectionException.class);
    }

    @Test
    void resolve_unknownProvince_isInvalidSelection() {
        expectProvinces(ExpectedCount.once(), PROVINCES);

        assertThatThrownBy(() -> service.resolve("99", null)).isInstanceOf(InvalidLocationSelectionException.class);
    }
}
