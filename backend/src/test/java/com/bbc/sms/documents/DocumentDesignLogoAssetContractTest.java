package com.bbc.sms.documents;

import com.bbc.sms.platform.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentDesignLogoAssetContractTest {
    private static final String ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

    @Test
    void acceptsAValidatedPngAndReturnsTheBytesUsedByTheVersionedBrandingRow() {
        DocumentDesignService.LogoAsset asset = DocumentDesignService.logoAsset(
                new DocumentDesignDtos.PublishRequest("logo", "fr", "image/png", ONE_PIXEL_PNG), null);

        assertThat(asset.contentType()).isEqualTo("image/png");
        assertThat(asset.bytes()).isNotEmpty();
    }

    @Test
    void rejectsUnsupportedOrMalformedLogoPayloadsBeforePublishing() {
        assertThatThrownBy(() -> DocumentDesignService.logoAsset(
                new DocumentDesignDtos.PublishRequest("logo", "fr", "image/svg+xml", ONE_PIXEL_PNG), null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatus().value()).isEqualTo(400));

        assertThatThrownBy(() -> DocumentDesignService.logoAsset(
                new DocumentDesignDtos.PublishRequest("logo", "fr", "image/png", "not-base64"), null))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("VALIDATION_ERROR"));
    }
}
