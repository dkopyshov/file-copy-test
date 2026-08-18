package com.example.filecopy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class FileCopyApplication {

    // java -jar file-copy-test.jar <copy type> <source-directory> <target-root-directory>
    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        if (args.length != 3) {
            System.err.println("Ошибка: нужно три аргумента: <способ копирования> <source-directory> <target-root-directory>");
            return 2;
        }

        try {
            int testType = Integer.parseInt(args[0]);
            Path source = Path.of(args[1]).toAbsolutePath().normalize();
            Path targetRoot = Path.of(args[2]).toAbsolutePath().normalize();

            Path sequentialNoSyncTarget = targetRoot.resolve("sequential-no-sync");
            Path sequentialForceTarget = targetRoot.resolve("sequential-force");
            Path parallelNoSyncTarget = targetRoot.resolve("parallel-no-sync-3");

            switch (testType) {
                case 1 -> {
                    FileCopyOperations.CopyResult sequentialNoSync = FileCopyOperations.copySequentialWithoutSynchronization(source, sequentialNoSyncTarget);
                    printResults("Обычная без force", sequentialNoSync);
                }
                case 2 -> {
                    FileCopyOperations.CopyResult sequentialForce = FileCopyOperations.copySequentialWithForce(source, sequentialForceTarget);
                    printResults("Обычная с force(true)", sequentialForce);
                }

                case 3 -> {
                    FileCopyOperations.CopyResult parallelNoSync = FileCopyOperations.copyParallelWithoutSynchronization(source, parallelNoSyncTarget);
                    printResults("Параллельная без force, " + FileCopyOperations.parallelism() + " потока", parallelNoSync);
                }
            }

            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println("Ошибка: " + exception.getMessage());

            if (exception.getCause() != null) {
                System.err.println("Причина: " + exception.getCause());
            }

            return 1;
        }
    }

    private static void printResults(
            String method,
            FileCopyOperations.CopyResult first
    ) {
        System.out.println("Затраченное время (сек)\tСпособ проверки\tКоличество файлов\tОбъем копирования (МБ)");
        printResult(method + " (запуск 1)", first);
    }

    private static void printResult(String method, FileCopyOperations.CopyResult result) {
        System.out.printf(
                Locale.ROOT,
                "%.3f\t%s\t%d\t%.3f%n",
                result.elapsedSeconds(),
                method,
                result.copiedFiles(),
                result.copiedBytes() / 1_000_000.0
        );
    }
}
