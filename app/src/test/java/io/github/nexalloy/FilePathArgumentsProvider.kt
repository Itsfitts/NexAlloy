package io.github.nexalloy

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.support.ParameterDeclarations
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Stream
import kotlin.io.path.name

class FilePathArgumentsProvider : ArgumentsProvider {
    override fun provideArguments(
        parameters: ParameterDeclarations,
        context: ExtensionContext
    ): Stream<out Arguments> {
        val projectDir = Paths.get(".")
        val testInputPath = projectDir.resolve("binaries")

        if (!Files.exists(testInputPath)) {
            Assumptions.assumeTrue(false, "APKs folder not found: $testInputPath")
            return Stream.empty()
        }

        val files = Files.walk(testInputPath).filter { path ->
            Files.isRegularFile(path) && path.normalize().none { it.name.startsWith(".") }
        }.toList()

        if (files.isEmpty()) {
            Assumptions.assumeTrue(false, "No APKs found in $testInputPath")
            return Stream.empty()
        }

        return files.stream().map { Arguments.of(it) }
    }
}
