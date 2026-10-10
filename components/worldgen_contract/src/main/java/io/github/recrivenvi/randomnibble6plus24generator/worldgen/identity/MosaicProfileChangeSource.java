package io.github.recrivenvi.randomnibble6plus24generator.worldgen.identity;

/** The server's root SavedDataStorage notifies its owner about identity replacements. */
public interface MosaicProfileChangeSource {
    void randomnibble6plus24generator$onIdentityReplacement(Runnable listener);

    void randomnibble6plus24generator$discardProfileForExplicitReload();
}
