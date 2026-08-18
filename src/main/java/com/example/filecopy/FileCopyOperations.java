package com.example.filecopy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

final class FileCopyOperations {

    private static final int PARALLELISM = 3;

    static int parallelism() {
        return PARALLELISM;
    }

    static CopyResult copySequentialWithoutSynchronization(Path sourceDirectory, Path targetDirectory) throws IOException {
        long startedAt = System.nanoTime();

        List<Path> sourceFiles = discoverRegularFiles(sourceDirectory);
        createStrategyTargetDirectory(targetDirectory);

        int copiedFiles = 0;
        long copiedBytes = 0L;

        for (Path sourceFile : sourceFiles) {
            Path targetFile = resolveTargetFile(sourceDirectory, targetDirectory, sourceFile);
            copiedBytes = Math.addExact(copiedBytes, copyFile(sourceFile, targetFile));
            copiedFiles++;
        }

        return new CopyResult(copiedFiles, copiedBytes, System.nanoTime() - startedAt);
    }

    static CopyResult copySequentialWithForce(Path sourceDirectory, Path targetDirectory) throws IOException {
        long startedAt = System.nanoTime();

        List<Path> sourceFiles = discoverRegularFiles(sourceDirectory);
        createStrategyTargetDirectory(targetDirectory);

        int copiedFiles = 0;
        long copiedBytes = 0L;

        for (Path sourceFile : sourceFiles) {
            Path targetFile = resolveTargetFile(sourceDirectory, targetDirectory, sourceFile);
            copiedBytes = Math.addExact(copiedBytes, copyAndForceFile(sourceFile, targetFile));
            copiedFiles++;
        }

        return new CopyResult(copiedFiles, copiedBytes, System.nanoTime() - startedAt);
    }

    static CopyResult copyParallelWithoutSynchronization(Path sourceDirectory, Path targetDirectory) throws IOException {
        long startedAt = System.nanoTime();

        List<Path> sourceFiles = discoverRegularFiles(sourceDirectory);
        createStrategyTargetDirectory(targetDirectory);

        ExecutorService executor = Executors.newFixedThreadPool(PARALLELISM);
        CountDownLatch completion = new CountDownLatch(sourceFiles.size());
        AtomicReference<IOException> failure = new AtomicReference<>();
        LongAdder copiedBytes = new LongAdder();

        try {
            for (Path sourceFile : sourceFiles) {
                Path targetFile = resolveTargetFile(sourceDirectory, targetDirectory, sourceFile);

                executor.execute(() -> {
                    try {
                        copiedBytes.add(copyFile(sourceFile, targetFile));
                    } catch (IOException exception) {
                        failure.compareAndSet(null, exception);
                    } finally {
                        completion.countDown();
                    }
                });
            }

            try {
                completion.await();
            } catch (InterruptedException exception) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                throw new IOException("Копирование было прервано", exception);
            }

            IOException copyFailure = failure.get();

            if (copyFailure != null) {
                throw new IOException("Ошибка параллельного копирования", copyFailure);
            }

            return new CopyResult(sourceFiles.size(), copiedBytes.sum(), System.nanoTime() - startedAt);
        } finally {
            executor.shutdown();
        }
    }

    private static List<Path> discoverRegularFiles(Path sourceDirectory) throws IOException {
        try (Stream<Path> paths = Files.walk(sourceDirectory)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> sourceDirectory.relativize(path).toString()))
                    .toList();
        }
    }

    private static void createStrategyTargetDirectory(Path targetDirectory) throws IOException {
        Path parent = targetDirectory.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try {
            Files.createDirectory(targetDirectory);
        } catch (FileAlreadyExistsException exception) {
            throw new IOException(exception);
        }
    }

    private static Path resolveTargetFile(Path sourceDirectory, Path targetDirectory, Path sourceFile) {
        return targetDirectory.resolve(sourceDirectory.relativize(sourceFile));
    }

    private static long copyFile(Path sourceFile, Path targetFile) throws IOException {
        Path parent = targetFile.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (InputStream input = Files.newInputStream(sourceFile)) {
            return Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long copyAndForceFile(Path sourceFile, Path targetFile) throws IOException {
        long copiedBytes = copyFile(sourceFile, targetFile);

        try (FileChannel channel = FileChannel.open(targetFile, StandardOpenOption.WRITE)) {
            channel.force(true);
        }

        return copiedBytes;
    }

    record CopyResult(int copiedFiles, long copiedBytes, long elapsedNanos) {
        double elapsedSeconds() {
            return elapsedNanos / 1_000_000_000.0;
        }
    }
}
