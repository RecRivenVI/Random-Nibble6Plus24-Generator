package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.randomnibble6plus24generator.worldgen.profile.MosaicWorldProfile;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MosaicWorldIdentityTest {
    @Test
    void ordinaryWorldDoesNotAcquireMosaicAuthority() {
        assertDoesNotThrow(
                () -> MosaicWorldIdentity.requireAgreement(3, List.of(), Optional.empty()));
        assertThrows(
                MosaicIdentityValidationException.class,
                () ->
                        MosaicWorldIdentity.requireAgreement(
                                3, List.of(), Optional.of(MosaicWorldProfile.targetOnly())));
    }

    @Test
    void allDimensionsMustAgreeWithSavedProfile() {
        var profile = MosaicWorldProfile.targetOnly();
        var all = List.of(profile, profile, profile);
        assertDoesNotThrow(
                () -> MosaicWorldIdentity.requireAgreement(3, all, Optional.of(profile)));
        assertThrows(
                MosaicIdentityValidationException.class,
                () -> MosaicWorldIdentity.requireAgreement(3, all, Optional.empty()));
        assertThrows(
                MosaicIdentityValidationException.class,
                () ->
                        MosaicWorldIdentity.requireAgreement(
                                3, List.of(profile), Optional.of(profile)));
        assertThrows(
                MosaicIdentityValidationException.class,
                () ->
                        MosaicWorldIdentity.requireAgreement(
                                3,
                                List.of(profile, profile, MosaicWorldProfile.current()),
                                Optional.of(profile)));
        assertThrows(
                MosaicIdentityValidationException.class,
                () ->
                        MosaicWorldIdentity.requireAgreement(
                                3,
                                List.of(
                                        MosaicWorldProfile.current(),
                                        MosaicWorldProfile.current(),
                                        MosaicWorldProfile.current()),
                                Optional.of(MosaicWorldProfile.current())));
    }
}
