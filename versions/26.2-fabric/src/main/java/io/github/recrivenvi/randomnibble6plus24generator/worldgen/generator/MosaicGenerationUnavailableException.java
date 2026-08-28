package io.github.recrivenvi.randomnibble6plus24generator.worldgen.generator;

public final class MosaicGenerationUnavailableException extends IllegalStateException {

    public MosaicGenerationUnavailableException(String operation) {
        super("Mosaic world generation is not implemented; refusing operation '"
                + operation
                + "' to prevent fake Vanilla fallback terrain");
    }
}
