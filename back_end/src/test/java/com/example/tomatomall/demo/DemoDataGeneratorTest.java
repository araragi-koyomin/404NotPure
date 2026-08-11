package com.example.tomatomall.demo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataGeneratorTest {

    private static final Set<String> FRONTEND_CATEGORY_CODES = Set.of(
            "literature", "biography", "philosophy", "art", "science", "computer",
            "medical", "education", "economics", "politics", "social", "travel", "children"
    );

    @TempDir
    Path tempDirectory;

    @Test
    void defaultConfigurationGeneratesExpectedScaleWithoutHardcodingHundredsOfRows() {
        DemoDataImportConfig config = DemoDataImportConfig.defaults("local-demo-password", tempDirectory);

        DemoDataset dataset = new DemoDataGenerator().generate(config);

        assertEquals(300, dataset.books().size());
        assertEquals(502, dataset.users().size());
        assertEquals(6, dataset.advertisements().size());
        assertEquals(20, dataset.books().stream().filter(DemoBook::publicDomain).count());
        assertEquals(1, dataset.users().stream().filter(user -> "ADMIN".equals(user.role())).count());
        assertEquals("demo_admin", dataset.users().get(0).username());
        assertEquals("demo_user", dataset.users().get(1).username());
        assertEquals("load_user_0001", dataset.users().get(2).username());
    }

    @Test
    void sameSeedIsRepeatableAndDifferentSeedOnlyChangesSyntheticBookDetails() {
        DemoDataGenerator generator = new DemoDataGenerator();
        DemoDataset first = generator.generate(config(60, 10, 404L));
        DemoDataset repeated = generator.generate(config(60, 10, 404L));
        DemoDataset anotherSeed = generator.generate(config(60, 10, 405L));

        assertEquals(first, repeated);
        assertEquals(first.books().subList(0, 20), anotherSeed.books().subList(0, 20));
        assertNotEquals(first.books().get(20), anotherSeed.books().get(20));
        assertEquals(first.users(), anotherSeed.users());
    }

    @Test
    void generatedIdentifiersAndAssetPathsAreUniqueAndStayInsideReservedNamespace() {
        DemoDataset dataset = new DemoDataGenerator().generate(config(300, 500, 404L));
        Set<String> titles = new HashSet<>();
        Set<String> covers = new HashSet<>();
        Set<String> usernames = new HashSet<>();

        for (DemoBook book : dataset.books()) {
            assertTrue(titles.add(book.title()));
            assertTrue(covers.add(book.coverUrl()));
            assertTrue(FRONTEND_CATEGORY_CODES.contains(book.category()));
            assertTrue(book.coverUrl().startsWith("/demo-data/generated/seed-404/books/"));
            assertEquals(4, book.specifications().size());
            assertEquals(1, book.contentImageUrls().size());
        }
        for (DemoUser user : dataset.users()) {
            assertTrue(usernames.add(user.username()));
        }
    }

    @Test
    void invalidCountsAndMissingPasswordAreRejectedBeforeGeneration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDataImportConfig(19, 500, 404L, "password", tempDirectory));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDataImportConfig(300, 5001, 404L, "password", tempDirectory));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDataImportConfig(300, 500, 404L, " ", tempDirectory));
        assertThrows(IllegalArgumentException.class,
                () -> new DemoDataImportConfig(300, 500, 404L, "password", null));
    }

    @Test
    void assetWriterCreatesDeterministicLocalSvgFilesWithoutExternalUrls() throws Exception {
        DemoDataset dataset = new DemoDataGenerator().generate(config(20, 0, 404L));

        DemoAssetWriter writer = new DemoAssetWriter();
        DemoAssetSummary first = writer.write(dataset, tempDirectory);
        String firstCover = Files.readString(tempDirectory.resolve("seed-404/books/book-0001-cover.svg"));
        DemoAssetSummary repeated = writer.write(dataset, tempDirectory);

        assertEquals(20, first.coverCount());
        assertEquals(20, first.detailImageCount());
        assertEquals(6, first.advertisementCount());
        assertEquals(first, repeated);
        assertEquals(firstCover,
                Files.readString(tempDirectory.resolve("seed-404/books/book-0001-cover.svg")));
        assertTrue(firstCover.contains("<svg"));
        assertTrue(firstCover.contains("文学小说"));
        assertFalse(firstCover.contains("<image"));
        assertFalse(firstCover.contains("href=\"http://"));
        assertFalse(firstCover.contains("href=\"https://"));
    }

    @Test
    void publicDomainAssetKeepsSameContentWhenSeedChangesBecauseItsUrlIsStable() throws Exception {
        DemoAssetWriter writer = new DemoAssetWriter();
        Path firstRoot = tempDirectory.resolve("first");
        Path secondRoot = tempDirectory.resolve("second");

        writer.write(new DemoDataGenerator().generate(config(20, 0, 404L)), firstRoot);
        writer.write(new DemoDataGenerator().generate(config(20, 0, 405L)), secondRoot);

        assertEquals(
                Files.readString(firstRoot.resolve("seed-404/books/book-0001-cover.svg")),
                Files.readString(secondRoot.resolve("seed-404/books/book-0001-cover.svg"))
        );
        assertEquals(
                Files.readString(firstRoot.resolve("seed-404/books/book-0001-detail-01.svg")),
                Files.readString(secondRoot.resolve("seed-404/books/book-0001-detail-01.svg"))
        );
    }

    @Test
    void databaseGuardAcceptsOnlyExactDemoDatabaseName() {
        DemoDatabaseGuard.requireExactDemoDatabase("tomatomall_demo");

        assertThrows(IllegalStateException.class,
                () -> DemoDatabaseGuard.requireExactDemoDatabase("Tomato"));
        assertThrows(IllegalStateException.class,
                () -> DemoDatabaseGuard.requireExactDemoDatabase("tomatomall_demo_test"));
        assertThrows(IllegalStateException.class,
                () -> DemoDatabaseGuard.requireExactDemoDatabase(null));
    }

    private DemoDataImportConfig config(int books, int users, long seed) {
        return new DemoDataImportConfig(books, users, seed, "local-demo-password", tempDirectory);
    }
}
