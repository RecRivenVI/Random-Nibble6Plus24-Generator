package io.github.recrivenvi.randomnibble6plus24generator.worldgen.session;

public final class PhysicalWorldAccessException extends IllegalStateException {

    public PhysicalWorldAccessException(String operation) {
        super("Isolated SURFACE generation attempted forbidden physical access: " + operation);
    }
}
